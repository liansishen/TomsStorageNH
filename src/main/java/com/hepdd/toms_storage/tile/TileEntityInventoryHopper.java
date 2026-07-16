package com.hepdd.toms_storage.tile;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.inventory.CombinedStorageInventory;
import com.hepdd.toms_storage.inventory.IStorageInventory;
import com.hepdd.toms_storage.inventory.IStorageNetworkEndpoint;
import com.hepdd.toms_storage.inventory.InventoryAdapter;
import com.hepdd.toms_storage.inventory.StorageInventoryUtils;

public class TileEntityInventoryHopper extends TileEntity implements IStorageNetworkEndpoint {

    private static final int TRANSFER_COOLDOWN = 10;

    private ItemStack filter;
    private int cooldown;
    private int roundRobinSlot;
    private TileEntityInventoryConnector connector;
    private IStorageInventory network;
    private ForgeDirection networkSide = ForgeDirection.UNKNOWN;
    private long connectionEpoch = Long.MIN_VALUE;
    private boolean connectionConflict;

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        if (cooldown > 0) cooldown--;
        if (cooldown > 0 || worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord)) return;
        cooldown = TRANSFER_COOLDOWN;
        if (!hasCurrentNetwork() || connectionConflict) return;

        ForgeDirection facing = getFacing();
        if (networkSide != facing && networkSide != facing.getOpposite()) return;
        ForgeDirection externalSide = networkSide.getOpposite();
        TileEntity externalTile = worldObj
            .getTileEntity(xCoord + externalSide.offsetX, yCoord + externalSide.offsetY, zCoord + externalSide.offsetZ);
        if (!(externalTile instanceof IInventory)) return;
        IInventory externalInventory = (IInventory) externalTile;
        if (network instanceof CombinedStorageInventory
            && ((CombinedStorageInventory) network).containsInventory(externalInventory)) return;

        InventoryAdapter external = new InventoryAdapter(externalInventory, externalSide.getOpposite());
        if (isImportMode()) {
            transferOne(external, network, false);
        } else if (filter != null) {
            transferOne(network, external, true);
        }
    }

    @Override
    public boolean canConnectNetworkFrom(ForgeDirection side) {
        ForgeDirection facing = getFacing();
        return side == facing || side == facing.getOpposite();
    }

    @Override
    public void attachNetwork(TileEntityInventoryConnector newConnector, ForgeDirection side) {
        if (worldObj == null || newConnector == null || !canConnectNetworkFrom(side)) return;
        long epoch = worldObj.getTotalWorldTime() / 20L;
        if (connectionEpoch != epoch) {
            connectionEpoch = epoch;
            connector = newConnector;
            network = newConnector.getStorageInventory();
            networkSide = side;
            connectionConflict = false;
        } else if (connector != newConnector || networkSide != side) {
            connectionConflict = true;
        }
    }

    @Override
    public void markNetworkConflict() {
        if (worldObj == null) return;
        connectionEpoch = worldObj.getTotalWorldTime() / 20L;
        connector = null;
        network = null;
        connectionConflict = true;
    }

    public void markNetworkDirty() {
        connectionEpoch = Long.MIN_VALUE;
        connector = null;
        network = null;
        networkSide = ForgeDirection.UNKNOWN;
        connectionConflict = false;
    }

    public void setFilter(ItemStack stack) {
        filter = stack == null ? null : StorageItemUtils.copyWithSize(stack, 1);
        markDirty();
    }

    public ItemStack getFilter() {
        return filter == null ? null : filter.copy();
    }

    public String getModeTranslationKey() {
        if (!hasCurrentNetwork() || connectionConflict) return "message.tomsstorage.inventory_hopper.mode_unknown";
        return isImportMode() ? "message.tomsstorage.inventory_hopper.mode_import"
            : "message.tomsstorage.inventory_hopper.mode_export";
    }

    public String getStatusTranslationKey() {
        if (connectionConflict) return "message.tomsstorage.inventory_hopper.conflict";
        if (!hasCurrentNetwork()) return "message.tomsstorage.inventory_hopper.disconnected";
        if (!isImportMode() && filter == null) return "message.tomsstorage.inventory_hopper.filter_required";
        if (worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord))
            return "message.tomsstorage.inventory_hopper.redstone_disabled";
        return "message.tomsstorage.inventory_hopper.active";
    }

    private boolean isImportMode() {
        return networkSide == getFacing();
    }

    private boolean hasCurrentNetwork() {
        return worldObj != null && connector != null
            && network != null
            && connectionEpoch >= worldObj.getTotalWorldTime() / 20L - 1L;
    }

    private ForgeDirection getFacing() {
        ForgeDirection facing = ForgeDirection.getOrientation(getBlockMetadata());
        return facing == ForgeDirection.UNKNOWN ? ForgeDirection.NORTH : facing;
    }

    private boolean transferOne(IStorageInventory source, IStorageInventory destination, boolean requireFilter) {
        int slots = source.getSlots();
        if (slots <= 0) return false;
        for (int checked = 0; checked < slots; checked++) {
            int slot = Math.floorMod(roundRobinSlot + checked, slots);
            ItemStack candidate = source.getStackInSlot(slot);
            if (candidate == null || candidate.stackSize <= 0 || !matchesFilter(candidate, requireFilter)) continue;
            ItemStack simulatedExtract = source.extractItem(slot, 1, true);
            if (simulatedExtract == null || simulatedExtract.stackSize != 1) continue;
            ItemStack simulatedRemainder = StorageInventoryUtils.insertStacked(destination, simulatedExtract, true);
            if (simulatedRemainder != null && simulatedRemainder.stackSize > 0) continue;

            ItemStack extracted = source.extractItem(slot, 1, false);
            if (extracted == null || extracted.stackSize <= 0) continue;
            if (extracted.stackSize != 1 || !StorageItemUtils.areItemStacksEqual(extracted, simulatedExtract, true)) {
                rollbackOrDrop(source, slot, extracted);
                return false;
            }
            ItemStack remainder = StorageInventoryUtils.insertStacked(destination, extracted, false);
            if (remainder != null && remainder.stackSize > 0) rollbackOrDrop(source, slot, remainder);
            roundRobinSlot = (slot + 1) % slots;
            markDirty();
            return remainder == null || remainder.stackSize <= 0;
        }
        roundRobinSlot = (roundRobinSlot + 1) % slots;
        return false;
    }

    private boolean matchesFilter(ItemStack stack, boolean requireFilter) {
        if (filter == null) return !requireFilter;
        return StorageItemUtils.areItemStacksEqual(filter, stack, true);
    }

    private void rollbackOrDrop(IStorageInventory source, int sourceSlot, ItemStack stack) {
        ItemStack remainder = source.insertItem(sourceSlot, stack, false);
        if (remainder != null) remainder = StorageInventoryUtils.insertStacked(source, remainder, false);
        if (remainder == null || remainder.stackSize <= 0) return;
        EntityItem dropped = new EntityItem(worldObj, xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, remainder.copy());
        worldObj.spawnEntityInWorld(dropped);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        cooldown = tag.getInteger("Cooldown");
        roundRobinSlot = tag.getInteger("RoundRobinSlot");
        filter = tag.hasKey("Filter") ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("Filter")) : null;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("Cooldown", cooldown);
        tag.setInteger("RoundRobinSlot", roundRobinSlot);
        if (filter != null) {
            NBTTagCompound filterTag = new NBTTagCompound();
            filter.writeToNBT(filterTag);
            tag.setTag("Filter", filterTag);
        } else {
            tag.removeTag("Filter");
        }
    }
}
