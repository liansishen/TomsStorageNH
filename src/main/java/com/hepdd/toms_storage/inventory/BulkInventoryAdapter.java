package com.hepdd.toms_storage.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.StorageItemUtils;

final class BulkInventoryAdapter extends StorageInventoryAdapter {

    private final Object storage;
    private final boolean projectRed;

    BulkInventoryAdapter(TileEntity tile, ForgeDirection side, Object storage, boolean projectRed) {
        super(tile, (IInventory) tile, side, tile, () -> true, 2);
        this.storage = storage;
        this.projectRed = projectRed;
    }

    private int getCount() {
        return projectRed ? (Integer) StorageCompatibility.call(storage, "storage")
            : (Integer) StorageCompatibility.field(storage, "mItemCount");
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot == 1) return super.getStackInSlot(slot);
        if (slot != 0) return null;
        int count = getCount();
        ItemStack prototype = projectRed ? inventory.getStackInSlot(1)
            : (ItemStack) StorageCompatibility.field(storage, "mItemStack");
        if (count <= 0 || prototype == null || !canExtract(1, prototype)) return null;
        return StorageItemUtils.copyWithSize(prototype, count);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // The real input buffer is separate from the virtual slot exposed to the network.
        return slot == 0 ? super.insertItem(0, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot == 1) return super.extractItem(slot, amount, simulate);
        if (slot != 0 || amount <= 0) return null;
        ItemStack stack = getStackInSlot(0);
        if (stack == null) return null;
        int extracted = Math.min(amount, stack.stackSize);
        if (!simulate) {
            int remaining = getCount() - extracted;
            if (projectRed) {
                StorageCompatibility.setCount(storage, "storage_$eq", remaining);
            } else {
                StorageCompatibility.setField(storage, "mItemCount", remaining);
                if (remaining == 0) StorageCompatibility.setField(storage, "mItemStack", null);
            }
            inventory.markDirty();
        }
        return StorageItemUtils.copyWithSize(stack, extracted);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0 && canInsert(0, stack);
    }
}
