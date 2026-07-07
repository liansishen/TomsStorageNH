package com.hepdd.toms_storage.crafting;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;

import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot.Extraction;
import com.hepdd.toms_storage.nei.RecipeNbtSerializer;

public final class CraftingPlanner {

    private final EntityPlayerMP player;
    private final CraftingInventorySnapshot snapshot;
    private final List<CraftingPattern> patterns;
    private final boolean allowSubCrafting;

    public CraftingPlanner(EntityPlayerMP player, CraftingInventorySnapshot snapshot, List<CraftingPattern> patterns,
        boolean allowSubCrafting) {
        this.player = player;
        this.snapshot = snapshot;
        this.patterns = patterns;
        this.allowSubCrafting = allowSubCrafting;
    }

    public CraftResult plan(CraftingPattern rootPattern, int count) {
        CraftingPlan plan = new CraftingPlan();
        for (int i = 0; i < Math.max(1, count); i++) {
            CraftResult result = planPattern(rootPattern, plan, new HashSet<String>(), true);
            if (!result.isSuccess()) return result;
        }
        return plan.isEmpty() ? CraftResult.failure("nothing_to_craft") : CraftResult.success(plan);
    }

    private CraftResult planPattern(CraftingPattern pattern, CraftingPlan plan, Set<String> resolving,
        boolean finalOutput) {
        ItemStack output = pattern.getOutput();
        if (output == null || output.stackSize <= 0) return CraftResult.failure("missing_output");

        String key = getKey(output);
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

            ItemStack result = validate(pattern, extracted);
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

    private CraftResult craftMissingCandidate(ItemStack[] candidates, CraftingPlan plan, Set<String> resolving) {
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
        return CraftResult.failure("missing_subrecipe");
    }

    private CraftingPattern findPattern(ItemStack target) {
        for (CraftingPattern pattern : patterns) {
            ItemStack output = pattern.getOutput();
            if (output != null && CraftingStackMatcher.matchesIngredient(target, output)) return pattern;
        }
        return null;
    }

    private ItemStack validate(CraftingPattern pattern, Extraction[] extracted) {
        InventoryCrafting craftMatrix = new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) {
                return false;
            }
        }, 3, 3);
        for (int i = 0; i < extracted.length; i++) {
            if (extracted[i] != null) craftMatrix.setInventorySlotContents(
                i,
                extracted[i].getStack()
                    .copy());
        }
        return CraftingManager.getInstance()
            .findMatchingRecipe(craftMatrix, player.worldObj);
    }

    private static boolean matchesExpectedResult(ItemStack result, ItemStack expectedOutput) {
        return CraftingStackMatcher.matchesOutput(expectedOutput, result);
    }

    private static String getKey(ItemStack stack) {
        return Item.getIdFromItem(stack.getItem()) + ":"
            + stack.getItemDamage()
            + ":"
            + (stack.hasTagCompound() ? stack.getTagCompound()
                .hashCode() : 0);
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
}
