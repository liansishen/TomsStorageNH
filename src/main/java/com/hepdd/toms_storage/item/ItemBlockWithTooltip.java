package com.hepdd.toms_storage.item;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.Config;
import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.client.TooltipHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemBlockWithTooltip extends ItemBlock {

    private final Block block;

    public ItemBlockWithTooltip(Block block) {
        super(block);
        this.block = block;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        String key = getTooltipKey();
        if (key != null) TooltipHelper.addLines(tooltip, key);
        if (block == ModRegistry.inventoryConnector) {
            tooltip.add(
                net.minecraft.client.resources.I18n
                    .format("tooltip.tomsstorage.inventory_connector.range", Config.inventoryConnectorRange));
            tooltip
                .add(net.minecraft.client.resources.I18n.format("tooltip.tomsstorage.inventory_connector.range_cable"));
        }
    }

    private String getTooltipKey() {
        if (block == ModRegistry.inventoryConnector) return "tooltip.tomsstorage.inventory_connector";
        if (block == ModRegistry.craftingTerminal) return "tooltip.tomsstorage.crafting_terminal";
        if (block == ModRegistry.storageTerminal) return "tooltip.tomsstorage.storage_terminal";
        if (block == ModRegistry.trim) return "tooltip.tomsstorage.trim";
        if (block == ModRegistry.inventoryCable) return "tooltip.tomsstorage.inventory_cable";
        if (block == ModRegistry.inventoryHopper) return "tooltip.tomsstorage.inventory_hopper";
        return null;
    }
}
