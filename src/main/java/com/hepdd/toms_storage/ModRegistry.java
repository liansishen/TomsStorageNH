package com.hepdd.toms_storage;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.hepdd.toms_storage.block.BlockCraftingTerminal;
import com.hepdd.toms_storage.block.BlockInventoryConnector;
import com.hepdd.toms_storage.block.BlockStorageTerminal;
import com.hepdd.toms_storage.block.BlockTrim;
import com.hepdd.toms_storage.item.ItemBlockWithTooltip;
import com.hepdd.toms_storage.item.ItemWirelessTerminal;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;
import com.hepdd.toms_storage.tile.TileEntityInventoryConnector;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

import cpw.mods.fml.common.registry.GameRegistry;

public final class ModRegistry {

    public static Block inventoryConnector;
    public static Block craftingTerminal;
    public static Block storageTerminal;
    public static Block trim;
    public static Item wirelessTerminal;
    public static int terminalRenderId;

    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(TomsStorageMod.MODID) {

        @Override
        public Item getTabIconItem() {
            return Item.getItemFromBlock(Blocks.chest);
        }
    };

    private ModRegistry() {}

    public static void preInit() {
        inventoryConnector = new BlockInventoryConnector();
        craftingTerminal = new BlockCraftingTerminal();
        storageTerminal = new BlockStorageTerminal();
        trim = new BlockTrim();
        wirelessTerminal = new ItemWirelessTerminal();

        GameRegistry.registerBlock(inventoryConnector, ItemBlockWithTooltip.class, "inventory_connector");
        GameRegistry.registerBlock(craftingTerminal, ItemBlockWithTooltip.class, "crafting_terminal");
        GameRegistry.registerBlock(storageTerminal, ItemBlockWithTooltip.class, "storage_terminal");
        GameRegistry.registerBlock(trim, ItemBlockWithTooltip.class, "trim");
        GameRegistry.registerItem(wirelessTerminal, "wireless_terminal");
        GameRegistry.registerTileEntity(TileEntityCraftingTerminal.class, TomsStorageMod.MODID + ":crafting_terminal");
        GameRegistry
            .registerTileEntity(TileEntityInventoryConnector.class, TomsStorageMod.MODID + ":inventory_connector");
        GameRegistry.registerTileEntity(TileEntityStorageTerminal.class, TomsStorageMod.MODID + ":storage_terminal");
    }

    public static void init() {
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(inventoryConnector),
                "LLL",
                "LcL",
                "LLL",
                'L',
                "logWood",
                'c',
                Blocks.chest));
        GameRegistry.addRecipe(
            new ShapedOreRecipe(
                new ItemStack(storageTerminal),
                "SSS",
                "ScS",
                "SSS",
                'S',
                "slabWood",
                'c',
                Blocks.chest));
        GameRegistry
            .addRecipe(new ShapelessOreRecipe(new ItemStack(craftingTerminal), storageTerminal, Blocks.crafting_table));
        GameRegistry.addRecipe(new ItemStack(trim, 4), " P ", "PcP", " P ", 'P', Blocks.planks, 'c', Blocks.chest);
        GameRegistry.addRecipe(
            new ItemStack(wirelessTerminal),
            " E ",
            " T ",
            " D ",
            'E',
            Items.ender_pearl,
            'T',
            storageTerminal,
            'D',
            Items.diamond);
    }

    public static void postInit() {}
}
