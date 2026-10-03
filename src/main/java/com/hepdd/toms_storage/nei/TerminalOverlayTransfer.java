package com.hepdd.toms_storage.nei;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.toms_storage.ModNetwork;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.network.PacketAutoCraftRequest;
import com.hepdd.toms_storage.network.PacketTerminalAction;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.DefaultOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;

public final class TerminalOverlayTransfer {

    private static int nextAutoCraftRequestId;

    private TerminalOverlayTransfer() {}

    public static int transferRecipe(DefaultOverlayHandler overlay, GuiContainer gui, IRecipeHandler handler,
        int recipeIndex, int multiplier) {
        if (!(gui.inventorySlots instanceof ContainerCraftingTerminal)) return 0;
        ItemStack[][] ingredients = mapRecipeIngredients(gui, handler.getIngredientStacks(recipeIndex));
        if (ingredients == null) return 0;
        int count = multiplier <= 0 ? 64 : Math.min(64, multiplier);
        ModNetwork.channel.sendToServer(
            PacketTerminalAction
                .fillRecipe(gui.inventorySlots.windowId, RecipeNbtSerializer.writeRecipe(ingredients, null, count)));
        return count;
    }

    public static boolean craft(DefaultOverlayHandler overlay, GuiContainer gui, IRecipeHandler handler,
        int recipeIndex, int multiplier) {
        if (!(gui.inventorySlots instanceof ContainerCraftingTerminal)) return false;

        ContainerCraftingTerminal container = (ContainerCraftingTerminal) gui.inventorySlots;
        ItemStack output = getRecipeOutput(handler, recipeIndex);
        if (output == null) {
            NEIDebug.log("craft abort: recipe output not found");
            return false;
        }

        int count = Math.max(1, multiplier <= 0 ? 1 : multiplier);
        int requestId = nextAutoCraftRequestId++;
        ItemStack[][] mappedIngredients = mapRecipeIngredients(gui, handler.getIngredientStacks(recipeIndex));
        if (mappedIngredients == null) {
            NEIDebug.log("craft abort: recipe serialization failed");
            return false;
        }
        NBTTagCompound recipeTag = RecipeNbtSerializer.writeRecipe(mappedIngredients, output, count);
        RecipeNbtSerializer.setPatterns(
            recipeTag,
            container.getAutoCraftPreview()
                .getPatterns());
        ItemStack[][] ingredients = RecipeNbtSerializer.readIngredients(recipeTag);
        if (!container.getAutoCraftPreview()
            .applyRequest(requestId, container.clientStacks, gui.mc.thePlayer.inventory, ingredients, output, count)) {
            NEIDebug.log("craft abort: preview simulation failed");
            return false;
        }
        container.getAutoCraftPreview()
            .rememberPattern(recipeTag);

        NEIDebug.log("craft request id=" + requestId + " output=" + NEIDebug.stack(output) + " count=" + count);
        ModNetwork.channel.sendToServer(new PacketAutoCraftRequest(requestId, recipeTag, true, true));
        return true;
    }

    private static ItemStack[][] mapRecipeIngredients(GuiContainer gui, List<PositionedStack> ingredients) {
        ContainerCraftingTerminal container = (ContainerCraftingTerminal) gui.inventorySlots;
        Slot origin = container.getSlot(ContainerCraftingTerminal.GRID_SLOT_START);
        int offsetX = origin.xDisplayPosition - 25;
        int offsetY = origin.yDisplayPosition - 6;
        ItemStack[][] mapped = new ItemStack[RecipeNbtSerializer.GRID_SIZE][];
        for (int i = 0; i < mapped.length; i++) mapped[i] = new ItemStack[0];

        for (PositionedStack ingredient : ingredients) {
            if (ingredient == null || ingredient.items == null || ingredient.items.length == 0) continue;
            Slot slot = findCraftingSlot(gui, ingredient.relx + offsetX, ingredient.rely + offsetY);
            if (slot == null || slot.getSlotIndex() < 0 || slot.getSlotIndex() >= RecipeNbtSerializer.GRID_SIZE) {
                NEIDebug.log(
                    "recipe serialization failed: no craft slot for ingredient=" + NEIDebug.stack(
                        ingredient.item) + " at=" + (ingredient.relx + offsetX) + "," + (ingredient.rely + offsetY));
                return null;
            }
            mapped[slot.getSlotIndex()] = ingredient.items;
        }
        return mapped;
    }

    private static Slot findCraftingSlot(GuiContainer gui, int x, int y) {
        for (Object object : gui.inventorySlots.inventorySlots) {
            Slot slot = (Slot) object;
            if (slot.inventory instanceof InventoryCrafting && slot.xDisplayPosition == x
                && slot.yDisplayPosition == y) {
                return slot;
            }
        }
        return null;
    }

    private static ItemStack getRecipeOutput(IRecipeHandler handler, int recipeIndex) {
        try {
            Method method = handler.getClass()
                .getMethod("getResultStack", int.class);
            Object result = method.invoke(handler, recipeIndex);
            if (result instanceof PositionedStack) {
                ItemStack stack = ((PositionedStack) result).item;
                return stack == null ? null : stack.copy();
            }
            if (result instanceof ItemStack) return ((ItemStack) result).copy();
        } catch (ReflectiveOperationException e) {
            NEIDebug.log(
                "craft output lookup failed handler=" + handler.getClass()
                    .getName() + " error=" + e);
        }
        return null;
    }

}
