package com.hepdd.toms_storage.inventory;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.StorageItemUtils;

public class InventoryAdapter implements IStorageInventory {

    private final IInventory inventory;
    private final ForgeDirection side;

    public InventoryAdapter(IInventory inventory, ForgeDirection side) {
        this.inventory = inventory;
        this.side = side == null ? ForgeDirection.UNKNOWN : side;
    }

    @Override
    public int getSlots() {
        return inventory.getSizeInventory();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!isSlotInRange(slot)) return null;
        return inventory.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack == null || stack.stackSize < 1 || !canInsert(slot, stack)) return stack;

        ItemStack existing = inventory.getStackInSlot(slot);
        int limit = Math.min(inventory.getInventoryStackLimit(), stack.getMaxStackSize());
        if (existing != null) {
            if (!StorageItemUtils.areItemStacksEqual(existing, stack, true)) return stack;
            limit -= existing.stackSize;
        }
        if (limit <= 0) return stack;

        int inserted = Math.min(limit, stack.stackSize);
        if (!simulate) {
            if (existing == null) {
                inventory.setInventorySlotContents(slot, StorageItemUtils.copyWithSize(stack, inserted));
            } else {
                existing.stackSize += inserted;
                inventory.setInventorySlotContents(slot, existing);
            }
            inventory.markDirty();
        }

        if (inserted == stack.stackSize) return null;
        ItemStack remainder = stack.copy();
        remainder.stackSize -= inserted;
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount < 1 || !canExtract(slot)) return null;
        ItemStack existing = inventory.getStackInSlot(slot);
        if (existing == null) return null;

        int extracted = Math.min(amount, existing.stackSize);
        ItemStack result = StorageItemUtils.copyWithSize(existing, extracted);
        if (!simulate) {
            inventory.decrStackSize(slot, extracted);
            inventory.markDirty();
        }
        return result;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return canInsert(slot, stack);
    }

    private boolean canInsert(int slot, ItemStack stack) {
        if (!isSlotInRange(slot)) return false;
        if (!inventory.isItemValidForSlot(slot, stack)) return false;
        if (inventory instanceof ISidedInventory) {
            ISidedInventory sided = (ISidedInventory) inventory;
            return sided.canInsertItem(slot, stack, side.ordinal());
        }
        return true;
    }

    private boolean canExtract(int slot) {
        if (!isSlotInRange(slot)) return false;
        if (inventory instanceof ISidedInventory) {
            ISidedInventory sided = (ISidedInventory) inventory;
            ItemStack stack = inventory.getStackInSlot(slot);
            return stack != null && sided.canExtractItem(slot, stack, side.ordinal());
        }
        return true;
    }

    private boolean isSlotInRange(int slot) {
        return slot >= 0 && slot < inventory.getSizeInventory();
    }
}
