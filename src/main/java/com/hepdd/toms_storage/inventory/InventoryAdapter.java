package com.hepdd.toms_storage.inventory;

import java.util.BitSet;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.StorageItemUtils;

public class InventoryAdapter implements IStorageInventory {

    protected final IInventory inventory;
    protected final ForgeDirection side;
    private int[] cachedAccessibleRef;
    private BitSet cachedAccessibleBits;

    public InventoryAdapter(IInventory inventory, ForgeDirection side) {
        this.inventory = inventory;
        this.side = side == null ? ForgeDirection.UNKNOWN : side;
    }

    public IInventory getInventory() {
        return inventory;
    }

    public IInventory getOwnerInventory() {
        return inventory;
    }

    public Object getStorageIdentity() {
        return inventory;
    }

    public boolean containsInventory(IInventory target) {
        return inventory == target;
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
        int limit = getSlotLimit(stack);
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
        if (amount < 1 || !isSlotInRange(slot)) return null;
        ItemStack existing = inventory.getStackInSlot(slot);
        if (existing == null || !canExtract(slot, existing)) return null;

        int extracted = Math.min(amount, existing.stackSize);
        ItemStack result = StorageItemUtils.copyWithSize(existing, extracted);
        if (!simulate) {
            ItemStack extractedStack = inventory.decrStackSize(slot, extracted);
            inventory.markDirty();
            return extractedStack;
        }
        return result;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return canInsert(slot, stack);
    }

    protected boolean canInsert(int slot, ItemStack stack) {
        if (!isSlotInRange(slot)) return false;
        if (!inventory.isItemValidForSlot(slot, stack)) return false;
        if (inventory instanceof ISidedInventory) {
            ISidedInventory sided = (ISidedInventory) inventory;
            return isSlotAccessible(sided, slot) && sided.canInsertItem(slot, stack, side.ordinal());
        }
        return true;
    }

    protected boolean canExtract(int slot, ItemStack stack) {
        if (!isSlotInRange(slot)) return false;
        if (inventory instanceof ISidedInventory) {
            ISidedInventory sided = (ISidedInventory) inventory;
            return isSlotAccessible(sided, slot) && sided.canExtractItem(slot, stack, side.ordinal());
        }
        return true;
    }

    protected int getSlotLimit(ItemStack stack) {
        return Math.min(inventory.getInventoryStackLimit(), stack.getMaxStackSize());
    }

    private boolean isSlotInRange(int slot) {
        return slot >= 0 && slot < inventory.getSizeInventory();
    }

    private boolean isSlotAccessible(ISidedInventory sided, int slot) {
        if (slot < 0) return false;
        int[] accessible = sided.getAccessibleSlotsFromSide(side.ordinal());
        if (accessible == null || accessible.length == 0) return false;
        if (accessible.length <= 8) {
            for (int candidate : accessible) {
                if (candidate == slot) return true;
            }
            return false;
        }
        BitSet bits = cachedAccessibleBits;
        if (accessible != cachedAccessibleRef || bits == null) {
            bits = new BitSet(accessible.length);
            for (int candidate : accessible) {
                if (candidate >= 0) {
                    bits.set(candidate);
                }
            }
            cachedAccessibleRef = accessible;
            cachedAccessibleBits = bits;
        }
        return bits.get(slot);
    }
}
