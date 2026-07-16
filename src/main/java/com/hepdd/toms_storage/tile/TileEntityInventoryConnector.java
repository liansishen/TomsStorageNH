package com.hepdd.toms_storage.tile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.Config;
import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.inventory.CombinedStorageInventory;
import com.hepdd.toms_storage.inventory.IStorageInventory;
import com.hepdd.toms_storage.inventory.IStorageNetworkAccess;
import com.hepdd.toms_storage.inventory.IStorageNetworkEndpoint;
import com.hepdd.toms_storage.inventory.InventoryAdapter;

public class TileEntityInventoryConnector extends TileEntity implements IStorageNetworkAccess {

    private final CombinedStorageInventory inventory = new CombinedStorageInventory();
    private long lastScanTick = -1L;
    private boolean cableScanLimitReached;
    private boolean cableNetworkConflict;

    @Override
    public IStorageInventory getStorageInventory() {
        if (!worldObj.isRemote && lastScanTick != worldObj.getTotalWorldTime()) {
            scanInventories();
        }
        return inventory;
    }

    @Override
    public void updateEntity() {
        if (!worldObj.isRemote && worldObj.getTotalWorldTime() % 20L == 0L) {
            scanInventories();
        }
    }

    private void scanInventories() {
        lastScanTick = worldObj.getTotalWorldTime();
        inventory.clear();
        cableScanLimitReached = false;
        cableNetworkConflict = false;

        Deque<ChunkCoordinates> toCheck = new ArrayDeque<>();
        Set<String> checked = new HashSet<>();
        ChunkCoordinates start = new ChunkCoordinates(xCoord, yCoord, zCoord);
        toCheck.addLast(start);
        checked.add(getKey(start));
        int cableNodes = 0;
        List<EndpointRef> cableEndpoints = new ArrayList<>();

        while (!toCheck.isEmpty()) {
            ChunkCoordinates current = toCheck.removeFirst();
            Block currentBlock = worldObj.getBlock(current.posX, current.posY, current.posZ);
            boolean currentIsTrim = currentBlock == ModRegistry.trim;
            boolean currentIsCableNode = currentBlock == ModRegistry.inventoryCable
                || current.posX == xCoord && current.posY == yCoord && current.posZ == zCoord;

            for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                ChunkCoordinates next = new ChunkCoordinates(
                    current.posX + direction.offsetX,
                    current.posY + direction.offsetY,
                    current.posZ + direction.offsetZ);
                String key = getKey(next);
                if (!worldObj.getChunkProvider()
                    .chunkExists(next.posX >> 4, next.posZ >> 4)) continue;

                Block nextBlock = worldObj.getBlock(next.posX, next.posY, next.posZ);
                TileEntity tile = worldObj.getTileEntity(next.posX, next.posY, next.posZ);

                if (currentBlock == ModRegistry.inventoryCable && tile instanceof TileEntityInventoryConnector
                    && tile != this) {
                    cableNetworkConflict = true;
                    continue;
                }

                if (currentIsCableNode && tile instanceof IStorageNetworkEndpoint) {
                    IStorageNetworkEndpoint endpoint = (IStorageNetworkEndpoint) tile;
                    if (endpoint.canConnectNetworkFrom(direction.getOpposite())) {
                        cableEndpoints.add(new EndpointRef(endpoint, direction.getOpposite()));
                    }
                    continue;
                }

                if (checked.contains(key)) continue;

                if (currentIsCableNode && nextBlock == ModRegistry.inventoryCable) {
                    if (cableNodes >= Config.maxInventoryCableNodes) {
                        cableScanLimitReached = true;
                        continue;
                    }
                    checked.add(key);
                    cableNodes++;
                    toCheck.addLast(next);
                    continue;
                }

                if (nextBlock == ModRegistry.inventoryCable || tile instanceof IStorageNetworkEndpoint) continue;
                if (currentBlock == ModRegistry.inventoryCable
                    || getDistanceSq(next) > Config.getInventoryConnectorRangeSquared()) continue;
                checked.add(key);

                if (tile instanceof TileEntityInventoryConnector) {
                    continue;
                }

                if (tile instanceof IInventory && (!Config.onlyTrimsConnect || currentIsTrim)) {
                    inventory.add(new InventoryAdapter((IInventory) tile, direction.getOpposite()));
                    toCheck.addLast(next);
                    continue;
                }

                if (nextBlock == ModRegistry.trim) {
                    toCheck.addLast(next);
                }
            }
        }

        for (EndpointRef endpoint : cableEndpoints) {
            if (cableNetworkConflict) {
                endpoint.endpoint.markNetworkConflict();
            } else {
                endpoint.endpoint.attachNetwork(this, endpoint.side);
            }
        }
    }

    public boolean isCableScanLimitReached() {
        return cableScanLimitReached;
    }

    public boolean hasCableNetworkConflict() {
        return cableNetworkConflict;
    }

    private double getDistanceSq(ChunkCoordinates coordinates) {
        double dx = coordinates.posX - xCoord;
        double dy = coordinates.posY - yCoord;
        double dz = coordinates.posZ - zCoord;
        return dx * dx + dy * dy + dz * dz;
    }

    private String getKey(ChunkCoordinates coordinates) {
        return coordinates.posX + ":" + coordinates.posY + ":" + coordinates.posZ;
    }

    private static final class EndpointRef {

        private final IStorageNetworkEndpoint endpoint;
        private final ForgeDirection side;

        private EndpointRef(IStorageNetworkEndpoint endpoint, ForgeDirection side) {
            this.endpoint = endpoint;
            this.side = side;
        }
    }
}
