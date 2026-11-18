package com.hepdd.toms_storage.crafting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

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

        return executePlan(terminal, player, result.getPlan());
    }

    static CraftResult executePlan(TileEntityStorageTerminal terminal, EntityPlayerMP player, CraftingPlan plan) {
        ApplyResult applyResult = apply(terminal, player, plan);
        return applyResult.isSuccess() ? CraftResult.success() : CraftResult.failure(applyResult.getReason());
    }

    private static ApplyResult apply(TileEntityStorageTerminal terminal, EntityPlayerMP player, CraftingPlan plan) {
        if (terminal == null || player == null || player.inventory == null) {
            return ApplyResult.failure("not_crafting_terminal");
        }
        int playerInvSize = player.inventory.mainInventory.length;
        int[] netPlayerPull = new int[playerInvSize];
        ItemStack[] expectedPlayerTemplates = new ItemStack[playerInvSize];
        Map<ExactStackKey, Long> virtualPool = new LinkedHashMap<>();
        Map<ExactStackKey, Long> netTerminalPull = new LinkedHashMap<>();
        Map<ExactStackKey, Long> finalOutputs = new LinkedHashMap<>();

        for (CraftOperation operation : plan.getOperations()) {
            Extraction[] extractions = operation.getExtractions();
            for (Extraction extraction : extractions) {
                if (extraction == null) continue;
                ItemStack req = extraction.getStack();
                if (req == null || req.getItem() == null || req.stackSize <= 0) continue;
                int needed = req.stackSize;

                if (extraction.isFromPlayer()) {
                    int slot = extraction.getPlayerSlot();
                    if (slot < 0 || slot >= playerInvSize) {
                        return ApplyResult.failure("apply_missing_player_item");
                    }
                    if (expectedPlayerTemplates[slot] == null) {
                        expectedPlayerTemplates[slot] = StorageItemUtils.copyWithSize(req, 1);
                    } else if (!StorageItemUtils.areItemStacksEqual(expectedPlayerTemplates[slot], req, true)) {
                        return ApplyResult.failure("apply_missing_player_item");
                    }
                    netPlayerPull[slot] += needed;
                } else {
                    ExactStackKey key = ExactStackKey.of(req);
                    long availableInPool = virtualPool.getOrDefault(key, 0L);
                    long fromPool = Math.min(availableInPool, needed);
                    if (fromPool > 0L) {
                        long remainingInPool = availableInPool - fromPool;
                        if (remainingInPool > 0L) {
                            virtualPool.put(key, remainingInPool);
                        } else {
                            virtualPool.remove(key);
                        }
                    }
                    long fromTerminal = needed - fromPool;
                    if (fromTerminal > 0L) {
                        netTerminalPull.merge(key, fromTerminal, Long::sum);
                    }
                }
            }

            for (Extraction extraction : extractions) {
                if (extraction == null) continue;
                ItemStack remainder = CraftingRemainderHandler.getRemainder(extraction.getStack());
                if (remainder != null && remainder.getItem() != null && remainder.stackSize > 0) {
                    virtualPool.merge(ExactStackKey.of(remainder), (long) remainder.stackSize, Long::sum);
                }
            }

            ItemStack output = operation.getOutput();
            if (output != null && output.getItem() != null && output.stackSize > 0) {
                if (operation.isFinalOutput()) {
                    finalOutputs.merge(ExactStackKey.of(output), (long) output.stackSize, Long::sum);
                } else {
                    virtualPool.merge(ExactStackKey.of(output), (long) output.stackSize, Long::sum);
                }
            }
        }

        // Pre-validate all player inventory slots before mutating anything.
        for (int slot = 0; slot < playerInvSize; slot++) {
            int required = netPlayerPull[slot];
            if (required <= 0) continue;
            ItemStack actual = player.inventory.getStackInSlot(slot);
            if (!isExpectedExtraction(expectedPlayerTemplates[slot], required, actual)) {
                NEIDebug.log(
                    "autocraft apply missing source=player expected=" + required
                        + "x"
                        + NEIDebug.stack(expectedPlayerTemplates[slot])
                        + " actual="
                        + NEIDebug.stack(actual));
                return ApplyResult.failure("apply_missing_player_item");
            }
        }

        // Batch-extract net required ingredients from storage network with atomic rollback on failure.
        Map<ExactStackKey, Long> extractedFromTerminal = new LinkedHashMap<>();
        for (Map.Entry<ExactStackKey, Long> entry : netTerminalPull.entrySet()) {
            ExactStackKey key = entry.getKey();
            long required = entry.getValue();
            long pulled = pullExactFromTerminal(terminal, key.template, required);
            if (pulled > 0L) {
                extractedFromTerminal.put(key, pulled);
            }
            if (pulled < required) {
                NEIDebug.log(
                    "autocraft apply missing source=terminal expected=" + required
                        + "x"
                        + NEIDebug.stack(key.template)
                        + " actual="
                        + pulled);
                for (Map.Entry<ExactStackKey, Long> rollback : extractedFromTerminal.entrySet()) {
                    returnToStorageOrDrop(terminal, player, rollback.getKey().template, rollback.getValue(), false);
                }
                return ApplyResult.failure("apply_missing_terminal_item");
            }
        }

        // Commit player slot deductions now that all terminal extractions have succeeded.
        for (int slot = 0; slot < playerInvSize; slot++) {
            if (netPlayerPull[slot] > 0) {
                player.inventory.decrStackSize(slot, netPlayerPull[slot]);
            }
        }

        // Deliver final outputs directly to player inventory first (never routing through network first).
        for (Map.Entry<ExactStackKey, Long> entry : finalOutputs.entrySet()) {
            deliverFinalOutput(terminal, player, entry.getKey().template, entry.getValue());
        }

        // Return leftover container remainders / damaged GT tools / excess sub-craft outputs.
        for (Map.Entry<ExactStackKey, Long> entry : virtualPool.entrySet()) {
            returnToStorageOrDrop(terminal, player, entry.getKey().template, entry.getValue(), true);
        }

        return ApplyResult.success();
    }

    private static long pullExactFromTerminal(TileEntityStorageTerminal terminal, ItemStack template, long required) {
        long remaining = required;
        StoredItemStack query = new StoredItemStack(template, 1);
        while (remaining > 0L) {
            int requestAmount = (int) Math.min(remaining, Integer.MAX_VALUE);
            StoredItemStack pulled = terminal.pullStack(query, requestAmount);
            if (pulled == null || pulled.getQuantity() <= 0L || pulled.getStack() == null) break;
            if (!StorageItemUtils.areItemStacksEqual(template, pulled.getStack(), true)) {
                returnToStorageOrDrop(terminal, null, pulled.getStack(), pulled.getQuantity(), false);
                break;
            }
            remaining -= pulled.getQuantity();
        }
        return required - remaining;
    }

    private static void deliverFinalOutput(TileEntityStorageTerminal terminal, EntityPlayerMP player,
        ItemStack template, long totalAmount) {
        if (template == null || totalAmount <= 0L) return;
        int maxStack = Math.max(1, template.getMaxStackSize());
        long remaining = totalAmount;
        while (remaining > 0L) {
            int chunkSize = (int) Math.min(remaining, maxStack);
            remaining -= chunkSize;
            ItemStack chunk = StorageItemUtils.copyWithSize(template, chunkSize);
            if (!player.inventory.addItemStackToInventory(chunk) && chunk.stackSize > 0) {
                terminal.pushOrDrop(chunk);
            }
        }
    }

    private static void returnToStorageOrDrop(TileEntityStorageTerminal terminal, EntityPlayerMP player,
        ItemStack template, long totalAmount, boolean fallbackToPlayer) {
        if (template == null || totalAmount <= 0L) return;
        int maxStack = Math.max(1, template.getMaxStackSize());
        long remaining = totalAmount;
        while (remaining > 0L) {
            int chunkSize = (int) Math.min(remaining, maxStack);
            remaining -= chunkSize;
            ItemStack chunk = StorageItemUtils.copyWithSize(template, chunkSize);
            if (fallbackToPlayer && player != null) {
                ItemStack remainder = terminal.pushStack(chunk);
                if (remainder != null && remainder.stackSize > 0) {
                    if (!player.inventory.addItemStackToInventory(remainder) && remainder.stackSize > 0) {
                        dropStackAtTerminal(terminal, player, remainder);
                    }
                }
            } else {
                terminal.pushOrDrop(chunk);
            }
        }
    }

    private static void dropStackAtTerminal(TileEntityStorageTerminal terminal, EntityPlayerMP player,
        ItemStack remainder) {
        if (remainder == null || remainder.stackSize <= 0) return;
        if (terminal.getWorldObj() != null) {
            EntityItem entityItem = new EntityItem(
                terminal.getWorldObj(),
                terminal.xCoord + 0.5D,
                terminal.yCoord + 0.5D,
                terminal.zCoord + 0.5D,
                remainder);
            terminal.getWorldObj()
                .spawnEntityInWorld(entityItem);
        } else if (player != null) {
            player.dropPlayerItemWithRandomChoice(remainder, false);
        }
    }

    private static boolean isExpectedExtraction(ItemStack expectedTemplate, int requiredCount, ItemStack actual) {
        return expectedTemplate != null && actual != null
            && actual.stackSize >= requiredCount
            && StorageItemUtils.areItemStacksEqual(expectedTemplate, actual, true);
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

    private static final class ExactStackKey {

        private final Item item;
        private final int damage;
        private final NBTTagCompound tag;
        private final ItemStack template;
        private final int hash;

        private ExactStackKey(ItemStack template) {
            this.template = template;
            this.item = template.getItem();
            this.damage = template.getItemDamage();
            this.tag = template.getTagCompound();
            int h = System.identityHashCode(this.item);
            h = 31 * h + this.damage;
            h = 31 * h + (this.tag == null ? 0 : this.tag.hashCode());
            this.hash = h;
        }

        private static ExactStackKey of(ItemStack source) {
            return new ExactStackKey(StorageItemUtils.copyWithSize(source, 1));
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ExactStackKey)) return false;
            ExactStackKey other = (ExactStackKey) obj;
            return damage == other.damage && item == other.item && Objects.equals(tag, other.tag);
        }
    }
}
