package com.hepdd.toms_storage.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot.Extraction;
import com.hepdd.toms_storage.crafting.CraftingPlan.CraftOperation;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.nei.NEIDebug;
import com.hepdd.toms_storage.nei.RecipeNbtSerializer;
import com.hepdd.toms_storage.network.PacketAutoCraftRequest;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

public final class SingleAutoCraftExecutor {

    private SingleAutoCraftExecutor() {}

    public static CraftResult execute(PacketAutoCraftRequest request, EntityPlayerMP player,
        ContainerCraftingTerminal container) {
        TileEntityStorageTerminal terminal = container.getTerminal();
        CraftingPattern rootPattern = RecipeNbtSerializer.readPattern(request.getRecipeTag());
        List<CraftingPattern> patterns = RecipeNbtSerializer.readPatterns(request.getRecipeTag());
        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(
            terminal,
            player,
            request.isUsePlayerInventory());
        CraftingPlanner planner = new CraftingPlanner(player, snapshot, patterns, request.isAllowSubCrafting());
        CraftingPlanner.CraftResult result = planner.plan(rootPattern, request.getCount());
        if (!result.isSuccess()) return CraftResult.failure(result.getReason());

        ApplyResult applyResult = apply(terminal, player, result.getPlan());
        return applyResult.isSuccess() ? CraftResult.success() : CraftResult.failure(applyResult.getReason());
    }

    private static ApplyResult apply(TileEntityStorageTerminal terminal, EntityPlayerMP player, CraftingPlan plan) {
        List<ItemStack> storedFinalOutputs = new ArrayList<>();
        List<ItemStack> directFinalOutputs = new ArrayList<>();
        for (CraftOperation operation : plan.getOperations()) {
            List<ItemStack> consumed = new ArrayList<>();
            for (Extraction extraction : operation.getExtractions()) {
                if (extraction == null) continue;
                ItemStack actual;
                if (extraction.isFromPlayer()) {
                    actual = player.inventory
                        .decrStackSize(extraction.getPlayerSlot(), extraction.getStack().stackSize);
                } else {
                    StoredItemStack pulled = terminal
                        .pullStack(new StoredItemStack(extraction.getStack()), extraction.getStack().stackSize);
                    actual = pulled == null ? null : pulled.getActualStack();
                }

                if (!isExpectedExtraction(extraction.getStack(), actual)) {
                    NEIDebug.log(
                        "autocraft apply missing source=" + (extraction.isFromPlayer() ? "player" : "terminal")
                            + " expected="
                            + NEIDebug.stack(extraction.getStack())
                            + " actual="
                            + NEIDebug.stack(actual)
                            + " output="
                            + NEIDebug.stack(operation.getOutput()));
                    if (actual != null) terminal.pushOrDrop(actual);
                    restoreConsumed(terminal, consumed);
                    return ApplyResult.failure(
                        extraction.isFromPlayer() ? "apply_missing_player_item" : "apply_missing_terminal_item");
                }
                consumed.add(actual);
            }
            for (ItemStack stack : consumed) {
                CraftingRemainderHandler.handleRemainder(terminal, stack.copy());
            }
            ItemStack output = operation.getOutput()
                .copy();
            if (operation.isFinalOutput()) {
                ItemStack remainder = terminal.pushStack(output.copy());
                int stored = output.stackSize - (remainder == null ? 0 : remainder.stackSize);
                if (stored > 0) {
                    ItemStack storedOutput = output.copy();
                    storedOutput.stackSize = stored;
                    storedFinalOutputs.add(storedOutput);
                }
                if (remainder != null && remainder.stackSize > 0) directFinalOutputs.add(remainder.copy());
            } else {
                terminal.pushOrDrop(output);
            }
        }
        moveFinalOutputsToPlayer(terminal, player, storedFinalOutputs, directFinalOutputs);
        return ApplyResult.success();
    }

    private static void moveFinalOutputsToPlayer(TileEntityStorageTerminal terminal, EntityPlayerMP player,
        List<ItemStack> storedFinalOutputs, List<ItemStack> directFinalOutputs) {
        for (ItemStack output : storedFinalOutputs) {
            StoredItemStack pulled = terminal.pullStack(new StoredItemStack(output), output.stackSize);
            ItemStack stack = pulled == null ? null : pulled.getActualStack();
            if (stack != null && !player.inventory.addItemStackToInventory(stack)) terminal.pushOrDrop(stack);
        }
        for (ItemStack output : directFinalOutputs) {
            ItemStack stack = output.copy();
            if (!player.inventory.addItemStackToInventory(stack)) terminal.pushOrDrop(stack);
        }
    }

    private static boolean isExpectedExtraction(ItemStack expected, ItemStack actual) {
        return expected != null && actual != null
            && actual.stackSize >= expected.stackSize
            && StorageItemUtils.areItemStacksEqual(expected, actual, true);
    }

    private static void restoreConsumed(TileEntityStorageTerminal terminal, List<ItemStack> consumed) {
        for (ItemStack stack : consumed) {
            terminal.pushOrDrop(stack.copy());
        }
    }

    public static final class CraftResult {

        private final boolean success;
        private final String reason;

        private CraftResult(boolean success, String reason) {
            this.success = success;
            this.reason = reason;
        }

        public static CraftResult success() {
            return new CraftResult(true, "");
        }

        public static CraftResult failure(String reason) {
            return new CraftResult(false, reason);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getReason() {
            return reason;
        }
    }

    private static final class ApplyResult {

        private final boolean success;
        private final String reason;

        private ApplyResult(boolean success, String reason) {
            this.success = success;
            this.reason = reason;
        }

        private static ApplyResult success() {
            return new ApplyResult(true, "");
        }

        private static ApplyResult failure(String reason) {
            return new ApplyResult(false, reason);
        }

        private boolean isSuccess() {
            return success;
        }

        private String getReason() {
            return reason;
        }
    }
}
