package com.hepdd.toms_storage.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.toms_storage.ModNetwork;
import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.network.PacketAutoCraftRequest;
import com.hepdd.toms_storage.network.PacketTerminalAction;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.DefaultOverlayHandler;
import codechicken.nei.recipe.IRecipeHandler;

public final class TerminalOverlayTransfer {

    private static final int SAFE_PACKET_BYTES = 30_000;
    private static final int HARD_MAX_PACKET_BYTES = 32_000;
    private static final int FALLBACK_MAX_CANDIDATES = 2;
    private static final int MAX_ESTIMATED_SUB_CRAFTS = 4096;
    private static final AtomicInteger NEXT_AUTOCRAFT_REQUEST_ID = new AtomicInteger(1);

    private TerminalOverlayTransfer() {}

    public static int transferRecipe(DefaultOverlayHandler overlay, GuiContainer gui, IRecipeHandler handler,
        int recipeIndex, int multiplier) {
        if (!(gui.inventorySlots instanceof ContainerCraftingTerminal)) return 0;
        ContainerCraftingTerminal container = (ContainerCraftingTerminal) gui.inventorySlots;
        ItemStack[][] rawIngredients = mapRecipeIngredients(gui, handler.getIngredientStacks(recipeIndex));
        if (rawIngredients == null) return 0;

        int count = multiplier <= 0 ? 64 : Math.min(64, multiplier);
        CraftingInventorySnapshot snapshot = createLocalSnapshot(container, gui.mc.thePlayer.inventory, true);
        ItemStack[][] pruned = RecipeNbtSerializer
            .pruneIngredients(rawIngredients, snapshot, null, count, true, Integer.MAX_VALUE);
        NBTTagCompound recipeTag = RecipeNbtSerializer.writeRecipe(pruned, null, count);

        int compressedBytes = estimateActionPacketBytes(container.windowId, recipeTag);
        if (compressedBytes > SAFE_PACKET_BYTES) {
            pruned = RecipeNbtSerializer
                .pruneIngredients(rawIngredients, snapshot, null, count, true, FALLBACK_MAX_CANDIDATES);
            recipeTag = RecipeNbtSerializer.writeRecipe(pruned, null, count);
            compressedBytes = estimateActionPacketBytes(container.windowId, recipeTag);
        }
        if (compressedBytes > SAFE_PACKET_BYTES) {
            pruned = RecipeNbtSerializer.pruneIngredients(rawIngredients, snapshot, null, count, true, 0);
            recipeTag = RecipeNbtSerializer.writeRecipe(pruned, null, count);
            compressedBytes = estimateActionPacketBytes(container.windowId, recipeTag);
        }
        if (compressedBytes >= HARD_MAX_PACKET_BYTES) {
            NEIDebug.log("transferRecipe abort: compressed recipe exceeds 32KB limit bytes=" + compressedBytes);
            return 0;
        }

        ModNetwork.channel.sendToServer(PacketTerminalAction.fillRecipe(container.windowId, recipeTag));
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
        int requestId = NEXT_AUTOCRAFT_REQUEST_ID.getAndIncrement();
        ItemStack[][] mappedIngredients = mapRecipeIngredients(gui, handler.getIngredientStacks(recipeIndex));
        if (mappedIngredients == null) {
            NEIDebug.log("craft abort: recipe serialization failed");
            return false;
        }

        AutoCraftPreviewInventory preview = container.getAutoCraftPreview();
        CraftingInventorySnapshot preCraftSnapshot = createLocalSnapshot(container, gui.mc.thePlayer.inventory, false);
        List<NBTTagCompound> rawReachablePatterns = RecipeNbtSerializer
            .filterReachablePatterns(mappedIngredients, preview.getPatterns());
        List<ItemStack> subOutputs = collectPatternOutputs(rawReachablePatterns);
        int estimatedSubCrafts = estimateSubCraftCount(count);

        ItemStack[][] prunedIngredients = RecipeNbtSerializer
            .pruneIngredients(mappedIngredients, preCraftSnapshot, subOutputs, count, false, Integer.MAX_VALUE);
        List<NBTTagCompound> prunedPatterns = prunePatternCandidates(
            rawReachablePatterns,
            preCraftSnapshot,
            subOutputs,
            estimatedSubCrafts,
            Integer.MAX_VALUE);
        NBTTagCompound recipeTag = buildCraftTag(prunedIngredients, output, count, prunedPatterns);

        int compressedBytes = estimateAutoCraftPacketBytes(requestId, container.windowId, recipeTag);
        if (compressedBytes > SAFE_PACKET_BYTES) {
            prunedIngredients = RecipeNbtSerializer.pruneIngredients(
                mappedIngredients,
                preCraftSnapshot,
                subOutputs,
                count,
                false,
                FALLBACK_MAX_CANDIDATES);
            prunedPatterns = prunePatternCandidates(
                rawReachablePatterns,
                preCraftSnapshot,
                subOutputs,
                estimatedSubCrafts,
                FALLBACK_MAX_CANDIDATES);
            recipeTag = buildCraftTag(prunedIngredients, output, count, prunedPatterns);
            compressedBytes = estimateAutoCraftPacketBytes(requestId, container.windowId, recipeTag);
        }
        if (compressedBytes > SAFE_PACKET_BYTES) {
            prunedIngredients = RecipeNbtSerializer
                .pruneIngredients(mappedIngredients, preCraftSnapshot, subOutputs, count, false, 0);
            prunedPatterns = prunePatternCandidates(
                rawReachablePatterns,
                preCraftSnapshot,
                subOutputs,
                estimatedSubCrafts,
                0);
            recipeTag = buildCraftTag(prunedIngredients, output, count, prunedPatterns);
            compressedBytes = estimateAutoCraftPacketBytes(requestId, container.windowId, recipeTag);
        }
        if (compressedBytes >= HARD_MAX_PACKET_BYTES) {
            NEIDebug.log("craft abort: compressed packet exceeds 32KB limit bytes=" + compressedBytes);
            return false;
        }

        if (!preview.applyRequest(
            requestId,
            container.clientStacks,
            gui.mc.thePlayer.inventory,
            prunedIngredients,
            output,
            count)) {
            NEIDebug.log("craft abort: preview simulation failed");
            return false;
        }
        preview.rememberPattern(RecipeNbtSerializer.writeRecipe(mappedIngredients, output, count));

        NEIDebug.log("craft request id=" + requestId + " output=" + NEIDebug.stack(output) + " count=" + count);
        ModNetwork.channel
            .sendToServer(new PacketAutoCraftRequest(requestId, container.windowId, recipeTag, true, true));
        return true;
    }

    private static CraftingInventorySnapshot createLocalSnapshot(ContainerCraftingTerminal container,
        InventoryPlayer playerInventory, boolean includeGrid) {
        AutoCraftPreviewInventory preview = container.getAutoCraftPreview();
        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(
            preview.getStacksView(container.clientStacks),
            preview.getPlayerStacksView(playerInventory),
            true);
        if (includeGrid && !preview.isActive()) {
            for (int i = 0; i < container.craftMatrix.getSizeInventory(); i++) {
                snapshot.addTerminal(container.craftMatrix.getStackInSlot(i));
            }
        }
        return snapshot;
    }

    private static List<ItemStack> collectPatternOutputs(List<NBTTagCompound> patternTags) {
        List<ItemStack> outputs = new ArrayList<>(patternTags.size());
        for (NBTTagCompound tag : patternTags) {
            ItemStack out = RecipeNbtSerializer.readOutput(tag);
            if (out != null) outputs.add(out);
        }
        return outputs;
    }

    private static int estimateSubCraftCount(int rootCount) {
        long scaled = (long) Math.max(1, rootCount) * RecipeNbtSerializer.GRID_SIZE;
        return (int) Math.min((long) MAX_ESTIMATED_SUB_CRAFTS, scaled);
    }

    private static List<NBTTagCompound> prunePatternCandidates(List<NBTTagCompound> patternTags,
        CraftingInventorySnapshot snapshot, List<ItemStack> subOutputs, int subCraftCount, int maxLocalMatches) {
        if (patternTags == null || patternTags.isEmpty()) return new ArrayList<>(0);
        List<NBTTagCompound> shrunk = new ArrayList<>(patternTags.size());
        for (NBTTagCompound tag : patternTags) {
            ItemStack out = RecipeNbtSerializer.readOutput(tag);
            int patCount = Math.max(1, tag.getInteger("count"));
            ItemStack[][] ing = RecipeNbtSerializer.readIngredients(tag);
            int effectiveCount = Math.max(patCount, subCraftCount);
            ItemStack[][] pruned = RecipeNbtSerializer
                .pruneIngredients(ing, snapshot, subOutputs, effectiveCount, false, maxLocalMatches);
            shrunk.add(RecipeNbtSerializer.writeRecipe(pruned, out, patCount));
        }
        return shrunk;
    }

    private static NBTTagCompound buildCraftTag(ItemStack[][] ingredients, ItemStack output, int count,
        List<NBTTagCompound> reachablePatterns) {
        NBTTagCompound tag = RecipeNbtSerializer.writeRecipe(ingredients, output, count);
        RecipeNbtSerializer.setPatterns(tag, reachablePatterns);
        return tag;
    }

    private static int estimateActionPacketBytes(int windowId, NBTTagCompound recipeTag) {
        NBTTagCompound wrapper = new NBTTagCompound();
        wrapper.setTag("recipe", recipeTag);
        wrapper.setInteger("windowId", windowId);
        return RecipeNbtSerializer.estimateCompressedSize(wrapper);
    }

    private static int estimateAutoCraftPacketBytes(int requestId, int windowId, NBTTagCompound recipeTag) {
        NBTTagCompound wrapper = new NBTTagCompound();
        wrapper.setInteger("requestId", requestId);
        wrapper.setInteger("windowId", windowId);
        wrapper.setTag("recipe", recipeTag);
        wrapper.setBoolean("allowSubCrafting", true);
        wrapper.setBoolean("usePlayerInventory", true);
        return RecipeNbtSerializer.estimateCompressedSize(wrapper);
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
        if (handler == null) return null;
        PositionedStack result = handler.getResultStack(recipeIndex);
        if (result == null || result.item == null) return null;
        return result.item.copy();
    }
}
