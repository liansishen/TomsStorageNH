package com.hepdd.toms_storage.crafting;

import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.nei.RecipeNbtSerializer;

public final class CraftingPattern {

    private final ItemStack output;
    private final ItemStack[][] ingredients;

    public CraftingPattern(ItemStack output, ItemStack[][] ingredients) {
        this.output = output == null ? null : output.copy();
        this.ingredients = copyIngredients(ingredients);
    }

    public ItemStack getOutput() {
        return output == null ? null : output.copy();
    }

    public int getOutputCount() {
        return output == null ? 0 : Math.max(1, output.stackSize);
    }

    public ItemStack[][] getIngredients() {
        return copyIngredients(ingredients);
    }

    private static ItemStack[][] copyIngredients(ItemStack[][] source) {
        ItemStack[][] copy = new ItemStack[RecipeNbtSerializer.GRID_SIZE][];
        for (int i = 0; i < copy.length; i++) {
            ItemStack[] candidates = source == null || i >= source.length || source[i] == null ? new ItemStack[0]
                : source[i];
            copy[i] = new ItemStack[candidates.length];
            for (int j = 0; j < candidates.length; j++) {
                copy[i][j] = candidates[j] == null ? null : candidates[j].copy();
            }
        }
        return copy;
    }
}
