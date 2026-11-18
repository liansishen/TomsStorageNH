package com.hepdd.toms_storage.nei;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot;
import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot.Extraction;
import com.hepdd.toms_storage.crafting.CraftingPattern;
import com.hepdd.toms_storage.crafting.CraftingRemainderHandler;
import com.hepdd.toms_storage.crafting.CraftingStackMatcher;

import codechicken.nei.PositionedStack;

public final class RecipeNbtSerializer {

    public static final int GRID_SIZE = 9;
    private static final int DEFAULT_SIMULATION_CAP = 64;
    private static final int FALLBACK_SIMULATION_CAP = 8192;

    private RecipeNbtSerializer() {}

    public static NBTTagCompound writeRecipe(List<PositionedStack> ingredients, ItemStack output, int count) {
        NBTTagCompound tag = createBaseRecipe(output, count);
        NBTTagList slots = new NBTTagList();
        for (int i = 0; i < GRID_SIZE; i++) {
            NBTTagList candidates = writeCandidates(
                ingredients != null && i < ingredients.size() ? ingredients.get(i) : null);
            if (candidates.tagCount() == 0) continue;
            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag.setInteger("slot", i);
            slotTag.setTag("candidates", candidates);
            slots.appendTag(slotTag);
        }
        tag.setTag("ingredients", slots);
        return tag;
    }

    public static NBTTagCompound writeRecipe(ItemStack[][] ingredients, ItemStack output, int count) {
        NBTTagCompound tag = createBaseRecipe(output, count);
        NBTTagList slots = new NBTTagList();
        for (int i = 0; i < GRID_SIZE; i++) {
            NBTTagList candidates = writeCandidates(
                ingredients == null || i >= ingredients.length ? null : ingredients[i]);
            if (candidates.tagCount() == 0) continue;
            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag.setInteger("slot", i);
            slotTag.setTag("candidates", candidates);
            slots.appendTag(slotTag);
        }
        tag.setTag("ingredients", slots);
        return tag;
    }

    public static void setPatterns(NBTTagCompound recipeTag, List<NBTTagCompound> patternTags) {
        if (recipeTag == null) return;
        if (patternTags == null || patternTags.isEmpty()) {
            recipeTag.removeTag("patterns");
            return;
        }
        NBTTagList patterns = new NBTTagList();
        for (NBTTagCompound patternTag : patternTags) {
            if (patternTag != null) patterns.appendTag(copyRecipeWithoutPatterns(patternTag));
        }
        if (patterns.tagCount() > 0) {
            recipeTag.setTag("patterns", patterns);
        } else {
            recipeTag.removeTag("patterns");
        }
    }

    public static ItemStack readOutput(NBTTagCompound recipeTag) {
        if (recipeTag == null || !recipeTag.hasKey("output")) return null;
        return ItemStack.loadItemStackFromNBT(recipeTag.getCompoundTag("output"));
    }

    public static ItemStack[][] readIngredients(NBTTagCompound recipeTag) {
        ItemStack[][] ingredients = new ItemStack[GRID_SIZE][];
        for (int i = 0; i < GRID_SIZE; i++) ingredients[i] = new ItemStack[0];
        if (recipeTag == null) return ingredients;

        NBTTagList slots = recipeTag.getTagList("ingredients", 10);
        for (int i = 0; i < slots.tagCount(); i++) {
            NBTTagCompound slotTag = slots.getCompoundTagAt(i);
            int slot = slotTag.getInteger("slot");
            if (slot < 0 || slot >= GRID_SIZE) continue;

            NBTTagList candidateTags = slotTag.getTagList("candidates", 10);
            ItemStack[] candidates = new ItemStack[candidateTags.tagCount()];
            for (int j = 0; j < candidateTags.tagCount(); j++) {
                ItemStack loaded = ItemStack.loadItemStackFromNBT(candidateTags.getCompoundTagAt(j));
                if (loaded != null && loaded.stackSize <= 0) {
                    loaded.stackSize = 1;
                }
                candidates[j] = loaded;
            }
            ingredients[slot] = candidates;
        }
        return ingredients;
    }

    public static CraftingPattern readPattern(NBTTagCompound recipeTag) {
        return new CraftingPattern(readOutput(recipeTag), readIngredients(recipeTag));
    }

    public static List<CraftingPattern> readPatterns(NBTTagCompound recipeTag) {
        List<CraftingPattern> patterns = new ArrayList<>();
        if (recipeTag == null || !recipeTag.hasKey("patterns")) return patterns;

        NBTTagList patternTags = recipeTag.getTagList("patterns", 10);
        for (int i = 0; i < patternTags.tagCount(); i++) {
            CraftingPattern pattern = readPattern(patternTags.getCompoundTagAt(i));
            if (pattern.getOutput() != null) patterns.add(pattern);
        }
        return patterns;
    }

    public static List<NBTTagCompound> filterReachablePatterns(ItemStack[][] rootIngredients,
        List<NBTTagCompound> allPatternTags) {
        if (rootIngredients == null || allPatternTags == null || allPatternTags.isEmpty()) {
            return Collections.emptyList();
        }
        Map<ExactKey, NBTTagCompound> deduplicated = new LinkedHashMap<>();
        for (NBTTagCompound tag : allPatternTags) {
            if (tag == null) continue;
            ItemStack out = readOutput(tag);
            if (out != null && out.getItem() != null) {
                ExactKey outKey = ExactKey.ofItem(out);
                deduplicated.remove(outKey);
                deduplicated.put(outKey, copyRecipeWithoutPatterns(tag));
            }
        }
        if (deduplicated.isEmpty()) return Collections.emptyList();

        List<PatternNode> candidates = new ArrayList<>(deduplicated.size());
        for (Map.Entry<ExactKey, NBTTagCompound> entry : deduplicated.entrySet()) {
            NBTTagCompound tag = entry.getValue();
            candidates.add(new PatternNode(readOutput(tag), readIngredients(tag), tag));
        }

        List<NBTTagCompound> reachable = new ArrayList<>();
        boolean[] selected = new boolean[candidates.size()];
        Deque<ItemStack[][]> queue = new ArrayDeque<>();
        queue.add(rootIngredients);

        while (!queue.isEmpty()) {
            ItemStack[][] currentGrid = queue.poll();
            for (ItemStack[] slotCandidates : currentGrid) {
                if (slotCandidates == null) continue;
                for (ItemStack needed : slotCandidates) {
                    if (needed == null) continue;
                    for (int i = 0; i < candidates.size(); i++) {
                        if (selected[i]) continue;
                        PatternNode node = candidates.get(i);
                        if (CraftingStackMatcher.matchesIngredient(needed, node.output)) {
                            selected[i] = true;
                            reachable.add(node.tag);
                            queue.add(node.ingredients);
                        }
                    }
                }
            }
        }
        return reachable;
    }

    public static ItemStack[][] pruneIngredients(ItemStack[][] rawIngredients, CraftingInventorySnapshot localSnapshot,
        List<ItemStack> subOutputs, int maxLocalMatchesPerSlot) {
        return pruneIngredients(rawIngredients, localSnapshot, subOutputs, 1, false, maxLocalMatchesPerSlot);
    }

    public static ItemStack[][] pruneIngredients(ItemStack[][] rawIngredients, CraftingInventorySnapshot localSnapshot,
        List<ItemStack> subOutputs, int count, boolean fillMode, int maxLocalMatchesPerSlot) {
        ItemStack[][] pruned = new ItemStack[GRID_SIZE][];
        int cap = Math.max(0, maxLocalMatchesPerSlot);
        int simulationLimit = cap == Integer.MAX_VALUE ? DEFAULT_SIMULATION_CAP : FALLBACK_SIMULATION_CAP;
        List<Map<ExactKey, ItemStack>> requiredBySlot = simulateRequiredCandidates(
            rawIngredients,
            localSnapshot,
            subOutputs,
            Math.min(Math.max(1, count), simulationLimit),
            fillMode);
        for (int i = 0; i < GRID_SIZE; i++) {
            ItemStack[] raw = rawIngredients == null || i >= rawIngredients.length || rawIngredients[i] == null
                ? new ItemStack[0]
                : rawIngredients[i];
            pruned[i] = pruneSlotCandidates(raw, localSnapshot, subOutputs, requiredBySlot.get(i), cap);
        }
        return pruned;
    }

    public static int estimateCompressedSize(NBTTagCompound tag) {
        if (tag == null) return 0;
        try {
            byte[] compressed = CompressedStreamTools.compress(tag);
            return compressed == null ? 0 : compressed.length;
        } catch (IOException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static List<Map<ExactKey, ItemStack>> simulateRequiredCandidates(ItemStack[][] rawIngredients,
        CraftingInventorySnapshot localSnapshot, List<ItemStack> subOutputs, int crafts, boolean fillMode) {
        List<Map<ExactKey, ItemStack>> requiredBySlot = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            requiredBySlot.add(new LinkedHashMap<>());
        }
        if (localSnapshot == null || rawIngredients == null) return requiredBySlot;

        CraftingInventorySnapshot sim = localSnapshot.copy();
        if (fillMode) {
            for (int slot = 0; slot < GRID_SIZE; slot++) {
                ItemStack[] raw = slot < rawIngredients.length ? rawIngredients[slot] : null;
                if (raw == null || raw.length == 0) continue;
                Extraction extraction = sim.reserveLargest(raw);
                if (extraction != null) {
                    recordMatchedCandidate(requiredBySlot.get(slot), raw, extraction.getStack());
                }
            }
            return requiredBySlot;
        }

        boolean[] slotHasSubRecipe = new boolean[GRID_SIZE];
        if (subOutputs != null && !subOutputs.isEmpty()) {
            for (int slot = 0; slot < GRID_SIZE; slot++) {
                ItemStack[] raw = slot < rawIngredients.length ? rawIngredients[slot] : null;
                slotHasSubRecipe[slot] = canSatisfyFromSubOutputs(raw, subOutputs);
            }
        }

        ItemStack[] roundExtracted = new ItemStack[GRID_SIZE];
        for (int round = 0; round < crafts; round++) {
            boolean anyReserved = false;
            boolean roundComplete = true;
            Arrays.fill(roundExtracted, null);
            for (int slot = 0; slot < GRID_SIZE; slot++) {
                ItemStack[] raw = slot < rawIngredients.length ? rawIngredients[slot] : null;
                if (raw == null || raw.length == 0) continue;
                Extraction extraction = sim.reserve(raw);
                if (extraction == null) {
                    if (!slotHasSubRecipe[slot]) {
                        roundComplete = false;
                        break;
                    }
                    continue;
                }
                anyReserved = true;
                roundExtracted[slot] = extraction.getStack();
            }
            if (!roundComplete || !anyReserved) break;
            for (int slot = 0; slot < GRID_SIZE; slot++) {
                ItemStack extractedStack = roundExtracted[slot];
                if (extractedStack == null) continue;
                recordMatchedCandidate(requiredBySlot.get(slot), rawIngredients[slot], extractedStack);
                ItemStack remainder = CraftingRemainderHandler.getRemainder(extractedStack);
                if (remainder != null) {
                    sim.addTerminal(remainder);
                }
            }
        }
        return requiredBySlot;
    }

    private static boolean canSatisfyFromSubOutputs(ItemStack[] candidates, List<ItemStack> subOutputs) {
        if (candidates == null || subOutputs == null || subOutputs.isEmpty()) return false;
        for (ItemStack candidate : candidates) {
            if (candidate == null || candidate.getItem() == null) continue;
            for (int i = 0; i < subOutputs.size(); i++) {
                ItemStack subOut = subOutputs.get(i);
                if (subOut != null && CraftingStackMatcher.matchesIngredient(candidate, subOut)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void recordMatchedCandidate(Map<ExactKey, ItemStack> slotRequired, ItemStack[] raw,
        ItemStack extractedStack) {
        if (extractedStack == null || extractedStack.getItem() == null) return;
        for (ItemStack candidate : raw) {
            if (candidate == null || candidate.getItem() == null) continue;
            if (Math.max(1, candidate.stackSize) == extractedStack.stackSize
                && CraftingStackMatcher.matchesIngredient(candidate, extractedStack)) {
                ExactKey key = ExactKey.ofCandidate(candidate);
                if (!slotRequired.containsKey(key)) {
                    slotRequired.put(key, candidate);
                }
                return;
            }
        }
    }

    private static ItemStack[] pruneSlotCandidates(ItemStack[] raw, CraftingInventorySnapshot localSnapshot,
        List<ItemStack> subOutputs, Map<ExactKey, ItemStack> requiredForSlot, int maxLocalMatches) {
        if (raw == null || raw.length == 0) return new ItemStack[0];
        if (raw.length == 1) {
            return raw[0] == null || raw[0].getItem() == null ? new ItemStack[0]
                : new ItemStack[] { copyCandidate(raw[0]) };
        }

        Map<ExactKey, ScoredCandidate> localMatches = new LinkedHashMap<>();
        Map<ExactKey, ItemStack> subCraftMatches = new LinkedHashMap<>();
        ItemStack firstValid = null;

        for (ItemStack candidate : raw) {
            if (candidate == null || candidate.getItem() == null) continue;
            if (firstValid == null) firstValid = candidate;
            ExactKey key = ExactKey.ofCandidate(candidate);

            if (localSnapshot != null && !localMatches.containsKey(key)) {
                long available = localSnapshot.getAvailableAmount(candidate);
                if (available > 0L) {
                    long crafts = available / Math.max(1, candidate.stackSize);
                    localMatches.put(key, new ScoredCandidate(key, candidate, crafts, available));
                }
            }

            if (subOutputs != null && !subOutputs.isEmpty() && !subCraftMatches.containsKey(key)) {
                for (int i = 0; i < subOutputs.size(); i++) {
                    ItemStack subOut = subOutputs.get(i);
                    if (subOut != null && CraftingStackMatcher.matchesIngredient(candidate, subOut)) {
                        subCraftMatches.put(key, candidate);
                        break;
                    }
                }
            }
        }

        Map<ExactKey, ItemStack> chosen = new LinkedHashMap<>();
        if (requiredForSlot != null && !requiredForSlot.isEmpty()) {
            for (Map.Entry<ExactKey, ItemStack> entry : requiredForSlot.entrySet()) {
                chosen.put(entry.getKey(), copyCandidate(entry.getValue()));
            }
        }

        if (!localMatches.isEmpty() && chosen.size() < maxLocalMatches) {
            List<ScoredCandidate> sorted = new ArrayList<>(localMatches.values());
            sorted.sort(
                Comparator.comparingLong((ScoredCandidate c) -> c.crafts)
                    .thenComparingLong(c -> c.available)
                    .reversed());
            for (int i = 0; i < sorted.size() && chosen.size() < maxLocalMatches; i++) {
                ScoredCandidate sc = sorted.get(i);
                if (!chosen.containsKey(sc.key)) {
                    chosen.put(sc.key, copyCandidate(sc.stack));
                }
            }
        }

        for (Map.Entry<ExactKey, ItemStack> entry : subCraftMatches.entrySet()) {
            if (!chosen.containsKey(entry.getKey())) {
                chosen.put(entry.getKey(), copyCandidate(entry.getValue()));
            }
        }

        if (chosen.isEmpty() && firstValid != null) {
            return new ItemStack[] { copyCandidate(firstValid) };
        }
        return chosen.values()
            .toArray(new ItemStack[0]);
    }

    private static ItemStack copyCandidate(ItemStack stack) {
        ItemStack copy = stack.copy();
        if (copy.stackSize <= 0) {
            copy.stackSize = 1;
        }
        return copy;
    }

    private static NBTTagCompound copyRecipeWithoutPatterns(NBTTagCompound recipeTag) {
        NBTTagCompound copy = (NBTTagCompound) recipeTag.copy();
        copy.removeTag("patterns");
        return copy;
    }

    private static NBTTagCompound createBaseRecipe(ItemStack output, int count) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("count", Math.max(1, count));
        if (output != null) {
            NBTTagCompound outputTag = new NBTTagCompound();
            output.writeToNBT(outputTag);
            tag.setTag("output", outputTag);
        }
        return tag;
    }

    private static NBTTagList writeCandidates(PositionedStack ingredient) {
        if (ingredient == null || ingredient.items == null) return new NBTTagList();
        return writeCandidates(ingredient.items);
    }

    private static NBTTagList writeCandidates(ItemStack[] ingredients) {
        NBTTagList candidates = new NBTTagList();
        if (ingredients == null) return candidates;

        for (ItemStack stack : ingredients) {
            if (stack == null || stack.getItem() == null) continue;
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            if (stack.stackSize <= 0) {
                stackTag.setByte("Count", (byte) 1);
            }
            candidates.appendTag(stackTag);
        }
        return candidates;
    }

    private static final class PatternNode {

        private final ItemStack output;
        private final ItemStack[][] ingredients;
        private final NBTTagCompound tag;

        private PatternNode(ItemStack output, ItemStack[][] ingredients, NBTTagCompound tag) {
            this.output = output;
            this.ingredients = ingredients;
            this.tag = tag;
        }
    }

    private static final class ScoredCandidate {

        private final ExactKey key;
        private final ItemStack stack;
        private final long crafts;
        private final long available;

        private ScoredCandidate(ExactKey key, ItemStack stack, long crafts, long available) {
            this.key = key;
            this.stack = stack;
            this.crafts = crafts;
            this.available = available;
        }
    }

    private static final class ExactKey {

        private final Item item;
        private final int damage;
        private final int count;
        private final NBTTagCompound tag;
        private final int hash;

        private ExactKey(ItemStack stack, int count) {
            this.item = stack.getItem();
            this.damage = stack.getItemDamage();
            this.count = count;
            this.tag = stack.getTagCompound();
            int h = System.identityHashCode(this.item);
            h = 31 * h + this.damage;
            h = 31 * h + this.count;
            h = 31 * h + (this.tag == null ? 0 : this.tag.hashCode());
            this.hash = h;
        }

        private static ExactKey ofCandidate(ItemStack stack) {
            return new ExactKey(stack, Math.max(1, stack.stackSize));
        }

        private static ExactKey ofItem(ItemStack stack) {
            return new ExactKey(stack, 0);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ExactKey)) return false;
            ExactKey other = (ExactKey) obj;
            return damage == other.damage && count == other.count
                && item == other.item
                && Objects.equals(tag, other.tag);
        }
    }
}
