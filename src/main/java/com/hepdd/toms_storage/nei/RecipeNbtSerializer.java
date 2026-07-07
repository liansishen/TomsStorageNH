package com.hepdd.toms_storage.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hepdd.toms_storage.crafting.CraftingPattern;

import codechicken.nei.PositionedStack;

public final class RecipeNbtSerializer {

    public static final int GRID_SIZE = 9;

    private RecipeNbtSerializer() {}

    public static NBTTagCompound writeRecipe(List<PositionedStack> ingredients, ItemStack output, int count) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("count", Math.max(1, count));
        if (output != null) {
            NBTTagCompound outputTag = new NBTTagCompound();
            output.writeToNBT(outputTag);
            tag.setTag("output", outputTag);
        }

        NBTTagList slots = new NBTTagList();
        for (int i = 0; i < GRID_SIZE; i++) {
            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag.setInteger("slot", i);
            slotTag.setTag("candidates", writeCandidates(i < ingredients.size() ? ingredients.get(i) : null));
            slots.appendTag(slotTag);
        }
        tag.setTag("ingredients", slots);
        NBTTagList patterns = new NBTTagList();
        patterns.appendTag(copyRecipeWithoutPatterns(tag));
        tag.setTag("patterns", patterns);
        return tag;
    }

    public static NBTTagCompound writeRecipe(ItemStack[][] ingredients, ItemStack output, int count) {
        NBTTagCompound tag = createBaseRecipe(output, count);
        NBTTagList slots = createEmptySlots();
        for (int i = 0; i < GRID_SIZE; i++) {
            slots.getCompoundTagAt(i)
                .setTag(
                    "candidates",
                    writeCandidates(ingredients == null || i >= ingredients.length ? null : ingredients[i]));
        }
        tag.setTag("ingredients", slots);
        NBTTagList patterns = new NBTTagList();
        patterns.appendTag(copyRecipeWithoutPatterns(tag));
        tag.setTag("patterns", patterns);
        return tag;
    }

    public static void setPatterns(NBTTagCompound recipeTag, List<NBTTagCompound> patternTags) {
        NBTTagList patterns = new NBTTagList();
        for (NBTTagCompound patternTag : patternTags) {
            if (patternTag != null) patterns.appendTag(copyRecipeWithoutPatterns(patternTag));
        }
        patterns.appendTag(copyRecipeWithoutPatterns(recipeTag));
        recipeTag.setTag("patterns", patterns);
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
                candidates[j] = ItemStack.loadItemStackFromNBT(candidateTags.getCompoundTagAt(j));
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
        if (recipeTag == null) return patterns;

        NBTTagList patternTags = recipeTag.getTagList("patterns", 10);
        for (int i = 0; i < patternTags.tagCount(); i++) {
            CraftingPattern pattern = readPattern(patternTags.getCompoundTagAt(i));
            if (pattern.getOutput() != null) patterns.add(pattern);
        }
        if (patterns.isEmpty() && readOutput(recipeTag) != null) patterns.add(readPattern(recipeTag));
        return patterns;
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

    private static NBTTagList createEmptySlots() {
        NBTTagList slots = new NBTTagList();
        for (int i = 0; i < GRID_SIZE; i++) {
            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag.setInteger("slot", i);
            slotTag.setTag("candidates", new NBTTagList());
            slots.appendTag(slotTag);
        }
        return slots;
    }

    private static NBTTagList writeCandidates(PositionedStack ingredient) {
        NBTTagList candidates = new NBTTagList();
        if (ingredient == null || ingredient.items == null) return candidates;

        for (ItemStack stack : ingredient.items) {
            if (stack == null) continue;
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            candidates.appendTag(stackTag);
        }
        return candidates;
    }

    private static NBTTagList writeCandidates(ItemStack[] ingredients) {
        NBTTagList candidates = new NBTTagList();
        if (ingredients == null) return candidates;

        for (ItemStack stack : ingredients) {
            if (stack == null) continue;
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            candidates.appendTag(stackTag);
        }
        return candidates;
    }
}
