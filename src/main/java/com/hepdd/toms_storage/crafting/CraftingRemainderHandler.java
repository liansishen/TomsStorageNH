package com.hepdd.toms_storage.crafting;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

public final class CraftingRemainderHandler {

    private CraftingRemainderHandler() {}

    public static void handleRemainder(TileEntityStorageTerminal terminal, ItemStack ingredient) {
        if (ingredient == null || terminal == null) return;

        ItemStack remainder = getRemainder(ingredient);
        if (remainder != null) terminal.pushOrDrop(remainder);
    }

    public static ItemStack getRemainder(ItemStack ingredient) {
        if (ingredient == null) return null;

        if (isGTTool(ingredient)) {
            ItemStack tool = ingredient.copy();
            incrementGTDamage(tool, 1);
            return getGTDurability(tool) > 0 ? tool : null;
        }

        if (!ingredient.getItem()
            .hasContainerItem(ingredient)) return null;
        ItemStack container = ingredient.getItem()
            .getContainerItem(ingredient);
        if (container == null) return null;

        if (container.getItem() == ingredient.getItem()) {
            if (!container.isItemStackDamageable() || container.getItemDamage() <= container.getMaxDamage()) {
                return container.copy();
            }
            return null;
        }

        return container.copy();
    }

    public static boolean isGTTool(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return false;
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.hasKey("GT.ToolStats");
    }

    public static long getGTDurability(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("GT.ToolStats")) return 0;
        NBTTagCompound stats = tag.getCompoundTag("GT.ToolStats");
        return stats.getLong("MaxDamage") - stats.getLong("Damage");
    }

    public static void incrementGTDamage(ItemStack stack, int amount) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("GT.ToolStats")) return;
        NBTTagCompound stats = tag.getCompoundTag("GT.ToolStats");
        stats.setLong("Damage", stats.getLong("Damage") + amount);
    }
}
