package com.hepdd.toms_storage.tile;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.block.BlockStorageTerminal;
import com.hepdd.toms_storage.inventory.IStorageInventory;
import com.hepdd.toms_storage.inventory.StorageInventoryProvider;
import com.hepdd.toms_storage.inventory.StorageInventoryUtils;
import com.hepdd.toms_storage.item.ItemWirelessTerminal;

public class TileEntityStorageTerminal extends TileEntity {

    public List<StoredItemStack> getStacks() {
        if (worldObj == null || worldObj.isRemote) return new ArrayList<>();
        IStorageInventory inventory = getAttachedInventory();
        return inventory == null ? new ArrayList<>() : inventory.getAggregatedStacks();
    }

    public StoredItemStack pullStack(StoredItemStack stack, long maxAmount) {
        IStorageInventory inventory = getAttachedInventory();
        if (inventory == null || stack == null || stack.getStack() == null || maxAmount < 1) return null;

        ItemStack extracted = StorageInventoryUtils
            .extractMatching(inventory, stack.getStack(), (int) Math.min(maxAmount, Integer.MAX_VALUE), false);
        if (extracted != null) {
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
        if (player == null || worldObj == null || player.worldObj != worldObj) return false;
        if (!worldObj.isRemote && (!StorageInventoryUtils.isChunkLoaded(worldObj, xCoord, zCoord)
            || worldObj.getTileEntity(xCoord, yCoord, zCoord) != this)) {
            return false;
        }
        int reach = ItemWirelessTerminal.getPlayerReach(player);
        return player.getDistanceSq(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D) <= reach * reach;
    }

    protected IStorageInventory getAttachedInventory() {
        if (worldObj == null || worldObj.isRemote || !StorageInventoryUtils.isChunkLoaded(worldObj, xCoord, zCoord)) {
            return null;
        }
        ForgeDirection facing = BlockStorageTerminal.getFacing(worldObj.getBlockMetadata(xCoord, yCoord, zCoord));
        int tx = xCoord + facing.offsetX;
        int ty = yCoord + facing.offsetY;
        int tz = zCoord + facing.offsetZ;
        if (!StorageInventoryUtils.isChunkLoaded(worldObj, tx, tz)) return null;
        TileEntity tile = worldObj.getTileEntity(tx, ty, tz);
        if (tile instanceof TileEntityInventoryConnector) {
            return ((TileEntityInventoryConnector) tile).getStorageInventory();
        }
        return StorageInventoryProvider.getInventory(tile, facing.getOpposite());
    }
}
