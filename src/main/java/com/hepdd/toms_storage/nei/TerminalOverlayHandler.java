package com.hepdd.toms_storage.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.gui.ContainerStorageTerminal;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.DefaultOverlayHandler;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import codechicken.nei.recipe.IRecipeHandler;

public class TerminalOverlayHandler extends DefaultOverlayHandler {

    public TerminalOverlayHandler() {
        super(1, 105);
    }

    @Override
    public List<ItemOverlayState> presenceOverlay(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex) {
        List<ItemOverlayState> itemPresenceSlots = new ArrayList<>();
        List<ItemStack> invStacks = firstGui.inventorySlots.inventorySlots.stream()
            .filter(s -> s != null && s.getStack() != null && s.getStack().stackSize > 0)
            .map(
                s -> s.getStack()
                    .copy())
            .collect(Collectors.toList());
        if (firstGui.inventorySlots instanceof ContainerStorageTerminal) {
            ContainerStorageTerminal container = (ContainerStorageTerminal) firstGui.inventorySlots;
            if (container.getAutoCraftPreview()
                .isActive()) {
                invStacks.clear();
                invStacks.addAll(
                    container.getAutoCraftPreview()
                        .getPlayerStacks(firstGui.mc.thePlayer.inventory));
            }
            int added = 0;
            for (StoredItemStack stack : AutoCraftPreviewInventory.getStacks(container)) {
                ItemStack actual = stack.getActualStack();
                if (actual != null && actual.stackSize > 0) {
                    invStacks.add(actual);
                    added++;
                }
            }
            NEIDebug.log("presenceOverlay added terminal stacks=" + added + " recipeIndex=" + recipeIndex);
        }

        for (PositionedStack stack : recipe.getIngredientStacks(recipeIndex)) {
            Optional<ItemStack> used = invStacks.stream()
                .filter(is -> is.stackSize > 0 && stack.contains(is))
                .findAny();
            itemPresenceSlots.add(new ItemOverlayState(stack, used.isPresent()));
            NEIDebug.log("presenceOverlay ingredient=" + NEIDebug.stack(stack.item) + " present=" + used.isPresent());
            if (used.isPresent()) used.get().stackSize -= 1;
        }
        return itemPresenceSlots;
    }

}
