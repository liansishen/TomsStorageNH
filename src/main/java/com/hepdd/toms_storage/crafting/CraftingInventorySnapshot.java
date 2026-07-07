package com.hepdd.toms_storage.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

public final class CraftingInventorySnapshot {

    private final List<Entry> terminalStacks = new ArrayList<>();
    private final List<Entry> playerStacks = new ArrayList<>();
    private final boolean usePlayerInventory;

    public CraftingInventorySnapshot(TileEntityStorageTerminal terminal, EntityPlayerMP player,
        boolean usePlayerInventory) {
        this.usePlayerInventory = usePlayerInventory;

        for (StoredItemStack stored : terminal.getStacks()) {
            ItemStack stack = stored.getStack();
            if (stack != null && stored.getQuantity() > 0) {
                terminalStacks.add(new Entry(stack, (int) Math.min(Integer.MAX_VALUE, stored.getQuantity()), -1));
            }
        }

        if (usePlayerInventory) {
            for (int i = 0; i < player.inventory.mainInventory.length; i++) {
                ItemStack stack = player.inventory.mainInventory[i];
                if (stack != null && stack.stackSize > 0) playerStacks.add(new Entry(stack, stack.stackSize, i));
            }
        }
    }

    public Extraction reserve(ItemStack[] candidates) {
        if (candidates == null) return null;
        for (ItemStack candidate : candidates) {
            if (candidate == null) continue;
            int amount = Math.max(1, candidate.stackSize);
            Extraction extraction = reserveFrom(terminalStacks, candidate, amount, false);
            if (extraction != null) return extraction;
            if (usePlayerInventory) {
                extraction = reserveFrom(playerStacks, candidate, amount, true);
                if (extraction != null) return extraction;
            }
        }
        return null;
    }

    public void addTerminal(ItemStack stack) {
        addTo(terminalStacks, stack);
    }

    public boolean hasPlayerInventory() {
        return usePlayerInventory;
    }

    private static Extraction reserveFrom(List<Entry> entries, ItemStack candidate, int amount, boolean fromPlayer) {
        for (Entry entry : entries) {
            if (entry.amount >= amount && CraftingStackMatcher.matchesIngredient(candidate, entry.stack)) {
                entry.amount -= amount;
                ItemStack reserved = entry.stack.copy();
                reserved.stackSize = amount;
                return new Extraction(reserved, fromPlayer, entry.playerSlot);
            }
        }
        return null;
    }

    private static void addTo(List<Entry> entries, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return;
        for (Entry entry : entries) {
            if (StorageItemUtils.areItemStacksEqual(entry.stack, stack, true)) {
                entry.amount += stack.stackSize;
                return;
            }
        }
        entries.add(new Entry(stack, stack.stackSize, -1));
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

    private static final class Entry {

        private final ItemStack stack;
        private int amount;
        private final int playerSlot;

        private Entry(ItemStack stack, int amount, int playerSlot) {
            this.stack = stack.copy();
            this.stack.stackSize = 1;
            this.amount = amount;
            this.playerSlot = playerSlot;
        }
    }
}
