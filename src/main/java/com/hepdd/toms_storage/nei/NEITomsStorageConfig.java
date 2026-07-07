package com.hepdd.toms_storage.nei;

import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.Tags;
import com.hepdd.toms_storage.client.GuiCraftingTerminal;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.guihook.GuiContainerManager;

public class NEITomsStorageConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        API.registerGuiOverlay(GuiCraftingTerminal.class, "crafting", 26, 111);
        API.registerGuiOverlayHandler(GuiCraftingTerminal.class, new TerminalOverlayHandler(), "crafting");
        API.addRecipeCatalyst(new ItemStack(ModRegistry.craftingTerminal), "crafting");
        TerminalVirtualStackHandler virtualStackHandler = new TerminalVirtualStackHandler();
        GuiContainerManager.addObjectHandler(virtualStackHandler);
        GuiContainerManager.addTooltipHandler(virtualStackHandler);
    }

    @Override
    public String getName() {
        return "Tom's Simple Storage";
    }

    @Override
    public String getVersion() {
        return Tags.VERSION;
    }
}
