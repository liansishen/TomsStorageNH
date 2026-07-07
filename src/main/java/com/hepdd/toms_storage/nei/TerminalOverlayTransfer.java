package com.hepdd.toms_storage.nei;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.toms_storage.ModNetwork;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.network.PacketAutoCraftRequest;
import com.hepdd.toms_storage.network.PacketTerminalAction;

import codechicken.lib.inventory.InventoryUtils;
import codechicken.nei.FastTransferManager;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.DefaultOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.StackInfo;

public final class TerminalOverlayTransfer {

    private static int nextAutoCraftRequestId;

    private TerminalOverlayTransfer() {}

    public static int transferRecipe(DefaultOverlayHandler overlay, GuiContainer gui, IRecipeHandler handler,
        int recipeIndex, int multiplier) {
        beginTransfer(gui);
        try {
            List<PositionedStack> ingredients = handler.getIngredientStacks(recipeIndex);
            NEIDebug.log(
                "transferRecipe start ingredients=" + ingredients
                    .size() + " multiplier=" + multiplier + " offset=" + overlay.offsetx + "," + overlay.offsety);
            List<DefaultOverlayHandler.DistributedIngred> ingredientStacks = getPermutationIngredients(ingredients);

            if (!clearIngredients(gui, handler, overlay.offsetx, overlay.offsety)) {
                NEIDebug.log("transferRecipe abort: clearIngredients failed");
                return 0;
            }

            List<Source> sources = getSources(overlay, gui);
            NEIDebug.log("transferRecipe sources=" + sources.size() + " ingredientStacks=" + ingredientStacks.size());
            findInventoryQuantities(sources, ingredientStacks);
            for (DefaultOverlayHandler.DistributedIngred stack : ingredientStacks) {
                NEIDebug.log(
                    "ingredient quantity stack=" + NEIDebug.stack(stack.stack)
                        + " invAmount="
                        + stack.invAmount
                        + " recipeAmount="
                        + stack.recipeAmount
                        + " container="
                        + stack.isContainerItem);
            }

            List<DefaultOverlayHandler.IngredientDistribution> assignedIngredients = assignIngredients(
                ingredients,
                ingredientStacks);
            if (assignedIngredients == null) {
                NEIDebug.log("transferRecipe abort: assignedIngredients null");
                return 0;
            }

            assignIngredientSlots(gui, ingredients, assignedIngredients, overlay.offsetx, overlay.offsety);
            for (DefaultOverlayHandler.IngredientDistribution distribution : assignedIngredients) {
                NEIDebug.log(
                    "assigned ingredient=" + NEIDebug.stack(distribution.permutation)
                        + " distributed="
                        + distribution.distrib.distributed
                        + " slots="
                        + distribution.slots.length);
            }
            multiplier = Math.min(multiplier == 0 ? 64 : multiplier, calculateRecipeQuantity(assignedIngredients));
            NEIDebug.log("transferRecipe calculated multiplier=" + multiplier);
            moveIngredients(gui, sources, assignedIngredients, Math.max(1, multiplier));

            boolean missing = assignedIngredients.stream()
                .anyMatch(distribution -> distribution.distrib.distributed == 0);
            NEIDebug.log("transferRecipe done missing=" + missing + " return=" + (missing ? 0 : multiplier));
            return missing ? 0 : multiplier;
        } finally {
            endTransfer(gui);
        }
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
        ItemStack[][] mappedIngredients = mapRecipeIngredients(
            gui,
            handler.getIngredientStacks(recipeIndex),
            overlay.offsetx,
            overlay.offsety);
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

    private static ItemStack[][] mapRecipeIngredients(GuiContainer gui, List<PositionedStack> ingredients, int offsetX,
        int offsetY) {
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

    private static List<Source> getSources(DefaultOverlayHandler overlay, GuiContainer gui) {
        List<Source> sources = new ArrayList<>();
        Slot resultSlot = getCraftingResultSlot(gui);
        for (Object object : gui.inventorySlots.inventorySlots) {
            Slot slot = (Slot) object;
            if (slot == resultSlot || !slot.getHasStack()
                || !overlay.canMoveFrom(slot, gui)
                || !slot.canTakeStack(gui.mc.thePlayer)) continue;
            sources.add(Source.slot(slot));
        }

        if (gui.inventorySlots instanceof ContainerCraftingTerminal) {
            ContainerCraftingTerminal container = (ContainerCraftingTerminal) gui.inventorySlots;
            List<StoredItemStack> terminalStacks = container.getNeiTransferStacks();
            NEIDebug.log("getSources clientStacks=" + terminalStacks.size());
            for (int i = 0; i < terminalStacks.size(); i++) {
                ItemStack stack = terminalStacks.get(i)
                    .getActualStack();
                if (stack != null && stack.stackSize > 0) {
                    sources.add(Source.terminal(i, stack));
                    NEIDebug.log("getSources terminal index=" + i + " stack=" + NEIDebug.stack(stack));
                }
            }
        }
        return sources;
    }

    private static boolean clearIngredients(GuiContainer gui, IRecipeHandler handler, int offsetX, int offsetY) {
        for (Slot slot : getCraftMatrixSlots(gui, handler, offsetX, offsetY)) {
            NEIDebug.log(
                "clearIngredients slot=" + slot.slotNumber
                    + " has="
                    + slot.getHasStack()
                    + " stack="
                    + NEIDebug.stack(slot.getStack()));
            if (!slot.getHasStack() || !slot.canTakeStack(gui.mc.thePlayer)) continue;
            FastTransferManager.clickSlot(gui, slot.slotNumber, 0, 0);
            if (gui.mc.thePlayer.inventory.getItemStack() != null) {
                NEIDebug.log(
                    "clearIngredients returning held=" + NEIDebug.stack(gui.mc.thePlayer.inventory.getItemStack()));
                FastTransferManager.clickSlot(gui, ContainerCraftingTerminal.getTerminalSourceSlotId(0), 0, 0);
            }
            if (slot.getHasStack()) {
                NEIDebug.log("clearIngredients failed slot still has " + NEIDebug.stack(slot.getStack()));
                return false;
            }
        }

        FastTransferManager.dropHeldItem(gui);
        boolean clear = gui.mc.thePlayer.inventory.getItemStack() == null;
        NEIDebug.log("clearIngredients final heldClear=" + clear);
        return clear;
    }

    private static HashSet<Slot> getCraftMatrixSlots(GuiContainer gui, IRecipeHandler handler, int offsetX,
        int offsetY) {
        PositionedStack firstIngredient = handler.getIngredientStacks(0)
            .get(0);
        HashMap<Integer, Slot> grid = new HashMap<>();
        HashSet<Slot> ingredientSlots = new HashSet<>();

        for (Object object : gui.inventorySlots.inventorySlots) {
            Slot slot = (Slot) object;
            int key = (slot.xDisplayPosition - offsetX) * 10000 + (slot.yDisplayPosition - offsetY);
            grid.put(key, slot);
        }

        int left = firstIngredient.relx;
        int top = firstIngredient.rely;
        int right = left;
        int bottom = top;

        while (grid.containsKey((left - 18) * 10000 + firstIngredient.rely)) left -= 18;
        while (grid.containsKey((right + 18) * 10000 + firstIngredient.rely)) right += 18;
        while (grid.containsKey(firstIngredient.relx * 10000 + (top - 18))) top -= 18;
        while (grid.containsKey(firstIngredient.relx * 10000 + (bottom + 18))) bottom += 18;

        for (int x = left; x <= right; x += 18) {
            for (int y = top; y <= bottom; y += 18) {
                Slot slot = grid.get(x * 10000 + y);
                if (slot != null && !(slot.inventory instanceof InventoryPlayer)) ingredientSlots.add(slot);
            }
        }
        NEIDebug.log("getCraftMatrixSlots count=" + ingredientSlots.size());
        return ingredientSlots;
    }

    private static Slot getCraftingResultSlot(GuiContainer gui) {
        for (Object object : gui.inventorySlots.inventorySlots) {
            if (object instanceof SlotCrafting) return (Slot) object;
        }
        return null;
    }

    private static void moveIngredients(GuiContainer gui, List<Source> sources,
        List<DefaultOverlayHandler.IngredientDistribution> assignedIngredients, int multiplier) {
        for (Source source : sources) {
            if (!source.hasStack()) continue;
            ItemStack stack = source.getStack();
            int slotTransferCap = stack.getMaxStackSize();
            boolean pickup = false;
            int remaining = source.getStackSize();

            for (DefaultOverlayHandler.IngredientDistribution distribution : assignedIngredients) {
                if (distribution.slots.length == 0 || remaining <= 0 || !canStack(distribution.permutation, stack))
                    continue;
                int transferCap = Math.min(slotTransferCap, multiplier * distribution.permutation.stackSize);
                int stackSize = remaining;

                for (Slot destination : distribution.slots) {
                    int amount = Math.min(
                        transferCap - (destination.getHasStack() ? destination.getStack().stackSize : 0),
                        stackSize);
                    if (amount <= 0) continue;

                    if (stackSize <= amount) {
                        if (!pickup) {
                            NEIDebug.log("moveIngredients pickup source=" + source.describe());
                            source.click(gui, 0);
                            pickup = true;
                        }
                        NEIDebug.log(
                            "moveIngredients place all into slot=" + destination.slotNumber
                                + " amount="
                                + stackSize
                                + " dest="
                                + NEIDebug.stack(destination.getStack()));
                        FastTransferManager.clickSlot(gui, destination.slotNumber);
                        stackSize = 0;
                        remaining = 0;
                        break;
                    }

                    for (int i = 0; i < amount; i++) {
                        if (!pickup) {
                            NEIDebug.log("moveIngredients pickup source=" + source.describe());
                            source.click(gui, 0);
                            pickup = true;
                        }
                        NEIDebug.log(
                            "moveIngredients place one into slot=" + destination.slotNumber
                                + " remainingBefore="
                                + stackSize);
                        FastTransferManager.clickSlot(gui, destination.slotNumber, 1);
                        stackSize--;
                        remaining--;
                    }
                }

                source.setStackSize(remaining);
            }

            if (pickup && gui.mc.thePlayer.inventory.getItemStack() != null) {
                NEIDebug.log(
                    "moveIngredients returning held=" + NEIDebug.stack(gui.mc.thePlayer.inventory.getItemStack())
                        + " to source="
                        + source.describe());
                source.click(gui, 0);
            }
        }
    }

    private static int calculateRecipeQuantity(List<DefaultOverlayHandler.IngredientDistribution> assignedIngredients) {
        int quantity = Integer.MAX_VALUE;

        for (DefaultOverlayHandler.IngredientDistribution distribution : assignedIngredients) {
            DefaultOverlayHandler.DistributedIngred stack = distribution.distrib;
            if (stack.distributed == 0) continue;
            if (stack.numSlots == 0) return 0;

            int maxStackSize = stack.stack.getMaxStackSize();
            if (maxStackSize == 1 && stack.isContainerItem) continue;

            int allSlots = Math.min(stack.invAmount, stack.numSlots * maxStackSize);
            quantity = Math.min(quantity, allSlots / stack.distributed);
        }

        return quantity == Integer.MAX_VALUE ? 1 : quantity;
    }

    private static Slot[][] assignIngredientSlots(GuiContainer gui, List<PositionedStack> ingredients,
        List<DefaultOverlayHandler.IngredientDistribution> assignedIngredients, int offsetX, int offsetY) {
        Slot[][] recipeSlots = mapIngredientSlots(gui, ingredients, offsetX, offsetY);

        HashMap<Slot, Integer> distribution = new HashMap<>();
        for (Slot[] recipeSlot : recipeSlots) {
            for (Slot slot : recipeSlot) {
                if (!distribution.containsKey(slot)) distribution.put(slot, -1);
            }
        }

        HashSet<Slot> availableSlots = new HashSet<>(distribution.keySet());
        HashSet<Integer> remainingIngredients = new HashSet<>();
        ArrayList<LinkedList<Slot>> assignedSlots = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            remainingIngredients.add(i);
            assignedSlots.add(new LinkedList<>());
        }

        while (!availableSlots.isEmpty() && !remainingIngredients.isEmpty()) {
            for (Iterator<Integer> iterator = remainingIngredients.iterator(); iterator.hasNext();) {
                int i = iterator.next();
                boolean assigned = false;
                DefaultOverlayHandler.DistributedIngred stack = assignedIngredients.get(i).distrib;

                for (Slot slot : recipeSlots[i]) {
                    if (!availableSlots.contains(slot)) continue;
                    availableSlots.remove(slot);
                    if (slot.getHasStack()) continue;

                    stack.numSlots++;
                    assignedSlots.get(i)
                        .add(slot);
                    assigned = true;
                    break;
                }

                if (!assigned || stack.numSlots * stack.stack.getMaxStackSize() >= stack.invAmount) iterator.remove();
            }
        }

        for (int i = 0; i < ingredients.size(); i++) {
            assignedIngredients.get(i).slots = assignedSlots.get(i)
                .toArray(
                    new Slot[assignedSlots.get(i)
                        .size()]);
        }
        return recipeSlots;
    }

    private static Slot[][] mapIngredientSlots(GuiContainer gui, List<PositionedStack> ingredients, int offsetX,
        int offsetY) {
        Slot[][] recipeSlots = new Slot[ingredients.size()][];
        for (int i = 0; i < ingredients.size(); i++) {
            LinkedList<Slot> slots = new LinkedList<>();
            PositionedStack stack = ingredients.get(i);
            for (Object object : gui.inventorySlots.inventorySlots) {
                Slot slot = (Slot) object;
                if (slot.xDisplayPosition == stack.relx + offsetX && slot.yDisplayPosition == stack.rely + offsetY) {
                    slots.add(slot);
                    break;
                }
            }
            recipeSlots[i] = slots.toArray(new Slot[slots.size()]);
        }
        return recipeSlots;
    }

    private static List<DefaultOverlayHandler.IngredientDistribution> assignIngredients(
        List<PositionedStack> ingredients, List<DefaultOverlayHandler.DistributedIngred> ingredientStacks) {
        ArrayList<DefaultOverlayHandler.IngredientDistribution> assignedIngredients = new ArrayList<>();
        for (PositionedStack positionedStack : ingredients) {
            DefaultOverlayHandler.DistributedIngred biggestIngredient = null;
            ItemStack permutation = null;
            int biggestSize = 0;
            for (ItemStack permutationStack : positionedStack.items) {
                for (DefaultOverlayHandler.DistributedIngred ingredientStack : ingredientStacks) {
                    if (!canStack(permutationStack, ingredientStack.stack)
                        || ingredientStack.invAmount - ingredientStack.distributed < permutationStack.stackSize
                        || ingredientStack.recipeAmount == 0
                        || permutationStack.stackSize == 0) continue;

                    int relativeSize = (ingredientStack.invAmount
                        - ingredientStack.invAmount / ingredientStack.recipeAmount * ingredientStack.distributed)
                        / permutationStack.stackSize;
                    if (relativeSize > biggestSize) {
                        biggestSize = relativeSize;
                        biggestIngredient = ingredientStack;
                        permutation = permutationStack;
                        break;
                    }
                }
            }

            if (biggestIngredient == null) {
                biggestIngredient = new DefaultOverlayHandler.DistributedIngred(positionedStack.item);
                permutation = InventoryUtils.copyStack(positionedStack.item, 0);
            }

            biggestIngredient.distributed += permutation.stackSize;
            assignedIngredients.add(new DefaultOverlayHandler.IngredientDistribution(biggestIngredient, permutation));
        }
        return assignedIngredients;
    }

    private static void findInventoryQuantities(List<Source> sources,
        List<DefaultOverlayHandler.DistributedIngred> ingredientStacks) {
        for (Source source : sources) {
            if (!source.hasStack()) continue;
            ItemStack stack = source.getStack();
            DefaultOverlayHandler.DistributedIngred ingredient = findIngredient(ingredientStacks, stack);
            if (ingredient == null) continue;

            ingredient.invAmount += stack.stackSize;
            if (!ingredient.isContainerItem && stack.getMaxStackSize() == 1
                && stack.getItem()
                    .hasContainerItem(stack)) {
                NBTTagCompound tag = stack.getTagCompound();
                if (tag != null && tag.hasKey("GT.ToolStats")) {
                    ingredient.isContainerItem = true;
                } else {
                    boolean paused = StackInfo.isPausedItemDamageSound();
                    StackInfo.pauseItemDamageSound(true);
                    ItemStack containerItem = stack.getItem()
                        .getContainerItem(stack);
                    if (containerItem != null) ingredient.isContainerItem = stack.getItem() == containerItem.getItem();
                    StackInfo.pauseItemDamageSound(paused);
                }
            }
        }
    }

    private static List<DefaultOverlayHandler.DistributedIngred> getPermutationIngredients(
        List<PositionedStack> ingredients) {
        ArrayList<DefaultOverlayHandler.DistributedIngred> ingredientStacks = new ArrayList<>();
        for (PositionedStack positionedStack : ingredients) {
            for (ItemStack stack : positionedStack.items) {
                DefaultOverlayHandler.DistributedIngred ingredient = findIngredient(ingredientStacks, stack);
                if (ingredient == null) {
                    ingredient = new DefaultOverlayHandler.DistributedIngred(stack);
                    ingredientStacks.add(ingredient);
                }
                ingredient.recipeAmount += stack.stackSize;
            }
        }
        return ingredientStacks;
    }

    private static DefaultOverlayHandler.DistributedIngred findIngredient(
        List<DefaultOverlayHandler.DistributedIngred> ingredientStacks, ItemStack stack) {
        for (DefaultOverlayHandler.DistributedIngred ingredient : ingredientStacks) {
            if (canStack(ingredient.stack, stack)) return ingredient;
        }
        return null;
    }

    private static boolean canStack(ItemStack first, ItemStack second) {
        if (first == null || second == null) return true;
        return NEIClientUtils.areStacksSameTypeCraftingWithNBT(first, second);
    }

    private static void beginTransfer(GuiContainer gui) {
        if (!(gui.inventorySlots instanceof ContainerCraftingTerminal)) return;
        ((ContainerCraftingTerminal) gui.inventorySlots).beginNeiTransfer();
        ModNetwork.channel.sendToServer(PacketTerminalAction.neiTransfer(true));
    }

    private static void endTransfer(GuiContainer gui) {
        if (!(gui.inventorySlots instanceof ContainerCraftingTerminal)) return;
        ((ContainerCraftingTerminal) gui.inventorySlots).endNeiTransfer(gui.mc.thePlayer);
        ModNetwork.channel.sendToServer(PacketTerminalAction.neiTransfer(false));
    }

    private static class Source {

        private final Slot slot;
        private final int terminalIndex;
        private final ItemStack terminalStack;

        private Source(Slot slot, int terminalIndex, ItemStack terminalStack) {
            this.slot = slot;
            this.terminalIndex = terminalIndex;
            this.terminalStack = terminalStack;
        }

        private static Source slot(Slot slot) {
            return new Source(slot, -1, null);
        }

        private static Source terminal(int index, ItemStack stack) {
            return new Source(null, index, stack.copy());
        }

        private boolean hasStack() {
            return getStack() != null && getStack().stackSize > 0;
        }

        private ItemStack getStack() {
            return slot == null ? terminalStack : slot.getStack();
        }

        private int getStackSize() {
            ItemStack stack = getStack();
            return stack == null ? 0 : stack.stackSize;
        }

        private void setStackSize(int size) {
            if (slot == null && terminalStack != null) terminalStack.stackSize = size;
        }

        private void click(GuiContainer gui, int button) {
            int slotNumber = slot == null ? ContainerCraftingTerminal.getTerminalSourceSlotId(terminalIndex)
                : slot.slotNumber;
            NEIDebug.log("source click slotNumber=" + slotNumber + " button=" + button + " source=" + describe());
            FastTransferManager.clickSlot(gui, slotNumber, button, 0);
        }

        private String describe() {
            return slot == null ? "terminal[" + terminalIndex + "] " + NEIDebug.stack(terminalStack)
                : "slot[" + slot.slotNumber + "] " + NEIDebug.stack(slot.getStack());
        }
    }
}
