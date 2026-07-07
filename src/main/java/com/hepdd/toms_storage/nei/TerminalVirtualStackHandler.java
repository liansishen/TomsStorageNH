package com.hepdd.toms_storage.nei;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.client.GuiStorageTerminal;

import codechicken.nei.guihook.IContainerObjectHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;

public class TerminalVirtualStackHandler implements IContainerObjectHandler, IContainerTooltipHandler {

    @Override
    public void guiTick(GuiContainer gui) {}

    @Override
    public void refresh(GuiContainer gui) {}

    @Override
    public void load(GuiContainer gui) {}

    @Override
    public ItemStack getStackUnderMouse(GuiContainer gui, int mousex, int mousey) {
        StoredItemStack stack = getVirtualStack(gui, mousex, mousey);
        if (stack == null) return null;
        ItemStack result = stack.getStack()
            .copy();
        result.stackSize = (int) Math.min(stack.getQuantity(), Integer.MAX_VALUE);
        return result;
    }

    @Override
    public boolean objectUnderMouse(GuiContainer gui, int mousex, int mousey) {
        return getVirtualStack(gui, mousex, mousey) != null;
    }

    @Override
    public boolean shouldShowTooltip(GuiContainer gui) {
        return true;
    }

    private StoredItemStack getVirtualStack(GuiContainer gui, int mousex, int mousey) {
        if (!(gui instanceof GuiStorageTerminal)) return null;
        return ((GuiStorageTerminal) gui).getVirtualStackUnderMouse(mousex, mousey);
    }
}
