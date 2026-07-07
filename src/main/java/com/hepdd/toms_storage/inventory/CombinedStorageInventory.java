package com.hepdd.toms_storage.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

public class CombinedStorageInventory implements IStorageInventory {

    private final List<IStorageInventory> inventories = new ArrayList<>();

    public void clear() {
        inventories.clear();
    }

    public void add(IStorageInventory inventory) {
        if (inventory != null) {
            inventories.add(inventory);
        }
    }

    public List<IStorageInventory> getInventories() {
        return inventories;
    }

    @Override
    public int getSlots() {
        int slots = 0;
        for (IStorageInventory inventory : inventories) {
            slots += inventory.getSlots();
        }
        return slots;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        SlotRef ref = getSlotRef(slot);
        return ref == null ? null : ref.inventory.getStackInSlot(ref.slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        SlotRef ref = getSlotRef(slot);
        return ref == null ? stack : ref.inventory.insertItem(ref.slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        SlotRef ref = getSlotRef(slot);
        return ref == null ? null : ref.inventory.extractItem(ref.slot, amount, simulate);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        SlotRef ref = getSlotRef(slot);
        return ref != null && ref.inventory.isItemValid(ref.slot, stack);
    }

    private SlotRef getSlotRef(int slot) {
        int index = slot;
        for (IStorageInventory inventory : inventories) {
            int size = inventory.getSlots();
            if (index < size) return new SlotRef(inventory, index);
            index -= size;
        }
        return null;
    }

    private static class SlotRef {

        private final IStorageInventory inventory;
        private final int slot;

        private SlotRef(IStorageInventory inventory, int slot) {
            this.inventory = inventory;
            this.slot = slot;
        }
    }
}
