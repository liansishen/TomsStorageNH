package com.hepdd.toms_storage.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.StoredItemStack;

public class CombinedStorageInventory implements IStorageInventory {

    private final List<IStorageInventory> inventories = new ArrayList<>();
    private final Set<Object> identities = new HashSet<>();
    private final Set<IInventory> knownInventories = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<CombinedStorageInventory> nestedCombined = new ArrayList<>();

    private final LongSupplier tickSupplier;
    private final Runnable mutationListener;
    private final Supplier<List<StoredItemStack>> snapshotSupplier;

    private int[] slotOffsets = new int[0];
    private int cachedTotalSlots;
    private long slotOffsetsTick = Long.MIN_VALUE;
    private boolean slotOffsetsValid;

    public CombinedStorageInventory() {
        this(null, null, null);
    }

    public CombinedStorageInventory(LongSupplier tickSupplier, Runnable mutationListener,
        Supplier<List<StoredItemStack>> snapshotSupplier) {
        this.tickSupplier = tickSupplier;
        this.mutationListener = mutationListener;
        this.snapshotSupplier = snapshotSupplier;
    }

    public void clear() {
        inventories.clear();
        identities.clear();
        knownInventories.clear();
        nestedCombined.clear();
        invalidateSlotOffsets();
    }

    public void invalidateSlotOffsets() {
        slotOffsetsValid = false;
    }

    void onMutated() {
        if (mutationListener != null) {
            mutationListener.run();
        }
    }

    public void add(IStorageInventory inventory) {
        if (inventory instanceof CombinedStorageInventory) {
            CombinedStorageInventory other = (CombinedStorageInventory) inventory;
            knownInventories.addAll(other.knownInventories);
            for (IStorageInventory child : other.getInventories()) {
                add(child);
            }
        } else if (inventory != null) {
            if (inventory instanceof InventoryAdapter) {
                InventoryAdapter adapter = (InventoryAdapter) inventory;
                IInventory raw = adapter.getInventory();
                if (raw != null) knownInventories.add(raw);
                IInventory owner = adapter.getOwnerInventory();
                if (owner != null) knownInventories.add(owner);
                Object identity = adapter.getStorageIdentity();
                if (identity != null && !identities.add(identity)) return;
            }
            inventories.add(inventory);
            invalidateSlotOffsets();
        }
    }

    public List<IStorageInventory> getInventories() {
        return inventories;
    }

    public boolean containsInventory(IInventory target) {
        if (target == null) return false;
        if (knownInventories.contains(target)) return true;
        Object targetIdentity = StorageInventoryProvider.getStorageIdentity(target);
        if (targetIdentity != null && identities.contains(targetIdentity)) return true;
        for (CombinedStorageInventory child : nestedCombined) {
            if (child.containsInventory(target)) return true;
        }
        return false;
    }

    @Override
    public int getSlots() {
        if (ensureCachedOffsets()) {
            return cachedTotalSlots;
        }
        int slots = 0;
        for (IStorageInventory inventory : inventories) {
            slots += inventory.getSlots();
        }
        return slots;
    }

    @Override
    public void forEachStack(Consumer<ItemStack> consumer) {
        for (IStorageInventory inventory : inventories) inventory.forEachStack(consumer);
    }

    @Override
    public long packSlotStats() {
        long total = 0L;
        long free = 0L;
        for (IStorageInventory inventory : inventories) {
            long packed = inventory.packSlotStats();
            total += packed >>> 32;
            free += packed & 0xFFFFFFFFL;
        }
        return (total << 32) | (free & 0xFFFFFFFFL);
    }

    @Override
    public List<StoredItemStack> getAggregatedStacks() {
        return snapshotSupplier != null ? snapshotSupplier.get() : StorageInventoryUtils.getStacks(this);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        SlotRef ref = getSlotRef(slot);
        return ref == null ? null : ref.inventory.getStackInSlot(ref.slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        SlotRef ref = getSlotRef(slot);
        if (ref == null) return stack;
        int offered = stack == null ? 0 : stack.stackSize;
        ItemStack remainder = ref.inventory.insertItem(ref.slot, stack, simulate);
        if (!simulate && offered > 0 && (remainder == null || remainder.stackSize < offered)) {
            invalidateSlotOffsets();
            onMutated();
        }
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        SlotRef ref = getSlotRef(slot);
        if (ref == null) return null;
        ItemStack extracted = ref.inventory.extractItem(ref.slot, amount, simulate);
        if (!simulate && extracted != null && extracted.stackSize > 0) {
            onMutated();
        }
        return extracted;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        SlotRef ref = getSlotRef(slot);
        return ref != null && ref.inventory.isItemValid(ref.slot, stack);
    }

    private boolean ensureCachedOffsets() {
        if (tickSupplier == null) return false;
        long now = tickSupplier.getAsLong();
        if (now == Long.MIN_VALUE) return false;
        if (slotOffsetsValid && slotOffsetsTick == now) return true;

        int count = inventories.size();
        if (slotOffsets.length < count + 1) {
            slotOffsets = new int[count + 1];
        }
        int sum = 0;
        slotOffsets[0] = 0;
        for (int i = 0; i < count; i++) {
            sum += Math.max(
                0,
                inventories.get(i)
                    .getSlots());
            slotOffsets[i + 1] = sum;
        }
        cachedTotalSlots = sum;
        slotOffsetsTick = now;
        slotOffsetsValid = true;
        return true;
    }

    private SlotRef getSlotRef(int slot) {
        if (slot < 0) return null;
        if (ensureCachedOffsets()) {
            int count = inventories.size();
            if (count == 0 || slot >= cachedTotalSlots) return null;
            int low = 0;
            int high = count - 1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (slotOffsets[mid + 1] <= slot) {
                    low = mid + 1;
                } else if (slotOffsets[mid] > slot) {
                    high = mid - 1;
                } else {
                    return new SlotRef(inventories.get(mid), slot - slotOffsets[mid]);
                }
            }
            return null;
        }

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
