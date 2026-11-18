package com.hepdd.toms_storage.inventory;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.capability.item.ItemIO;
import com.gtnewhorizon.gtnhlib.item.FastImmutableItemStack;
import com.gtnewhorizon.gtnhlib.item.ImmutableItemStack;
import com.gtnewhorizon.gtnhlib.item.InventoryIterator;
import com.hepdd.toms_storage.StorageItemUtils;

final class CapabilityInventoryAdapter extends StorageInventoryAdapter {

    private final ItemIO io;
    private final boolean drawers;

    CapabilityInventoryAdapter(TileEntity owner, IInventory inventory, ForgeDirection side, ItemIO io,
        boolean drawers) {
        super(
            owner,
            inventory,
            side,
            StorageInventoryProvider.getStorageIdentity(inventory),
            () -> true,
            Integer.MAX_VALUE);
        this.io = io;
        this.drawers = drawers;
    }

    @Override
    public boolean hasFastTransfer() {
        return true;
    }

    @Override
    public int getSlots() {
        if (!isAccessible()) return 0;
        io.resetSource();
        InventoryIterator iterator = io.sourceIterator();
        if (iterator == null) return 0;
        int slots = 0;
        while (iterator.hasNext()) {
            iterator.next();
            slots++;
        }
        return slots;
    }

    @Override
    public void forEachStack(Consumer<ItemStack> consumer) {
        if (!isAccessible()) return;
        io.resetSource();
        InventoryIterator iterator = io.sourceIterator();
        if (iterator == null) return;
        int slot = 0;
        while (iterator.hasNext()) {
            ImmutableItemStack value = iterator.next();
            ItemStack stack = value == null || value.isEmpty() ? null : value.toStackFast();
            consumer.accept(stack != null && canExtract(outputSlot(slot), stack) ? stack : null);
            slot++;
        }
    }

    @Override
    public long packSlotStats() {
        if (!isAccessible()) return 0L;
        io.resetSource();
        InventoryIterator iterator = io.sourceIterator();
        if (iterator == null) return 0L;
        int total = 0;
        int free = 0;
        while (iterator.hasNext()) {
            ImmutableItemStack value = iterator.next();
            ItemStack stack = value == null || value.isEmpty() ? null : value.toStackFast();
            if (stack == null || stack.stackSize <= 0 || !canExtract(outputSlot(total), stack)) {
                free++;
            }
            total++;
        }
        return ((long) total << 32) | (free & 0xFFFFFFFFL);
    }

    @Override
    public ItemStack insertIntoInventory(ItemStack stack, boolean mergeOnly, boolean simulate) {
        if (stack == null || stack.stackSize <= 0 || !isAccessible()) return stack;
        io.resetSink();
        InventoryIterator iterator = simulate ? io.simulatedSinkIterator() : io.sinkIterator();
        if (iterator == null) return stack;

        int remaining = stack.stackSize;
        ItemStack offerStack = null;
        FastImmutableItemStack view = null;
        boolean changed = false;

        for (int slot = 0; iterator.hasNext() && remaining > 0; slot++) {
            ImmutableItemStack existing = iterator.next();
            boolean hasExisting = existing != null && !existing.isEmpty();
            if (mergeOnly != hasExisting) continue;
            if (mergeOnly && !matchesImmutable(existing, stack)) continue;
            if (!canInsert(inputSlot(slot), stack)) continue;

            if (offerStack == null) {
                offerStack = StorageItemUtils.copyWithSize(stack, remaining);
                view = new FastImmutableItemStack(offerStack);
            } else {
                offerStack.stackSize = remaining;
            }

            int after = iterator.insert(view, false);
            if (after < remaining) {
                remaining = after;
                changed = true;
            }
        }

        if (!simulate && changed) {
            inventory.markDirty();
            owner.markDirty();
        }
        if (remaining <= 0) return null;
        return remaining == stack.stackSize ? stack : StorageItemUtils.copyWithSize(stack, remaining);
    }

    @Override
    public ItemStack extractMatching(ItemStack match, int amount, boolean simulate) {
        if (match == null || amount <= 0 || !isAccessible()) return null;
        io.resetSource();
        InventoryIterator iterator = io.sourceIterator();
        if (iterator == null) return null;

        ItemStack result = null;
        int remaining = amount;
        boolean changed = false;

        for (int slot = 0; iterator.hasNext() && remaining > 0; slot++) {
            ImmutableItemStack current = iterator.next();
            if (current == null || current.isEmpty() || !matchesImmutable(current, match)) continue;
            ItemStack checkStack = current.toStackFast();
            if (!canExtract(outputSlot(slot), checkStack)) continue;

            if (simulate) {
                int take = Math.min(remaining, current.getStackSize());
                if (take > 0) {
                    if (result == null) {
                        result = current.toStack(take);
                    } else {
                        result.stackSize += take;
                    }
                    remaining -= take;
                }
            } else {
                ItemStack extracted = iterator.extract(remaining, false);
                if (extracted != null && extracted.stackSize > 0) {
                    if (result == null) {
                        result = extracted;
                    } else {
                        result.stackSize += extracted.stackSize;
                    }
                    remaining -= extracted.stackSize;
                    changed = true;
                }
            }
        }

        if (changed) {
            inventory.markDirty();
            owner.markDirty();
        }
        return result;
    }

    private static boolean matchesImmutable(ImmutableItemStack immutable, ItemStack target) {
        return immutable != null && target != null
            && immutable.getItem() == target.getItem()
            && immutable.getItemMeta() == target.getItemDamage()
            && Objects.equals(immutable.getTag(), target.getTagCompound());
    }

    private InventoryIterator iteratorAt(int slot, boolean sink, boolean simulate) {
        if (slot < 0) return null;
        io.resetSource();
        io.resetSink();
        InventoryIterator iterator = sink ? (simulate ? io.simulatedSinkIterator() : io.sinkIterator())
            : io.sourceIterator();
        if (iterator == null) return null;
        for (int index = 0; iterator.hasNext(); index++) {
            iterator.next();
            if (index == slot) return iterator;
        }
        return null;
    }

    private int inputSlot(int slot) {
        return drawers ? slot : 0;
    }

    private int outputSlot(int slot) {
        return drawers ? inventory.getSizeInventory() / 2 + slot : 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!isAccessible() || slot < 0) return null;
        io.resetSource();
        InventoryIterator iterator = io.sourceIterator();
        if (iterator == null) return null;
        for (int index = 0; iterator.hasNext(); index++) {
            ImmutableItemStack stack = iterator.next();
            if (index == slot) {
                if (stack == null || stack.isEmpty()) return null;
                ItemStack copy = stack.toStack();
                return canExtract(outputSlot(slot), copy) ? copy : null;
            }
        }
        return null;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isAccessible() || stack == null || stack.stackSize <= 0 || !canInsert(inputSlot(slot), stack)) {
            return stack;
        }
        InventoryIterator iterator = iteratorAt(slot, true, simulate);
        if (iterator == null) return stack;
        int remaining = iterator.insert(new FastImmutableItemStack(stack), false);
        if (!simulate && remaining < stack.stackSize) {
            inventory.markDirty();
            owner.markDirty();
        }
        return remaining == 0 ? null : StorageItemUtils.copyWithSize(stack, remaining);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!isAccessible() || amount <= 0) return null;
        ItemStack stack = getStackInSlot(slot);
        if (stack == null) return null;
        if (simulate) return StorageItemUtils.copyWithSize(stack, Math.min(amount, stack.stackSize));
        InventoryIterator iterator = iteratorAt(slot, false, false);
        ItemStack extracted = iterator == null ? null : iterator.extract(amount, false);
        if (extracted != null) {
            inventory.markDirty();
            owner.markDirty();
        }
        return extracted;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return isAccessible() && slot >= 0 && slot < getSlots() && canInsert(inputSlot(slot), stack);
    }
}
