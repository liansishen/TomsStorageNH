package com.hepdd.toms_storage.inventory;

import net.minecraft.item.ItemStack;

public interface IStorageInventory {

    int getSlots();

    ItemStack getStackInSlot(int slot);

    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    ItemStack extractItem(int slot, int amount, boolean simulate);

    boolean isItemValid(int slot, ItemStack stack);
}
