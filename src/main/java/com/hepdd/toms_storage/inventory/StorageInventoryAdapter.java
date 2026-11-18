package com.hepdd.toms_storage.inventory;

import java.util.function.BooleanSupplier;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

public class StorageInventoryAdapter extends InventoryAdapter {

    protected final TileEntity owner;
    private final Object identity;
    private final BooleanSupplier accessible;
    private final int slotLimit;

    public StorageInventoryAdapter(TileEntity owner, IInventory inventory, ForgeDirection side, Object identity,
        BooleanSupplier accessible, int slotLimit) {
        super(inventory, side);
        this.owner = owner;
        this.identity = identity;
        this.accessible = accessible;
        this.slotLimit = slotLimit;
    }

    protected boolean isAccessible() {
        return accessible.getAsBoolean();
    }

    @Override
    public IInventory getOwnerInventory() {
        return owner instanceof IInventory ? (IInventory) owner : super.getOwnerInventory();
    }

    @Override
    public Object getStorageIdentity() {
        return identity;
    }

    @Override
    public boolean containsInventory(IInventory target) {
        return target == owner || super.containsInventory(target)
            || identity.equals(StorageInventoryProvider.getStorageIdentity(target));
    }

    @Override
    public int getSlots() {
        return isAccessible() ? Math.min(slotLimit, inventory.getSizeInventory()) : 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!isAccessible() || slot < 0 || slot >= getSlots()) return null;
        ItemStack stack = inventory.getStackInSlot(slot);
        return stack != null && canExtract(slot, stack) ? stack : null;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isAccessible() || slot < 0 || slot >= getSlots()) return stack;
        ItemStack remainder = super.insertItem(slot, stack, simulate);
        if (!simulate) owner.markDirty();
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!isAccessible() || slot < 0 || slot >= getSlots()) return null;
        ItemStack extracted = super.extractItem(slot, amount, simulate);
        if (!simulate && extracted != null) owner.markDirty();
        return extracted;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return isAccessible() && slot >= 0 && slot < getSlots() && super.isItemValid(slot, stack);
    }

    @Override
    protected int getSlotLimit(ItemStack stack) {
        int limit = inventory.getInventoryStackLimit();
        return limit > 64 ? limit : Math.min(limit, stack.getMaxStackSize());
    }
}
