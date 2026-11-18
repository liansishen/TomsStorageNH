package com.hepdd.toms_storage.crafting;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot.Extraction;
import com.hepdd.toms_storage.nei.RecipeNbtSerializer;

public final class CraftingPlanner {

    private static final Container DUMMY_CONTAINER = new Container() {

        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
            return false;
        }
    };

    private final EntityPlayerMP player;
    private final CraftingInventorySnapshot snapshot;
    private final List<CraftingPattern> patterns;
    private final boolean allowSubCrafting;
    private final List<IRecipe> customRecipes;
    private final InventoryCrafting validationMatrix = new InventoryCrafting(DUMMY_CONTAINER, 3, 3);
    private final Map<GridSignature, ItemStack> validatedOutputs = new HashMap<>();

    public CraftingPlanner(EntityPlayerMP player, CraftingInventorySnapshot snapshot, List<CraftingPattern> patterns,
        boolean allowSubCrafting) {
        this(player, snapshot, patterns, allowSubCrafting, null);
    }

    CraftingPlanner(EntityPlayerMP player, CraftingInventorySnapshot snapshot, List<CraftingPattern> patterns,
        boolean allowSubCrafting, List<IRecipe> customRecipes) {
        this.player = player;
        this.snapshot = snapshot;
        this.patterns = patterns;
        this.allowSubCrafting = allowSubCrafting;
        this.customRecipes = customRecipes;
    }

    public CraftResult plan(CraftingPattern rootPattern, int count) {
        if (rootPattern == null) return CraftResult.failure("missing_output");
        CraftingPlan plan = new CraftingPlan();
        Set<OutputKey> resolving = new HashSet<>();
        for (int i = 0; i < Math.max(1, count); i++) {
            CraftResult result = planPattern(rootPattern, plan, resolving, true);
            if (!result.isSuccess()) return result;
        }
        return plan.isEmpty() ? CraftResult.failure("nothing_to_craft") : CraftResult.success(plan);
    }

    private CraftResult planPattern(CraftingPattern pattern, CraftingPlan plan, Set<OutputKey> resolving,
        boolean finalOutput) {
        ItemStack output = pattern.getOutput();
        if (output == null || output.getItem() == null || output.stackSize <= 0) {
            return CraftResult.failure("missing_output");
        }

        OutputKey key = new OutputKey(output);
        if (!resolving.add(key)) return CraftResult.failure("recipe_cycle");
        try {
            ItemStack[][] ingredients = pattern.getIngredients();
            Extraction[] extracted = new Extraction[RecipeNbtSerializer.GRID_SIZE];
            for (int slot = 0; slot < ingredients.length; slot++) {
                ItemStack[] candidates = ingredients[slot];
                if (candidates == null || candidates.length == 0) continue;

                Extraction extraction = snapshot.reserve(candidates);
                if (extraction == null && allowSubCrafting) {
                    CraftResult subCraft = craftMissingCandidate(candidates, plan, resolving);
                    if (!subCraft.isSuccess()) return subCraft;
                    extraction = snapshot.reserve(candidates);
                }
                if (extraction == null) return CraftResult.failure("missing_ingredients");
                extracted[slot] = extraction;
            }

            ItemStack result = validate(extracted);
            if (!matchesExpectedResult(result, output)) return CraftResult.failure("recipe_mismatch");

            for (Extraction extraction : extracted) {
                ItemStack remainder = CraftingRemainderHandler
                    .getRemainder(extraction == null ? null : extraction.getStack());
                if (remainder != null) snapshot.addTerminal(remainder);
            }
            if (!finalOutput) snapshot.addTerminal(result.copy());
            plan.addOperation(extracted, result, finalOutput);
            return CraftResult.success(plan);
        } finally {
            resolving.remove(key);
        }
    }

    private CraftResult craftMissingCandidate(ItemStack[] candidates, CraftingPlan plan, Set<OutputKey> resolving) {
        if (patterns != null) {
            for (ItemStack candidate : candidates) {
                if (candidate == null) continue;
                CraftingPattern pattern = findPattern(candidate);
                if (pattern == null) continue;
                int crafts = (int) Math.ceil((double) Math.max(1, candidate.stackSize) / pattern.getOutputCount());
                for (int i = 0; i < crafts; i++) {
                    CraftResult result = planPattern(pattern, plan, resolving, false);
                    if (!result.isSuccess()) return result;
                }
                return CraftResult.success(plan);
            }
        }
        return CraftResult.failure("missing_subrecipe");
    }

    private CraftingPattern findPattern(ItemStack target) {
        for (CraftingPattern pattern : patterns) {
            ItemStack output = pattern.getOutput();
            if (output != null && CraftingStackMatcher.matchesIngredient(target, output)) return pattern;
        }
        return null;
    }

    private ItemStack validate(Extraction[] extracted) {
        GridSignature signature = new GridSignature(extracted);
        if (validatedOutputs.containsKey(signature)) {
            ItemStack cached = validatedOutputs.get(signature);
            return cached == null ? null : cached.copy();
        }

        for (int i = 0; i < RecipeNbtSerializer.GRID_SIZE; i++) {
            ItemStack slotStack = i < extracted.length && extracted[i] != null && extracted[i].getStack() != null
                ? extracted[i].getStack()
                    .copy()
                : null;
            validationMatrix.setInventorySlotContents(i, slotStack);
        }

        World world = player == null ? null : player.worldObj;
        ItemStack matchedOut = findMatchingOutput(world);
        validatedOutputs.put(signature, matchedOut == null ? null : matchedOut.copy());
        return matchedOut;
    }

    private ItemStack findMatchingOutput(World world) {
        if (customRecipes == null) {
            return CraftingManager.getInstance()
                .findMatchingRecipe(validationMatrix, world);
        }
        for (int i = 0; i < customRecipes.size(); i++) {
            IRecipe recipe = customRecipes.get(i);
            if (recipe != null && recipe.matches(validationMatrix, world)) {
                return recipe.getCraftingResult(validationMatrix);
            }
        }
        return null;
    }

    private static boolean matchesExpectedResult(ItemStack result, ItemStack expectedOutput) {
        return CraftingStackMatcher.matchesOutput(expectedOutput, result);
    }

    public static final class CraftResult {

        private final boolean success;
        private final String reason;
        private final CraftingPlan plan;

        private CraftResult(boolean success, String reason, CraftingPlan plan) {
            this.success = success;
            this.reason = reason;
            this.plan = plan;
        }

        public static CraftResult success(CraftingPlan plan) {
            return new CraftResult(true, "", plan);
        }

        public static CraftResult failure(String reason) {
            return new CraftResult(false, reason, new CraftingPlan());
        }

        public boolean isSuccess() {
            return success;
        }

        public String getReason() {
            return reason;
        }

        public CraftingPlan getPlan() {
            return plan;
        }
    }

    private static final class OutputKey {

        private final Item item;
        private final int damage;
        private final NBTTagCompound tag;
        private final int hash;

        private OutputKey(ItemStack stack) {
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
            if (!(obj instanceof OutputKey)) return false;
            OutputKey other = (OutputKey) obj;
            return damage == other.damage && item == other.item && Objects.equals(tag, other.tag);
        }
    }

    private static final class GridSignature {

        private final Item[] items = new Item[RecipeNbtSerializer.GRID_SIZE];
        private final int[] damages = new int[RecipeNbtSerializer.GRID_SIZE];
        private final int[] counts = new int[RecipeNbtSerializer.GRID_SIZE];
        private final NBTTagCompound[] tags = new NBTTagCompound[RecipeNbtSerializer.GRID_SIZE];
        private final int hash;

        private GridSignature(Extraction[] extracted) {
            int h = 1;
            for (int i = 0; i < RecipeNbtSerializer.GRID_SIZE; i++) {
                ItemStack stack = i < extracted.length && extracted[i] != null ? extracted[i].getStack() : null;
                if (stack != null && stack.getItem() != null) {
                    items[i] = stack.getItem();
                    damages[i] = stack.getItemDamage();
                    counts[i] = stack.stackSize;
                    tags[i] = stack.getTagCompound();
                    h = 31 * h + System.identityHashCode(items[i]);
                    h = 31 * h + damages[i];
                    h = 31 * h + counts[i];
                    h = 31 * h + (tags[i] == null ? 0 : tags[i].hashCode());
                } else {
                    h = 31 * h;
                }
            }
            this.hash = h;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof GridSignature)) return false;
            GridSignature other = (GridSignature) obj;
            if (!Arrays.equals(damages, other.damages) || !Arrays.equals(counts, other.counts)) return false;
            for (int i = 0; i < RecipeNbtSerializer.GRID_SIZE; i++) {
                if (items[i] != other.items[i] || !Objects.equals(tags[i], other.tags[i])) {
                    return false;
                }
            }
            return true;
        }
    }
}
