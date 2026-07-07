package com.hepdd.toms_storage.tile;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.Config;
import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.inventory.CombinedStorageInventory;
import com.hepdd.toms_storage.inventory.IStorageInventory;
import com.hepdd.toms_storage.inventory.InventoryAdapter;

public class TileEntityInventoryConnector extends TileEntity {

    private final CombinedStorageInventory inventory = new CombinedStorageInventory();
    private long lastScanTick = -1L;

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

        Stack<ChunkCoordinates> toCheck = new Stack<>();
        Set<String> checked = new HashSet<>();
        ChunkCoordinates start = new ChunkCoordinates(xCoord, yCoord, zCoord);
        toCheck.push(start);
        checked.add(getKey(start));

        while (!toCheck.isEmpty()) {
            ChunkCoordinates current = toCheck.pop();
            boolean currentIsTrim = worldObj.getBlock(current.posX, current.posY, current.posZ) == ModRegistry.trim;

            for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
                ChunkCoordinates next = new ChunkCoordinates(
                    current.posX + direction.offsetX,
                    current.posY + direction.offsetY,
                    current.posZ + direction.offsetZ);
                String key = getKey(next);
                if (checked.contains(key) || getDistanceSq(next) > Config.getInventoryConnectorRangeSquared()) continue;
                checked.add(key);

                TileEntity tile = worldObj.getTileEntity(next.posX, next.posY, next.posZ);
                if (tile instanceof TileEntityInventoryConnector) {
                    continue;
                }

                if (tile instanceof IInventory && (!Config.onlyTrimsConnect || currentIsTrim)) {
                    inventory.add(new InventoryAdapter((IInventory) tile, direction.getOpposite()));
                    toCheck.push(next);
                    continue;
                }

                if (worldObj.getBlock(next.posX, next.posY, next.posZ) == ModRegistry.trim) {
                    toCheck.push(next);
                }
            }
        }
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
}
