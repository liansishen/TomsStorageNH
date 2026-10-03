package com.hepdd.toms_storage.nei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot;
import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot.Extraction;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.DefaultOverlayHandler;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import codechicken.nei.recipe.IRecipeHandler;

public class TerminalOverlayHandler extends DefaultOverlayHandler {

    public TerminalOverlayHandler() {
        this(ContainerCraftingTerminal.GRID_X - 25, ContainerCraftingTerminal.GUI_HEIGHT - 161);
    }

    public TerminalOverlayHandler(int x, int y) {
        super(x, y);
    }

    @Override
    public boolean requireShiftForOverlayRecipe() {
        return false;
    }

    @Override
    public int transferRecipe(GuiContainer gui, IRecipeHandler handler, int recipeIndex, int multiplier) {
        return TerminalOverlayTransfer.transferRecipe(this, gui, handler, recipeIndex, multiplier);
    }

    @Override
    public boolean craft(GuiContainer gui, IRecipeHandler handler, int recipeIndex, int multiplier) {
        return TerminalOverlayTransfer.craft(this, gui, handler, recipeIndex, multiplier);
    }

    @Override
    public List<ItemOverlayState> presenceOverlay(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex) {
        if (!(firstGui.inventorySlots instanceof ContainerCraftingTerminal)) {
            return super.presenceOverlay(firstGui, recipe, recipeIndex);
        }
        return getTerminalPresence(firstGui, recipe, recipeIndex);
    }

    public static List<ItemOverlayState> getTerminalPresence(GuiContainer firstGui, IRecipeHandler recipe,
        int recipeIndex) {
        ContainerCraftingTerminal container = (ContainerCraftingTerminal) firstGui.inventorySlots;
        AutoCraftPreviewInventory preview = container.getAutoCraftPreview();
        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(
            container.getPreviewStacks(),
            preview.getPlayerStacks(firstGui.mc.thePlayer.inventory));
        if (!preview.isActive()) {
            // Filling returns the current grid to storage before reserving ingredients.
            for (int i = 0; i < container.craftMatrix.getSizeInventory(); i++) {
                snapshot.addTerminal(container.craftMatrix.getStackInSlot(i));
            }
        }
        List<PositionedStack> ingredients = new ArrayList<>(recipe.getIngredientStacks(recipeIndex));
        ingredients.sort(
            Comparator.comparingInt((PositionedStack stack) -> stack.rely)
                .thenComparingInt(stack -> stack.relx));
        List<ItemOverlayState> presence = new ArrayList<>();
        for (PositionedStack ingredient : ingredients) {
            Extraction extraction = preview.isActive() ? snapshot.reserve(ingredient.items)
                : snapshot.reserveLargest(ingredient.items);
            boolean present = extraction != null;
            if (present) {
                ItemStack stack = extraction.getStack();
                present = stack.stackSize
                    <= Math.min(container.craftMatrix.getInventoryStackLimit(), stack.getMaxStackSize());
            }
            presence.add(new ItemOverlayState(ingredient, present));
            NEIDebug.log("presenceOverlay ingredient=" + NEIDebug.stack(ingredient.item) + " present=" + present);
        }
        return presence;
    }

}
