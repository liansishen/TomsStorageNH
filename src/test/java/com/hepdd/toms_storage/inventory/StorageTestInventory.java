package com.hepdd.toms_storage.inventory;

import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.capability.CapabilityProvider;
import com.gtnewhorizon.gtnhlib.capability.item.ItemIO;
import com.gtnewhorizon.gtnhlib.item.AbstractInventoryIterator;
import com.gtnewhorizon.gtnhlib.item.ImmutableItemStack;
import com.gtnewhorizon.gtnhlib.item.InventoryIterator;
import com.gtnewhorizon.gtnhlib.item.SimpleItemIO;
import com.hepdd.toms_storage.StorageItemUtils;

public class StorageTestInventory extends TileEntityChest implements ISidedInventory, CapabilityProvider {

    public ItemStack[] stacks;
    public boolean input = true;
    public boolean output = true;
    public int limit = 64;
    public int extractionLimit = Integer.MAX_VALUE;
    public int[] exposed;
    public ItemIO io;
    public int dirtyCount;
    public int stackReads;
    public int sizeQueries;

    public StorageTestInventory() {
        this(3);
    }

    public StorageTestInventory(int slots) {
        stacks = new ItemStack[slots];
        exposed = new int[slots];
        for (int slot = 0; slot < slots; slot++) exposed[slot] = slot;
    }

    @Override
    public int getSizeInventory() {
        sizeQueries++;
        return stacks.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        stackReads++;
        return stacks[slot];
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        stacks[slot] = stack;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        ItemStack current = stacks[slot];
        if (current == null) return null;
        int extracted = Math.min(Math.min(amount, extractionLimit), current.stackSize);
        ItemStack result = StorageItemUtils.copyWithSize(current, extracted);
        current.stackSize -= extracted;
        if (current.stackSize == 0) stacks[slot] = null;
        return result;
    }

    @Override
    public int getInventoryStackLimit() {
        return limit;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return input;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return exposed;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return input;
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return output;
    }

    @Override
    public void markDirty() {
        dirtyCount++;
    }

    @Override
    public <T> T getCapability(Class<T> capability, ForgeDirection side) {
        return capability == ItemIO.class ? capability.cast(io) : null;
    }

    public static class BulkIO extends SimpleItemIO {

        public final ItemStack[] contents;
        public int capacity = 100000;
        public int iteratorCalls;

        public BulkIO(ItemStack... contents) {
            this.contents = contents;
        }

        @Override
        protected InventoryIterator iterator(int[] allowedSlots) {
            iteratorCalls++;
            return createIterator(allowedSlots, false);
        }

        @Override
        public InventoryIterator simulatedSinkIterator() {
            return createIterator(allowedSinkSlots, true);
        }

        private InventoryIterator createIterator(int[] allowedSlots, boolean simulate) {
            int[] slots = new int[contents.length];
            for (int slot = 0; slot < slots.length; slot++) slots[slot] = slot;
            return new AbstractInventoryIterator(slots, allowedSlots) {

                @Override
                protected ItemStack getStackInSlot(int slot) {
                    return contents[slot];
                }

                @Override
                public ImmutableItemStack next() {
                    ImmutableItemStack value = super.next();
                    if (value == null) return null;
                    // A plain JVM has no registered Minecraft feather for ItemUtil's metadata accessor.
                    return new com.gtnewhorizon.gtnhlib.item.FastImmutableItemStack(contents[getCurrentSlot()]) {

                        @Override
                        public int getItemMeta() {
                            return stack.getItemDamage();
                        }
                    };
                }

                @Override
                public ItemStack extract(int amount, boolean forced) {
                    int slot = getCurrentSlot();
                    ItemStack current = contents[slot];
                    if (current == null) return null;
                    int extracted = Math.min(amount, current.stackSize);
                    ItemStack result = StorageItemUtils.copyWithSize(current, extracted);
                    if (!simulate) {
                        current.stackSize -= extracted;
                        if (current.stackSize == 0) contents[slot] = null;
                    }
                    return result;
                }

                @Override
                public int insert(ImmutableItemStack stack, boolean forced) {
                    int slot = getCurrentSlot();
                    ItemStack current = contents[slot];
                    ItemStack offered = ((com.gtnewhorizon.gtnhlib.item.FastImmutableItemStack) stack).stack;
                    if (current != null && !StorageItemUtils.areItemStacksEqual(offered, current, true))
                        return stack.getStackSize();
                    int stored = current == null ? 0 : current.stackSize;
                    int inserted = Math.min(stack.getStackSize(), Math.max(0, capacity - stored));
                    if (!simulate) contents[slot] = StorageItemUtils.copyWithSize(offered, stored + inserted);
                    return stack.getStackSize() - inserted;
                }
            };
        }
    }
}
