package com.hepdd.toms_storage.crafting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

public final class CraftingInventorySnapshot {

    private final Map<Item, List<Entry>> terminalByItem = new IdentityHashMap<>();
    private final Map<Item, List<Entry>> playerByItem = new IdentityHashMap<>();
    private final Map<StackKey, KindGroup> kindGroups = new HashMap<>();
    private final boolean usePlayerInventory;

    public CraftingInventorySnapshot(TileEntityStorageTerminal terminal, EntityPlayerMP player,
        boolean usePlayerInventory) {
        this(
            terminal.getStacks(),
            usePlayerInventory ? Arrays.asList(player.inventory.mainInventory) : Collections.emptyList(),
            usePlayerInventory);
    }

    public CraftingInventorySnapshot(List<StoredItemStack> storedStacks, List<ItemStack> playerStacks) {
        this(storedStacks, playerStacks, true);
    }

    public CraftingInventorySnapshot(List<StoredItemStack> storedStacks, List<ItemStack> playerStacks,
        boolean usePlayerInventory) {
        this.usePlayerInventory = usePlayerInventory;
        if (storedStacks != null) {
            for (StoredItemStack stored : storedStacks) {
                if (stored == null) continue;
                ItemStack stack = stored.getStack();
                long quantity = stored.getQuantity();
                if (stack == null || stack.getItem() == null || quantity <= 0) continue;
                int capped = (int) Math.min(Integer.MAX_VALUE, quantity);
                addInitialTerminalEntry(stack, capped);
            }
        }

        if (usePlayerInventory && playerStacks != null) {
            for (int i = 0; i < playerStacks.size(); i++) {
                ItemStack stack = playerStacks.get(i);
                if (stack != null && stack.getItem() != null && stack.stackSize > 0) {
                    addInitialPlayerEntry(stack, stack.stackSize, i);
                }
            }
        }
    }

    private CraftingInventorySnapshot(boolean usePlayerInventory) {
        this.usePlayerInventory = usePlayerInventory;
    }

    public CraftingInventorySnapshot copy() {
        CraftingInventorySnapshot copy = new CraftingInventorySnapshot(this.usePlayerInventory);
        for (Map.Entry<Item, List<Entry>> mapEntry : terminalByItem.entrySet()) {
            List<Entry> srcBucket = mapEntry.getValue();
            List<Entry> dstBucket = new ArrayList<>(srcBucket.size());
            for (int i = 0; i < srcBucket.size(); i++) {
                Entry srcEntry = srcBucket.get(i);
                KindGroup dstGroup = copy.kindGroups.computeIfAbsent(srcEntry.group.key, KindGroup::new);
                Entry dstEntry = new Entry(srcEntry.stack, dstGroup, srcEntry.amount, srcEntry.playerSlot);
                dstGroup.terminalEntry = dstEntry;
                dstGroup.totalAmount += srcEntry.amount;
                dstBucket.add(dstEntry);
            }
            copy.terminalByItem.put(mapEntry.getKey(), dstBucket);
        }
        for (Map.Entry<Item, List<Entry>> mapEntry : playerByItem.entrySet()) {
            List<Entry> srcBucket = mapEntry.getValue();
            List<Entry> dstBucket = new ArrayList<>(srcBucket.size());
            for (int i = 0; i < srcBucket.size(); i++) {
                Entry srcEntry = srcBucket.get(i);
                KindGroup dstGroup = copy.kindGroups.computeIfAbsent(srcEntry.group.key, KindGroup::new);
                Entry dstEntry = new Entry(srcEntry.stack, dstGroup, srcEntry.amount, srcEntry.playerSlot);
                dstGroup.totalAmount += srcEntry.amount;
                dstBucket.add(dstEntry);
            }
            copy.playerByItem.put(mapEntry.getKey(), dstBucket);
        }
        return copy;
    }

    public Extraction reserve(ItemStack[] candidates) {
        if (candidates == null) return null;
        for (ItemStack candidate : candidates) {
            if (candidate == null || candidate.getItem() == null) continue;
            int amount = Math.max(1, candidate.stackSize);
            Extraction extraction = reserveFromBucket(
                terminalByItem.get(candidate.getItem()),
                candidate,
                amount,
                false);
            if (extraction != null) return extraction;
            if (usePlayerInventory) {
                extraction = reserveFromBucket(playerByItem.get(candidate.getItem()), candidate, amount, true);
                if (extraction != null) return extraction;
            }
        }
        return null;
    }

    public Extraction reserveLargest(ItemStack[] candidates) {
        if (candidates == null) return null;
        KindGroup bestGroup = null;
        int bestAmount = 0;
        long largest = 0;
        for (ItemStack candidate : candidates) {
            if (candidate == null || candidate.getItem() == null) continue;
            int amount = Math.max(1, candidate.stackSize);
            Item item = candidate.getItem();
            List<Entry> termBucket = terminalByItem.get(item);
            if (termBucket != null) {
                for (int i = 0; i < termBucket.size(); i++) {
                    Entry entry = termBucket.get(i);
                    if (entry.amount < amount || !CraftingStackMatcher.matchesIngredient(candidate, entry.stack)) {
                        continue;
                    }
                    long crafts = entry.group.totalAmount / amount;
                    if (crafts > largest) {
                        largest = crafts;
                        bestGroup = entry.group;
                        bestAmount = amount;
                    }
                }
            }
            if (usePlayerInventory) {
                List<Entry> playerBucket = playerByItem.get(item);
                if (playerBucket != null) {
                    for (int i = 0; i < playerBucket.size(); i++) {
                        Entry entry = playerBucket.get(i);
                        if (entry.amount < amount || !CraftingStackMatcher.matchesIngredient(candidate, entry.stack)) {
                            continue;
                        }
                        long crafts = entry.group.totalAmount / amount;
                        if (crafts > largest) {
                            largest = crafts;
                            bestGroup = entry.group;
                            bestAmount = amount;
                        }
                    }
                }
            }
        }
        if (bestGroup == null) return null;
        return reserveFromGroup(bestGroup, bestAmount);
    }

    public void addTerminal(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return;
        StackKey lookupKey = new StackKey(stack);
        KindGroup group = kindGroups.get(lookupKey);
        if (group != null && group.terminalEntry != null) {
            int delta = saturatedDelta(group.terminalEntry.amount, stack.stackSize);
            group.terminalEntry.amount += delta;
            group.totalAmount += delta;
            return;
        }

        ItemStack ownedTemplate = StorageItemUtils.copyWithSize(stack, 1);
        if (group == null) {
            StackKey ownedKey = new StackKey(ownedTemplate);
            group = new KindGroup(ownedKey);
            kindGroups.put(ownedKey, group);
        }
        Entry entry = new Entry(ownedTemplate, group, stack.stackSize, -1);
        group.terminalEntry = entry;
        group.totalAmount += stack.stackSize;
        terminalByItem.computeIfAbsent(ownedTemplate.getItem(), k -> new ArrayList<>(2))
            .add(entry);
    }

    public long getAvailableAmount(ItemStack candidate) {
        if (candidate == null || candidate.getItem() == null) return 0L;
        long total = 0L;
        Item item = candidate.getItem();
        List<Entry> termBucket = terminalByItem.get(item);
        if (termBucket != null) {
            for (int i = 0; i < termBucket.size(); i++) {
                Entry entry = termBucket.get(i);
                if (entry.amount > 0 && CraftingStackMatcher.matchesIngredient(candidate, entry.stack)) {
                    total += entry.amount;
                }
            }
        }
        if (usePlayerInventory) {
            List<Entry> playerBucket = playerByItem.get(item);
            if (playerBucket != null) {
                for (int i = 0; i < playerBucket.size(); i++) {
                    Entry entry = playerBucket.get(i);
                    if (entry.amount > 0 && CraftingStackMatcher.matchesIngredient(candidate, entry.stack)) {
                        total += entry.amount;
                    }
                }
            }
        }
        return total;
    }

    public boolean hasPlayerInventory() {
        return usePlayerInventory;
    }

    private void addInitialTerminalEntry(ItemStack stack, int amount) {
        StackKey key = new StackKey(stack);
        KindGroup group = kindGroups.get(key);
        if (group == null) {
            group = new KindGroup(key);
            kindGroups.put(key, group);
        }
        if (group.terminalEntry != null) {
            int delta = saturatedDelta(group.terminalEntry.amount, amount);
            group.terminalEntry.amount += delta;
            group.totalAmount += delta;
            return;
        }
        Entry entry = new Entry(stack, group, amount, -1);
        group.terminalEntry = entry;
        group.totalAmount += amount;
        terminalByItem.computeIfAbsent(stack.getItem(), k -> new ArrayList<>(2))
            .add(entry);
    }

    private void addInitialPlayerEntry(ItemStack stack, int amount, int playerSlot) {
        StackKey key = new StackKey(stack);
        KindGroup group = kindGroups.get(key);
        if (group == null) {
            group = new KindGroup(key);
            kindGroups.put(key, group);
        }
        Entry entry = new Entry(stack, group, amount, playerSlot);
        group.totalAmount += amount;
        playerByItem.computeIfAbsent(stack.getItem(), k -> new ArrayList<>(2))
            .add(entry);
    }

    private Extraction reserveFromGroup(KindGroup group, int amount) {
        Entry termEntry = group.terminalEntry;
        if (termEntry != null && termEntry.amount >= amount) {
            termEntry.amount -= amount;
            group.totalAmount -= amount;
            return new Extraction(StorageItemUtils.copyWithSize(termEntry.stack, amount), false, -1);
        }
        if (usePlayerInventory) {
            List<Entry> playerBucket = playerByItem.get(group.key.item);
            if (playerBucket != null) {
                for (int i = 0; i < playerBucket.size(); i++) {
                    Entry entry = playerBucket.get(i);
                    if (entry.group == group && entry.amount >= amount) {
                        entry.amount -= amount;
                        group.totalAmount -= amount;
                        return new Extraction(
                            StorageItemUtils.copyWithSize(entry.stack, amount),
                            true,
                            entry.playerSlot);
                    }
                }
            }
        }
        return null;
    }

    private static Extraction reserveFromBucket(List<Entry> bucket, ItemStack candidate, int amount,
        boolean fromPlayer) {
        if (bucket == null) return null;
        for (int i = 0; i < bucket.size(); i++) {
            Entry entry = bucket.get(i);
            if (entry.amount >= amount && CraftingStackMatcher.matchesIngredient(candidate, entry.stack)) {
                entry.amount -= amount;
                entry.group.totalAmount -= amount;
                return new Extraction(StorageItemUtils.copyWithSize(entry.stack, amount), fromPlayer, entry.playerSlot);
            }
        }
        return null;
    }

    private static int saturatedDelta(int current, int add) {
        int maxAdd = Integer.MAX_VALUE - current;
        return Math.min(maxAdd, add);
    }

    public static final class Extraction {

        private final ItemStack stack;
        private final boolean fromPlayer;
        private final int playerSlot;

        private Extraction(ItemStack stack, boolean fromPlayer, int playerSlot) {
            this.stack = stack;
            this.fromPlayer = fromPlayer;
            this.playerSlot = playerSlot;
        }

        public ItemStack getStack() {
            return stack;
        }

        public boolean isFromPlayer() {
            return fromPlayer;
        }

        public int getPlayerSlot() {
            return playerSlot;
        }
    }

    private static final class StackKey {

        private final Item item;
        private final int damage;
        private final NBTTagCompound tag;
        private final int hash;

        private StackKey(ItemStack stack) {
            this.item = stack.getItem();
            this.damage = stack.getItemDamage();
            this.tag = stack.getTagCompound();
            int h = System.identityHashCode(this.item);
            h = 31 * h + this.damage;
            h = 31 * h + (this.tag == null ? 0 : this.tag.hashCode());
            this.hash = h;
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
            return damage == other.damage && item == other.item && Objects.equals(tag, other.tag);
        }
    }

    private static final class KindGroup {

        private final StackKey key;
        private long totalAmount;
        private Entry terminalEntry;

        private KindGroup(StackKey key) {
            this.key = key;
        }
    }

    private static final class Entry {

        private final ItemStack stack;
        private final KindGroup group;
        private int amount;
        private final int playerSlot;

        private Entry(ItemStack stack, KindGroup group, int amount, int playerSlot) {
            this.stack = stack;
            this.group = group;
            this.amount = amount;
            this.playerSlot = playerSlot;
        }
    }
}
