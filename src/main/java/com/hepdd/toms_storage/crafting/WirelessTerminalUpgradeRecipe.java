package com.hepdd.toms_storage.crafting;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.item.ItemWirelessTerminal;

public class WirelessTerminalUpgradeRecipe extends ShapedOreRecipe {

    private final int inputLevel;

    public WirelessTerminalUpgradeRecipe(int inputLevel, String metal) {
        super(
            ItemWirelessTerminal.createLevelStack(inputLevel + 1),
            "RMR",
            "MTM",
            "RMR",
            'R',
            Items.redstone,
            'M',
            metal,
            'T',
            ItemWirelessTerminal.createLevelStack(inputLevel));
        this.inputLevel = inputLevel;
    }

    @Override
    public boolean matches(InventoryCrafting inventory, World world) {
        if (!super.matches(inventory, world)) return false;
        ItemStack terminal = inventory.getStackInRowAndColumn(1, 1);
        return terminal != null && terminal.getItem() == ModRegistry.wirelessTerminal
            && ItemWirelessTerminal.getLevel(terminal) == inputLevel;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inventory) {
        if (!matches(inventory, null)) return null;
        ItemStack result = inventory.getStackInRowAndColumn(1, 1)
            .copy();
        result.stackSize = 1;
        ItemWirelessTerminal.setLevel(result, inputLevel + 1);
        return result;
    }
}
