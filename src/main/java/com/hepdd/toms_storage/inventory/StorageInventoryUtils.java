package com.hepdd.toms_storage.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.StoredItemStack;

public final class StorageInventoryUtils {

    private StorageInventoryUtils() {}

    public static boolean isChunkLoaded(World world, int blockX, int blockZ) {
        if (world == null) return false;
        IChunkProvider provider = world.getChunkProvider();
        return provider != null ? provider.chunkExists(blockX >> 4, blockZ >> 4)
            : world.blockExists(blockX, 64, blockZ);
    }

    public static List<StoredItemStack> getStacks(IStorageInventory inventory) {
        if (inventory == null) return new ArrayList<>();
        Map<StackKey, StoredItemStack> stacks = new LinkedHashMap<>();
        StackKey probe = new StackKey();
        inventory.forEachStack(stack -> {
            if (stack != null && stack.stackSize > 0 && stack.getItem() != null) {
                probe.set(stack);
                StoredItemStack existing = stacks.get(probe);
                if (existing == null) {
                    StoredItemStack created = new StoredItemStack(stack);
                    stacks.put(probe.freeze(created.getStack()), created);
                } else {
                    existing.grow(stack.stackSize);
                }
            }
        });
        return new ArrayList<>(stacks.values());
    }

    public static ItemStack insertStacked(IStorageInventory inventory, ItemStack stack, boolean simulate) {
        if (stack == null || stack.stackSize < 1) return null;
        if (inventory == null) return stack.copy();
        int initialSize = stack.stackSize;
        List<IStorageInventory> inventories = getInventories(inventory);
        int[] slotCounts = new int[inventories.size()];
        Arrays.fill(slotCounts, -1);
        ItemStack remainder = stack.copy();
        // Merge across the whole network before filling any empty slot.
        for (int pass = 0; pass < 2 && remainder != null; pass++) {
            boolean mergeOnly = pass == 0;
            for (int child = 0; child < inventories.size() && remainder != null; child++) {
                IStorageInventory current = inventories.get(child);
                if (current.hasFastTransfer()) {
                    remainder = current.insertIntoInventory(remainder, mergeOnly, simulate);
                    continue;
                }
                if (slotCounts[child] < 0) slotCounts[child] = current.getSlots();
                for (int slot = 0; slot < slotCounts[child] && remainder != null; slot++) {
                    ItemStack existing = current.getStackInSlot(slot);
                    boolean accepts = mergeOnly
                        ? existing != null && StorageItemUtils.areItemStacksEqual(existing, remainder, true)
                        : existing == null;
                    if (accepts) {
                        int offered = remainder.stackSize;
                        remainder = current.insertItem(slot, remainder, simulate);
                        // Filing cabinets can append an input slot after a partial insertion.
                        if (!simulate && remainder != null
                            && remainder.stackSize < offered
                            && slot == slotCounts[child] - 1) {
                            slotCounts[child] = current.getSlots();
                        }
                    }
                }
            }
        }
        if (!simulate && (remainder == null || remainder.stackSize < initialSize)
            && inventory instanceof CombinedStorageInventory) {
            CombinedStorageInventory combined = (CombinedStorageInventory) inventory;
            combined.invalidateSlotOffsets();
            combined.onMutated();
        }
        return remainder;
    }

    public static ItemStack extractMatching(IStorageInventory inventory, ItemStack match, int amount,
        boolean simulate) {
        if (inventory == null || match == null || amount < 1) return null;
        ItemStack result = null;
        int remaining = amount;
        for (IStorageInventory current : getInventories(inventory)) {
            if (remaining <= 0) break;
            ItemStack extracted = current.extractMatching(match, remaining, simulate);
            if (extracted != null && extracted.stackSize > 0) {
                if (result == null) {
                    result = extracted;
                } else {
                    result.stackSize += extracted.stackSize;
                }
                remaining -= extracted.stackSize;
            }
        }
        if (!simulate && result != null && result.stackSize > 0 && inventory instanceof CombinedStorageInventory) {
            ((CombinedStorageInventory) inventory).onMutated();
        }
        return result;
    }

    private static List<IStorageInventory> getInventories(IStorageInventory inventory) {
        return inventory instanceof CombinedStorageInventory ? ((CombinedStorageInventory) inventory).getInventories()
            : Collections.singletonList(inventory);
    }

    private static final class StackKey {

        private Item item;
        private int damage;
        private NBTTagCompound tag;
        private int hash;

        void set(ItemStack stack) {
            this.item = stack.getItem();
            this.damage = stack.getItemDamage();
            this.tag = stack.getTagCompound();
            int h = System.identityHashCode(this.item);
            h = 31 * h + this.damage;
            h = 31 * h + (this.tag != null ? this.tag.hashCode() : 0);
            this.hash = h;
        }

        StackKey freeze(ItemStack copiedTemplate) {
            StackKey frozen = new StackKey();
            frozen.item = copiedTemplate.getItem();
            frozen.damage = copiedTemplate.getItemDamage();
            frozen.tag = copiedTemplate.getTagCompound();
            frozen.hash = this.hash;
            return frozen;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof StackKey)) return false;
            StackKey other = (StackKey) obj;
            return this.damage == other.damage && this.item == other.item && Objects.equals(this.tag, other.tag);
        }
    }
}
