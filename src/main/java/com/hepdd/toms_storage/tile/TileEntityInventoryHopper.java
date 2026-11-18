package com.hepdd.toms_storage.tile;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

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
    private static final int MAX_BACKOFF_COOLDOWN = 80;

    private final List<NetworkAttachment> attachments = new ArrayList<>(2);
    private ItemStack filter;
    private ItemStack filterSingle;
    private int cooldown;
    private int currentInterval = TRANSFER_COOLDOWN;
    private long lastFailedMutation = -1L;
    private int roundRobinSlot;
    private TileEntityInventoryConnector connector;
    private IStorageInventory network;
    private ForgeDirection networkSide = ForgeDirection.UNKNOWN;
    private boolean connectionConflict;
    private boolean unattributedConflict;
    private boolean resolvingConnections;

    private IInventory cachedExternalTarget;
    private ForgeDirection cachedExternalSide = ForgeDirection.UNKNOWN;
    private InventoryAdapter cachedExternalAdapter;

    @Override
    public void invalidate() {
        clearNetworkState();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        clearNetworkState();
        super.onChunkUnload();
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) return;
        if (cooldown > 0) {
            cooldown--;
            if (cooldown >= TRANSFER_COOLDOWN && cooldown % TRANSFER_COOLDOWN == 0
                && connector != null
                && connector.getMutationVersion() != lastFailedMutation) {
                currentInterval = TRANSFER_COOLDOWN;
                cooldown = 0;
            }
        }
        if (cooldown > 0 || worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord)) return;
        cooldown = TRANSFER_COOLDOWN;
        resolveConnections();
        if (!hasCurrentNetwork() || connectionConflict) return;

        ForgeDirection facing = getFacing();
        if (networkSide != facing && networkSide != facing.getOpposite()) return;
        ForgeDirection externalSide = networkSide.getOpposite();
        int tx = xCoord + externalSide.offsetX;
        int ty = yCoord + externalSide.offsetY;
        int tz = zCoord + externalSide.offsetZ;
        if (!StorageInventoryUtils.isChunkLoaded(worldObj, tx, tz)) return;

        TileEntity externalTile = worldObj.getTileEntity(tx, ty, tz);
        if (!(externalTile instanceof IInventory)) return;
        IInventory externalInventory = (IInventory) externalTile;
        if (network instanceof CombinedStorageInventory
            && ((CombinedStorageInventory) network).containsInventory(externalInventory)) {
            return;
        }

        InventoryAdapter external = getExternalAdapter(externalInventory, externalSide.getOpposite());
        if (isImportMode()) {
            transferFromExternal(external, network);
            currentInterval = TRANSFER_COOLDOWN;
        } else if (filter != null) {
            if (exportFromNetwork(network, external)) {
                currentInterval = TRANSFER_COOLDOWN;
            } else {
                lastFailedMutation = connector != null ? connector.getMutationVersion() : -1L;
                cooldown = currentInterval;
                currentInterval = Math.min(MAX_BACKOFF_COOLDOWN, currentInterval * 2);
            }
        }
    }

    private InventoryAdapter getExternalAdapter(IInventory target, ForgeDirection accessSide) {
        if (cachedExternalAdapter == null || cachedExternalTarget != target || cachedExternalSide != accessSide) {
            cachedExternalTarget = target;
            cachedExternalSide = accessSide;
            cachedExternalAdapter = new InventoryAdapter(target, accessSide);
        }
        return cachedExternalAdapter;
    }

    @Override
    public boolean canConnectNetworkFrom(ForgeDirection side) {
        ForgeDirection facing = getFacing();
        return side == facing || side == facing.getOpposite();
    }

    @Override
    public void attachNetwork(TileEntityInventoryConnector newConnector, ForgeDirection side) {
        if (worldObj == null || newConnector == null || !canConnectNetworkFrom(side)) return;
        upsertAttachment(newConnector, side, false);
    }

    @Override
    public void markNetworkConflict(TileEntityInventoryConnector sourceConnector, ForgeDirection side) {
        if (worldObj == null || sourceConnector == null || !canConnectNetworkFrom(side)) return;
        upsertAttachment(sourceConnector, side, true);
    }

    @Override
    public void markNetworkConflict() {
        if (worldObj == null) return;
        unattributedConflict = true;
        connector = null;
        network = null;
        networkSide = ForgeDirection.UNKNOWN;
        connectionConflict = true;
    }

    @Override
    public void detachNetwork(TileEntityInventoryConnector oldConnector) {
        if (oldConnector == null || attachments.isEmpty()) return;
        attachments.removeIf(entry -> {
            TileEntityInventoryConnector current = entry.connectorRef.get();
            return current == null || current == oldConnector;
        });
        syncResolvedState();
    }

    public void markNetworkDirty() {
        unattributedConflict = false;
        currentInterval = TRANSFER_COOLDOWN;
        lastFailedMutation = -1L;
        cachedExternalTarget = null;
        cachedExternalSide = ForgeDirection.UNKNOWN;
        cachedExternalAdapter = null;
        for (int i = attachments.size() - 1; i >= 0; i--) {
            TileEntityInventoryConnector current = attachments.get(i).connectorRef.get();
            if (current != null && !current.isInvalid()) {
                current.markTopologyDirty();
            }
        }
    }

    public void setFilter(ItemStack stack) {
        filter = stack == null ? null : StorageItemUtils.copyWithSize(stack, 1);
        filterSingle = filter == null ? null : filter.copy();
        currentInterval = TRANSFER_COOLDOWN;
        lastFailedMutation = -1L;
        if (cooldown > TRANSFER_COOLDOWN) {
            cooldown = TRANSFER_COOLDOWN;
        }
        markDirty();
    }

    public ItemStack getFilter() {
        return filter == null ? null : filter.copy();
    }

    public String getModeTranslationKey() {
        resolveConnections();
        if (!hasCurrentNetwork() || connectionConflict) return "message.tomsstorage.inventory_hopper.mode_unknown";
        return isImportMode() ? "message.tomsstorage.inventory_hopper.mode_import"
            : "message.tomsstorage.inventory_hopper.mode_export";
    }

    public String getStatusTranslationKey() {
        resolveConnections();
        if (connectionConflict) return "message.tomsstorage.inventory_hopper.conflict";
        if (!hasCurrentNetwork()) return "message.tomsstorage.inventory_hopper.disconnected";
        if (!isImportMode() && filter == null) return "message.tomsstorage.inventory_hopper.filter_required";
        if (worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord)) {
            return "message.tomsstorage.inventory_hopper.redstone_disabled";
        }
        return "message.tomsstorage.inventory_hopper.active";
    }

    private boolean isImportMode() {
        return networkSide == getFacing();
    }

    private boolean hasCurrentNetwork() {
        return worldObj != null && connector != null && network != null;
    }

    private void clearNetworkState() {
        attachments.clear();
        unattributedConflict = false;
        connector = null;
        network = null;
        networkSide = ForgeDirection.UNKNOWN;
        connectionConflict = false;
        cachedExternalTarget = null;
        cachedExternalSide = ForgeDirection.UNKNOWN;
        cachedExternalAdapter = null;
    }

    private void upsertAttachment(TileEntityInventoryConnector target, ForgeDirection side, boolean conflicted) {
        unattributedConflict = false;
        long scanId = target.getScanId();
        attachments.removeIf(entry -> {
            TileEntityInventoryConnector current = entry.connectorRef.get();
            return current == null || (current == target && entry.scanId != scanId);
        });
        for (int i = 0; i < attachments.size(); i++) {
            NetworkAttachment entry = attachments.get(i);
            if (entry.connectorRef.get() == target && entry.side == side) {
                entry.scanId = scanId;
                entry.conflicted = conflicted;
                syncResolvedState();
                return;
            }
        }
        attachments.add(new NetworkAttachment(target, side, scanId, conflicted));
        syncResolvedState();
    }

    private void resolveConnections() {
        if (resolvingConnections || worldObj == null || worldObj.isRemote) return;
        resolvingConnections = true;
        try {
            List<TileEntityInventoryConnector> toRefresh = null;
            for (int i = 0; i < attachments.size(); i++) {
                TileEntityInventoryConnector current = attachments.get(i).connectorRef.get();
                if (isConnectorAlive(current) && current.needsTopologyRefresh()) {
                    if (toRefresh == null) {
                        toRefresh = new ArrayList<>(attachments.size());
                    }
                    if (!toRefresh.contains(current)) {
                        toRefresh.add(current);
                    }
                }
            }
            if (toRefresh != null) {
                for (int i = 0; i < toRefresh.size(); i++) {
                    toRefresh.get(i)
                        .ensureTopologyUpToDate();
                }
            }
            syncResolvedState();
        } finally {
            resolvingConnections = false;
        }
    }

    private void syncResolvedState() {
        if (worldObj == null) {
            connector = null;
            network = null;
            networkSide = ForgeDirection.UNKNOWN;
            connectionConflict = false;
            return;
        }
        attachments.removeIf(entry -> !isAttachmentValid(entry));
        if (unattributedConflict) {
            connector = null;
            network = null;
            networkSide = ForgeDirection.UNKNOWN;
            connectionConflict = true;
            return;
        }
        if (attachments.isEmpty()) {
            connector = null;
            network = null;
            networkSide = ForgeDirection.UNKNOWN;
            connectionConflict = false;
            return;
        }
        if (attachments.size() == 1 && !attachments.get(0).conflicted) {
            NetworkAttachment active = attachments.get(0);
            TileEntityInventoryConnector activeConnector = active.connectorRef.get();
            connector = activeConnector;
            network = activeConnector != null ? activeConnector.getDirectStorageInventory() : null;
            networkSide = active.side;
            connectionConflict = false;
            return;
        }
        connector = null;
        network = null;
        networkSide = ForgeDirection.UNKNOWN;
        connectionConflict = true;
    }

    private boolean isAttachmentValid(NetworkAttachment entry) {
        TileEntityInventoryConnector current = entry.connectorRef.get();
        if (!isConnectorAlive(current)) return false;
        if (entry.scanId != current.getScanId()) return false;
        if (!canConnectNetworkFrom(entry.side)) return false;
        return isSidePhysicallyConnected(current, entry.side);
    }

    private boolean isConnectorAlive(TileEntityInventoryConnector current) {
        return current != null && !current.isInvalid()
            && current.getWorldObj() == worldObj
            && StorageInventoryUtils.isChunkLoaded(worldObj, current.xCoord, current.zCoord)
            && worldObj.getTileEntity(current.xCoord, current.yCoord, current.zCoord) == current;
    }

    private boolean isSidePhysicallyConnected(TileEntityInventoryConnector current, ForgeDirection side) {
        int nx = xCoord + side.offsetX;
        int ny = yCoord + side.offsetY;
        int nz = zCoord + side.offsetZ;
        if (!StorageInventoryUtils.isChunkLoaded(worldObj, nx, nz)) return false;
        if (TileEntityInventoryConnector.isCableBlock(worldObj.getBlock(nx, ny, nz))) return true;
        return worldObj.getTileEntity(nx, ny, nz) == current;
    }

    private ForgeDirection getFacing() {
        ForgeDirection facing = ForgeDirection.getOrientation(getBlockMetadata());
        return facing == ForgeDirection.UNKNOWN ? ForgeDirection.NORTH : facing;
    }

    private boolean exportFromNetwork(IStorageInventory sourceNetwork, IStorageInventory destination) {
        if (filterSingle == null) return false;
        filterSingle.stackSize = 1;
        ItemStack simulatedDestRemainder = StorageInventoryUtils.insertStacked(destination, filterSingle, true);
        if (simulatedDestRemainder != null && simulatedDestRemainder.stackSize > 0) return false;

        ItemStack simulatedExtract = StorageInventoryUtils.extractMatching(sourceNetwork, filterSingle, 1, true);
        if (simulatedExtract == null || simulatedExtract.stackSize != 1) return false;

        ItemStack extracted = StorageInventoryUtils.extractMatching(sourceNetwork, filterSingle, 1, false);
        if (extracted == null || extracted.stackSize <= 0) return false;
        if (extracted.stackSize != 1 || !StorageItemUtils.areItemStacksEqual(extracted, simulatedExtract, true)) {
            rollbackOrDropNetwork(sourceNetwork, extracted);
            return false;
        }
        ItemStack remainder = StorageInventoryUtils.insertStacked(destination, extracted, false);
        if (remainder != null && remainder.stackSize > 0) {
            rollbackOrDropNetwork(sourceNetwork, remainder);
        }
        markDirty();
        return remainder == null || remainder.stackSize <= 0;
    }

    private boolean transferFromExternal(IStorageInventory source, IStorageInventory destination) {
        int slots = source.getSlots();
        if (slots <= 0) return false;
        for (int checked = 0; checked < slots; checked++) {
            int slot = Math.floorMod(roundRobinSlot + checked, slots);
            ItemStack candidate = source.getStackInSlot(slot);
            if (candidate == null || candidate.stackSize <= 0 || !matchesFilter(candidate, false)) continue;
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

    private void rollbackOrDropNetwork(IStorageInventory targetNetwork, ItemStack stack) {
        ItemStack remainder = StorageInventoryUtils.insertStacked(targetNetwork, stack, false);
        if (remainder == null || remainder.stackSize <= 0) return;
        EntityItem dropped = new EntityItem(worldObj, xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, remainder.copy());
        worldObj.spawnEntityInWorld(dropped);
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
        filterSingle = filter == null ? null : StorageItemUtils.copyWithSize(filter, 1);
        currentInterval = TRANSFER_COOLDOWN;
        lastFailedMutation = -1L;
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

    private static final class NetworkAttachment {

        private final WeakReference<TileEntityInventoryConnector> connectorRef;
        private final ForgeDirection side;
        private long scanId;
        private boolean conflicted;

        private NetworkAttachment(TileEntityInventoryConnector connector, ForgeDirection side, long scanId,
            boolean conflicted) {
            this.connectorRef = new WeakReference<>(connector);
            this.side = side;
            this.scanId = scanId;
            this.conflicted = conflicted;
        }
    }
}
