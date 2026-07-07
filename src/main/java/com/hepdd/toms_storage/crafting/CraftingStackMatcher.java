package com.hepdd.toms_storage.crafting;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.hepdd.toms_storage.StorageItemUtils;

public final class CraftingStackMatcher {

    private CraftingStackMatcher() {}

    public static boolean matchesIngredient(ItemStack recipeItem, ItemStack candidate) {
        if (recipeItem == null || candidate == null || recipeItem.getItem() == null || candidate.getItem() == null) {
            return false;
        }

        if (OreDictionary.itemMatches(recipeItem, candidate, false)) return nbtMatches(recipeItem, candidate);

        if (recipeItem.getItem() == candidate.getItem()) {
            if (recipeItem.getItemDamage() == OreDictionary.WILDCARD_VALUE
                || recipeItem.getItemDamage() == candidate.getItemDamage()
                || recipeItem.isItemStackDamageable()) {
                return nbtMatches(recipeItem, candidate);
            }
        }

        return StorageItemUtils.areItemStacksEqual(recipeItem, candidate, true);
    }

    public static boolean matchesOutput(ItemStack expected, ItemStack actual) {
        if (expected == null || actual == null || expected.getItem() == null || actual.getItem() == null) return false;
        if (actual.stackSize < Math.max(1, expected.stackSize)) return false;
        if (expected.getItem() != actual.getItem()) return false;
        if (expected.getItemDamage() != OreDictionary.WILDCARD_VALUE
            && expected.getItemDamage() != actual.getItemDamage()) {
            return false;
        }
        return nbtMatches(expected, actual);
    }

    private static boolean nbtMatches(ItemStack recipeItem, ItemStack candidate) {
        return !recipeItem.hasTagCompound() || ItemStack.areItemStackTagsEqual(recipeItem, candidate);
    }
}
