package com.hepdd.toms_storage.nei;

import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.Config;
import com.hepdd.toms_storage.TomsStorageMod;

public final class NEIDebug {

    private NEIDebug() {}

    public static void log(String message) {
        if (Config.debugNeiAutocrafting) TomsStorageMod.LOG.info("[NEI autocraft] " + message);
    }

    public static String stack(ItemStack stack) {
        if (stack == null) return "null";
        return stack.stackSize + "x" + stack.getDisplayName() + "@" + stack.getItemDamage();
    }
}
