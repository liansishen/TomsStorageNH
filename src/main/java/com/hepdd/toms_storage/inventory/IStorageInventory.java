package com.hepdd.toms_storage.inventory;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.StoredItemStack;

public interface IStorageInventory {

    int getSlots();

    ItemStack getStackInSlot(int slot);

    default void forEachStack(Consumer<ItemStack> consumer) {
        int slots = getSlots();
        for (int slot = 0; slot < slots; slot++) consumer.accept(getStackInSlot(slot));
    }

    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    ItemStack extractItem(int slot, int amount, boolean simulate);

    boolean isItemValid(int slot, ItemStack stack);

    default boolean hasFastTransfer() {
        return false;
    }

    default ItemStack insertIntoInventory(ItemStack stack, boolean mergeOnly, boolean simulate) {
        return stack;
    }

    default ItemStack extractMatching(ItemStack match, int amount, boolean simulate) {
        if (match == null || amount < 1) return null;
        ItemStack result = null;
        int remaining = amount;
        int slots = getSlots();
        for (int slot = 0; slot < slots && remaining > 0; slot++) {
            ItemStack stack = getStackInSlot(slot);
            if (stack != null && StorageItemUtils.areItemStacksEqual(stack, match, true)) {
                ItemStack extracted = extractItem(slot, remaining, simulate);
                if (extracted != null && extracted.stackSize > 0) {
                    if (result == null) {
                        result = extracted;
                    } else {
                        result.stackSize += extracted.stackSize;
                    }
                    remaining -= extracted.stackSize;
                }
            }
        }
        return result;
    }

    default long packSlotStats() {
        int[] stats = new int[2];
        forEachStack(stack -> {
            stats[0]++;
            if (stack == null || stack.stackSize <= 0) stats[1]++;
        });
        return ((long) stats[0] << 32) | (stats[1] & 0xFFFFFFFFL);
    }

    default List<StoredItemStack> getAggregatedStacks() {
        return StorageInventoryUtils.getStacks(this);
    }
}
