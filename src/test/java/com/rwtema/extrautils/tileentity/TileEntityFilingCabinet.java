package com.rwtema.extrautils.tileentity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.inventory.StorageTestInventory;

public class TileEntityFilingCabinet extends StorageTestInventory {

    public final List<ItemStack> itemSlots = new ArrayList<>();
    public final List<ItemStack> inputSlots = new ArrayList<>();

    @Override
    public int getSizeInventory() {
        return itemSlots.size() + inputSlots.size() + 1;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int[] slots = new int[getSizeInventory()];
        for (int slot = 0; slot < slots.length; slot++) slots[slot] = slot;
        return slots;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < itemSlots.size()) return itemSlots.get(slot);
        int input = slot - itemSlots.size();
        return input < inputSlots.size() ? inputSlots.get(input) : null;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == getSizeInventory() - 1;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < itemSlots.size()) itemSlots.set(slot, stack);
        else if (slot == getSizeInventory() - 1) inputSlots.add(stack);
        else inputSlots.set(slot - itemSlots.size(), stack);
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot >= itemSlots.size()) return null;
        ItemStack stack = itemSlots.get(slot);
        if (stack == null) return null;
        int count = Math.min(Math.min(amount, stack.getMaxStackSize()), stack.stackSize);
        ItemStack extracted = StorageItemUtils.copyWithSize(stack, count);
        stack.stackSize -= count;
        if (stack.stackSize == 0) itemSlots.set(slot, null);
        return extracted;
    }
}
