package com.hepdd.toms_storage.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.StoredItemStack;

public final class StorageInventoryUtils {

    private StorageInventoryUtils() {}

    public static List<StoredItemStack> getStacks(IStorageInventory inventory) {
        Map<StoredItemStack, StoredItemStack> stacks = new HashMap<>();
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0) {
                StoredItemStack key = new StoredItemStack(stack);
                StoredItemStack existing = stacks.get(key);
                if (existing == null) {
                    stacks.put(key, key);
                } else {
                    existing.grow(stack.stackSize);
                }
            }
        }
        return new ArrayList<>(stacks.values());
    }

    public static ItemStack insertStacked(IStorageInventory inventory, ItemStack stack, boolean simulate) {
        if (stack == null || stack.stackSize < 1) return null;
        ItemStack remainder = stack.copy();

        for (int i = 0; i < inventory.getSlots() && remainder != null; i++) {
            ItemStack existing = inventory.getStackInSlot(i);
            if (existing != null && StorageItemUtils.areItemStacksEqual(existing, remainder, true)) {
                remainder = inventory.insertItem(i, remainder, simulate);
            }
        }

        for (int i = 0; i < inventory.getSlots() && remainder != null; i++) {
            if (inventory.getStackInSlot(i) == null) {
                remainder = inventory.insertItem(i, remainder, simulate);
            }
        }

        return remainder;
    }

    public static ItemStack extractMatching(IStorageInventory inventory, ItemStack match, int amount,
        boolean simulate) {
        if (match == null || amount < 1) return null;
        ItemStack result = null;
        int remaining = amount;

        for (int i = 0; i < inventory.getSlots() && remaining > 0; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null && StorageItemUtils.areItemStacksEqual(stack, match, true)) {
                ItemStack extracted = inventory.extractItem(i, remaining, simulate);
                if (extracted != null) {
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
}
