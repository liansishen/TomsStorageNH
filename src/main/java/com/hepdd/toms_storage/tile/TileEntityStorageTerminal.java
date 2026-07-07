package com.hepdd.toms_storage.tile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.Config;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.block.BlockStorageTerminal;
import com.hepdd.toms_storage.inventory.IStorageInventory;
import com.hepdd.toms_storage.inventory.InventoryAdapter;
import com.hepdd.toms_storage.inventory.StorageInventoryUtils;
import com.hepdd.toms_storage.item.ItemWirelessTerminal;

public class TileEntityStorageTerminal extends TileEntity {

    private final Map<StoredItemStack, StoredItemStack> items = new HashMap<>();
    private int sorting;
    private String lastSearch = "";

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote) {
            rebuildStacks();
        }
    }

    public List<StoredItemStack> getStacks() {
        if (worldObj != null && !worldObj.isRemote) rebuildStacks();
        return new ArrayList<>(items.values());
    }

    public StoredItemStack pullStack(StoredItemStack stack, long maxAmount) {
        IStorageInventory inventory = getAttachedInventory();
        if (inventory == null || stack == null || stack.getStack() == null || maxAmount < 1) return null;

        ItemStack extracted = StorageInventoryUtils
            .extractMatching(inventory, stack.getStack(), (int) Math.min(maxAmount, Integer.MAX_VALUE), false);
        if (extracted != null) {
            rebuildStacks();
            markDirty();
        }
        return extracted == null ? null : new StoredItemStack(extracted);
    }

    public StoredItemStack pushStack(StoredItemStack stack) {
        if (stack == null) return null;
        ItemStack remainder = pushStack(stack.getActualStack());
        return remainder == null ? null : new StoredItemStack(remainder);
    }

    public ItemStack pushStack(ItemStack stack) {
        IStorageInventory inventory = getAttachedInventory();
        if (inventory == null || stack == null) return stack;
        ItemStack remainder = StorageInventoryUtils.insertStacked(inventory, stack, false);
        rebuildStacks();
        markDirty();
        return remainder;
    }

    public void pushOrDrop(ItemStack stack) {
        if (stack == null) return;
        ItemStack remainder = pushStack(stack);
        if (remainder != null && remainder.stackSize > 0) {
            EntityItem entityItem = new EntityItem(worldObj, xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, remainder);
            worldObj.spawnEntityInWorld(entityItem);
        }
    }

    public boolean canInteractWith(EntityPlayer player) {
        if (worldObj.getTileEntity(xCoord, yCoord, zCoord) != this) return false;
        double maxDistance = ItemWirelessTerminal.isPlayerHolding(player) ? Config.wirelessReach * Config.wirelessReach
            : 64.0D;
        return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= maxDistance;
    }

    public int getSorting() {
        return sorting;
    }

    public void setSorting(int sorting) {
        this.sorting = sorting;
        markDirty();
    }

    public String getLastSearch() {
        return lastSearch;
    }

    public void setLastSearch(String lastSearch) {
        this.lastSearch = lastSearch == null ? "" : lastSearch;
        markDirty();
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("sorting", sorting);
        tag.setString("lastSearch", lastSearch);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        sorting = tag.getInteger("sorting");
        lastSearch = tag.getString("lastSearch");
    }

    protected IStorageInventory getAttachedInventory() {
        ForgeDirection facing = BlockStorageTerminal.getFacing(worldObj.getBlockMetadata(xCoord, yCoord, zCoord));
        TileEntity tile = worldObj
            .getTileEntity(xCoord + facing.offsetX, yCoord + facing.offsetY, zCoord + facing.offsetZ);
        if (tile instanceof TileEntityInventoryConnector) {
            return ((TileEntityInventoryConnector) tile).getStorageInventory();
        }
        if (tile instanceof IInventory) {
            return new InventoryAdapter((IInventory) tile, facing.getOpposite());
        }
        return null;
    }

    private void rebuildStacks() {
        items.clear();

        IStorageInventory inventory = getAttachedInventory();
        if (inventory == null) return;

        for (StoredItemStack stack : StorageInventoryUtils.getStacks(inventory)) {
            StoredItemStack existing = items.get(stack);
            if (existing == null) {
                items.put(stack, stack);
            } else {
                existing.grow(stack.getQuantity());
            }
        }
    }
}
