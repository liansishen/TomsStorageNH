package com.hepdd.toms_storage.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.crafting.CraftingRemainderHandler;
import com.hepdd.toms_storage.crafting.CraftingStackMatcher;
import com.hepdd.toms_storage.gui.ContainerStorageTerminal;

public final class AutoCraftPreviewInventory {

    private List<StoredItemStack> stacks = new ArrayList<>();
    private List<ItemStack> playerStacks = new ArrayList<>();
    private List<NBTTagCompound> patterns = new ArrayList<>();
    private int activeRequestId = -1;

    public List<StoredItemStack> getStacks(List<StoredItemStack> baseStacks) {
        return activeRequestId >= 0 ? copyStacks(stacks) : copyStacks(baseStacks);
    }

    public boolean isActive() {
        return activeRequestId >= 0;
    }

    public List<ItemStack> getPlayerStacks(InventoryPlayer inventory) {
        return activeRequestId >= 0 ? copyItems(playerStacks) : copyPlayerStacks(inventory);
    }

    public void clear() {
        stacks = new ArrayList<>();
        playerStacks = new ArrayList<>();
        patterns = new ArrayList<>();
        activeRequestId = -1;
    }

    public boolean applyRequest(int requestId, List<StoredItemStack> baseStacks, InventoryPlayer playerInventory,
        ItemStack[][] ingredients, ItemStack output, int count) {
        List<StoredItemStack> preview = activeRequestId >= 0 ? copyStacks(stacks) : copyStacks(baseStacks);
        List<ItemStack> previewPlayer = activeRequestId >= 0 ? copyItems(playerStacks)
            : copyPlayerStacks(playerInventory);
        for (int i = 0; i < Math.max(1, count); i++) {
            for (int slot = 0; slot < ingredients.length; slot++) {
                ItemStack[] candidates = ingredients[slot];
                if (candidates == null || candidates.length == 0) continue;
                ItemStack consumed = consumeCandidate(preview, previewPlayer, candidates);
                if (consumed == null) {
                    NEIDebug.log(
                        "preview failed request=" + requestId
                            + " slot="
                            + slot
                            + " candidates="
                            + describeCandidates(candidates)
                            + " terminal="
                            + describeStored(preview)
                            + " player="
                            + describeItems(previewPlayer));
                    return false;
                }
                ItemStack remainder = CraftingRemainderHandler.getRemainder(consumed);
                if (remainder != null) addStack(preview, remainder);
            }
            if (output != null && output.stackSize > 0) addStack(preview, output);
        }
        stacks = preview;
        playerStacks = previewPlayer;
        activeRequestId = requestId;
        return true;
    }

    public void rememberPattern(NBTTagCompound patternTag) {
        if (patternTag == null) return;
        NBTTagCompound copy = (NBTTagCompound) patternTag.copy();
        copy.removeTag("patterns");
        if (!patterns.contains(copy)) patterns.add(copy);
    }

    public List<NBTTagCompound> getPatterns() {
        List<NBTTagCompound> copy = new ArrayList<>();
        for (NBTTagCompound pattern : patterns) {
            copy.add((NBTTagCompound) pattern.copy());
        }
        return copy;
    }

    public void clearIfMatches(int requestId) {
        if (activeRequestId == requestId) clear();
    }

    public static List<StoredItemStack> getStacks(ContainerStorageTerminal container) {
        return container.getPreviewStacks();
    }

    private static ItemStack consumeCandidate(List<StoredItemStack> preview, List<ItemStack> previewPlayer,
        ItemStack[] candidates) {
        for (ItemStack candidate : candidates) {
            if (candidate == null) continue;
            int amount = Math.max(1, candidate.stackSize);
            for (int i = 0; i < preview.size(); i++) {
                StoredItemStack stored = preview.get(i);
                if (!CraftingStackMatcher.matchesIngredient(candidate, stored.getStack())
                    || stored.getQuantity() < amount) continue;
                long remaining = stored.getQuantity() - amount;
                if (remaining > 0) {
                    preview.set(i, new StoredItemStack(stored.getStack(), remaining));
                } else {
                    preview.remove(i);
                }
                ItemStack consumed = stored.getStack()
                    .copy();
                consumed.stackSize = amount;
                return consumed;
            }
            for (int i = 0; i < previewPlayer.size(); i++) {
                ItemStack stack = previewPlayer.get(i);
                if (!CraftingStackMatcher.matchesIngredient(candidate, stack) || stack.stackSize < amount) continue;
                ItemStack consumed = stack.copy();
                consumed.stackSize = amount;
                stack.stackSize -= amount;
                if (stack.stackSize <= 0) previewPlayer.remove(i);
                return consumed;
            }
        }
        return null;
    }

    private static void addStack(List<StoredItemStack> preview, ItemStack stack) {
        for (int i = 0; i < preview.size(); i++) {
            StoredItemStack stored = preview.get(i);
            if (StorageItemUtils.areItemStacksEqual(stored.getStack(), stack, true)) {
                preview.set(i, new StoredItemStack(stored.getStack(), stored.getQuantity() + stack.stackSize));
                return;
            }
        }
        preview.add(new StoredItemStack(stack, stack.stackSize));
    }

    private static List<StoredItemStack> copyStacks(List<StoredItemStack> source) {
        List<StoredItemStack> copy = new ArrayList<>();
        for (StoredItemStack stack : source) {
            copy.add(new StoredItemStack(stack.getStack(), stack.getQuantity()));
        }
        return copy;
    }

    private static List<ItemStack> copyPlayerStacks(InventoryPlayer inventory) {
        List<ItemStack> copy = new ArrayList<>();
        if (inventory == null) return copy;
        for (ItemStack stack : inventory.mainInventory) {
            if (stack != null && stack.stackSize > 0) copy.add(stack.copy());
        }
        return copy;
    }

    private static List<ItemStack> copyItems(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : source) {
            if (stack != null && stack.stackSize > 0) copy.add(stack.copy());
        }
        return copy;
    }

    private static String describeCandidates(ItemStack[] candidates) {
        List<String> values = new ArrayList<>();
        for (ItemStack stack : candidates) values.add(NEIDebug.stack(stack));
        return values.toString();
    }

    private static String describeStored(List<StoredItemStack> stacks) {
        List<String> values = new ArrayList<>();
        for (StoredItemStack stack : stacks) values.add(NEIDebug.stack(stack.getActualStack()));
        return values.toString();
    }

    private static String describeItems(List<ItemStack> stacks) {
        List<String> values = new ArrayList<>();
        for (ItemStack stack : stacks) values.add(NEIDebug.stack(stack));
        return values.toString();
    }

}
