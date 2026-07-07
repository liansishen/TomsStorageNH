package com.hepdd.toms_storage;

import net.minecraft.item.ItemStack;

public final class StorageItemUtils {

    private StorageItemUtils() {}

    public static boolean areItemStacksEqual(ItemStack stack, ItemStack other, boolean checkNBT) {
        if (stack == null || other == null) return stack == other;
        if (stack.getItem() != other.getItem()) return false;
        if (stack.getItemDamage() != other.getItemDamage()) return false;
        return !checkNBT || ItemStack.areItemStackTagsEqual(stack, other);
    }

    public static ItemStack copyWithSize(ItemStack stack, int size) {
        if (stack == null) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = size;
        return copy;
    }

    public static int getMaxStackSize(ItemStack stack) {
        return stack == null ? 0 : Math.min(stack.getMaxStackSize(), 64);
    }
}
