package com.hepdd.toms_storage.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.world.World;

import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot;
import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot.Extraction;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;

public class ContainerCraftingTerminal extends ContainerStorageTerminal {

    public static final int GUI_WIDTH = 195;
    public static final int MAX_STORAGE_ROWS = 6;
    public static final int BASE_HEIGHT = 188;
    public static final int GUI_HEIGHT = BASE_HEIGHT + MAX_STORAGE_ROWS * 18;
    public static final int GRID_X = 37;
    public static final int GRID_SLOT_START = 37;
    private int guiHeight = GUI_HEIGHT;

    public final InventoryCrafting craftMatrix = new InventoryCrafting(this, 3, 3);
    public final InventoryCraftResult craftResult = new InventoryCraftResult();
    private final World world;
    private final Slot resultSlot;
    private boolean autoRefill = true;
    private int lastAutoRefill = -1;
    private boolean batchingGridChanges;
    private boolean syncAfterCraft;
    private final boolean[] refillPlayerSlots;
    private int pendingCraftingClicks;

    public ContainerCraftingTerminal(InventoryPlayer playerInventory, TileEntityCraftingTerminal terminal) {
        super(playerInventory, terminal);
        world = terminal.getWorldObj();
        refillPlayerSlots = new boolean[playerInventory.mainInventory.length];

        resultSlot = addSlotToContainer(
            new SlotCrafting(playerInventory.player, craftMatrix, craftResult, 0, 131, GUI_HEIGHT - 137) {

                @Override
                public void onPickupFromSlot(EntityPlayer player, ItemStack output) {
                    ItemStack[] ingredients = new ItemStack[craftMatrix.getSizeInventory()];
                    for (int i = 0; i < ingredients.length; i++) {
                        ingredients[i] = ItemStack.copyItemStack(craftMatrix.getStackInSlot(i));
                    }
                    super.onPickupFromSlot(player, output);
                    if (!world.isRemote && player instanceof EntityPlayerMP && isAutoRefillEnabled()) {
                        refillGrid((EntityPlayerMP) player, ingredients);
                        syncAfterCraft = true;
                    }
                }
            });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlotToContainer(
                    new Slot(craftMatrix, col + row * 3, GRID_X + col * 18, GUI_HEIGHT - 155 + row * 18));
            }
        }
        onCraftMatrixChanged(craftMatrix);
    }

    @Override
    protected int getPlayerSlotsY() {
        return GUI_HEIGHT - 83;
    }

    public void setGuiHeight(int height) {
        int offset = height - guiHeight;
        for (Slot slot : inventorySlots) slot.yDisplayPosition += offset;
        guiHeight = height;
    }

    public boolean isAutoRefillEnabled() {
        return world.isRemote ? autoRefill : ((TileEntityCraftingTerminal) terminal).isAutoRefillEnabled();
    }

    public void setAutoRefillEnabled(boolean enabled) {
        autoRefill = enabled;
        if (!world.isRemote) ((TileEntityCraftingTerminal) terminal).setAutoRefillEnabled(enabled);
    }

    public void beginCraftingClick() {
        pendingCraftingClicks++;
    }

    public boolean hasPendingCraftingClick() {
        return pendingCraftingClicks > 0;
    }

    @Override
    public void addCraftingToCrafters(ICrafting crafter) {
        super.addCraftingToCrafters(crafter);
        crafter.sendProgressBarUpdate(this, 0, isAutoRefillEnabled() ? 1 : 0);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int state = isAutoRefillEnabled() ? 1 : 0;
        if (state == lastAutoRefill) return;
        for (Object crafter : crafters) {
            ((ICrafting) crafter).sendProgressBarUpdate(this, 0, state);
        }
        lastAutoRefill = state;
    }

    @Override
    public void updateProgressBar(int id, int value) {
        if (id == 0) {
            autoRefill = value != 0;
        } else if (id == 1) {
            pendingCraftingClicks = Math.max(0, pendingCraftingClicks - 1);
        } else {
            super.updateProgressBar(id, value);
        }
    }

    @Override
    public void onCraftMatrixChanged(net.minecraft.inventory.IInventory inventory) {
        if (batchingGridChanges) return;
        craftResult.setInventorySlotContents(
            0,
            CraftingManager.getInstance()
                .findMatchingRecipe(craftMatrix, world));
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        clearGrid();
    }

    public void clearGrid() {
        if (!world.isRemote) {
            for (int i = 0; i < craftMatrix.getSizeInventory(); i++) {
                ItemStack stack = craftMatrix.getStackInSlotOnClosing(i);
                if (stack != null) terminal.pushOrDrop(stack);
            }
            onCraftMatrixChanged(craftMatrix);
        }
    }

    public void fillRecipe(EntityPlayerMP player, ItemStack[][] ingredients, int count) {
        if (world.isRemote || player.inventory.getItemStack() != null) return;
        batchingGridChanges = true;
        try {
            fillRecipeContents(player, ingredients, count);
        } finally {
            batchingGridChanges = false;
            onCraftMatrixChanged(craftMatrix);
        }
    }

    private void fillRecipeContents(EntityPlayerMP player, ItemStack[][] ingredients, int count) {
        clearGrid();
        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);
        List<Extraction[]> batches = new ArrayList<>();
        ItemStack[][] selected = new ItemStack[ingredients.length][];
        boolean[] reusable = new boolean[ingredients.length];
        for (int round = 0; round < Math.max(1, Math.min(64, count)); round++) {
            Extraction[] batch = new Extraction[ingredients.length];
            boolean complete = true;
            boolean any = false;
            for (int i = 0; i < ingredients.length; i++) {
                if (ingredients[i].length == 0) continue;
                if (round > 0 && reusable[i]) continue;
                ItemStack[] candidates = round == 0 ? ingredients[i] : selected[i];
                Extraction extraction = round == 0 ? snapshot.reserveLargest(candidates) : snapshot.reserve(candidates);
                if (extraction != null) {
                    ItemStack stack = extraction.getStack();
                    int limit = Math.min(craftMatrix.getInventoryStackLimit(), stack.getMaxStackSize());
                    if ((round + 1) * stack.stackSize > limit) extraction = null;
                }
                batch[i] = extraction;
                if (extraction == null) {
                    complete = false;
                } else {
                    any = true;
                    if (round == 0) {
                        ItemStack chosen = extraction.getStack()
                            .copy();
                        selected[i] = new ItemStack[] { chosen };
                        if (chosen.getMaxStackSize() == 1 && chosen.getItem()
                            .hasContainerItem(chosen)) {
                            ItemStack remainder = chosen.getItem()
                                .getContainerItem(chosen.copy());
                            reusable[i] = remainder != null && remainder.getItem() == chosen.getItem();
                        }
                    }
                }
            }
            if (any && (complete || round == 0)) batches.add(batch);
            if (!complete || !any) break;
        }
        ItemStack[] templates = new ItemStack[ingredients.length];
        int[] networkAmounts = new int[ingredients.length];
        int[][] playerAmounts = new int[ingredients.length][player.inventory.mainInventory.length];
        for (Extraction[] batch : batches) {
            for (int i = 0; i < batch.length; i++) {
                Extraction extraction = batch[i];
                if (extraction == null) continue;
                templates[i] = extraction.getStack();
                if (extraction.isFromPlayer()) {
                    playerAmounts[i][extraction.getPlayerSlot()] += extraction.getStack().stackSize;
                } else {
                    networkAmounts[i] += extraction.getStack().stackSize;
                }
            }
        }
        for (int i = 0; i < templates.length; i++) {
            if (templates[i] == null) continue;
            ItemStack filled = networkAmounts[i] == 0 ? null
                : extractIngredient(player, StorageItemUtils.copyWithSize(templates[i], networkAmounts[i]), -1);
            for (int slot = 0; slot < playerAmounts[i].length; slot++) {
                if (playerAmounts[i][slot] == 0) continue;
                ItemStack extracted = extractIngredient(
                    player,
                    StorageItemUtils.copyWithSize(templates[i], playerAmounts[i][slot]),
                    slot);
                if (extracted == null) continue;
                if (filled == null) {
                    filled = extracted;
                } else {
                    filled.stackSize += extracted.stackSize;
                }
            }
            craftMatrix.setInventorySlotContents(i, filled);
        }
    }

    private void refillGrid(EntityPlayerMP player, ItemStack[] ingredients) {
        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);
        for (int i = 0; i < ingredients.length; i++) {
            ItemStack ingredient = ingredients[i];
            if (ingredient == null || craftMatrix.getStackInSlot(i) != null) continue;
            ItemStack requested = StorageItemUtils.copyWithSize(ingredient, 1);
            Extraction extraction = snapshot.reserve(new ItemStack[] { requested });
            if (extraction == null) continue;
            ItemStack extracted = extractIngredient(player, extraction);
            if (extracted == null) continue;
            if (extraction.isFromPlayer()) refillPlayerSlots[extraction.getPlayerSlot()] = true;
            craftMatrix.setInventorySlotContents(i, extracted);
        }
        onCraftMatrixChanged(craftMatrix);
    }

    private ItemStack extractIngredient(EntityPlayerMP player, Extraction extraction) {
        return extractIngredient(
            player,
            extraction.getStack(),
            extraction.isFromPlayer() ? extraction.getPlayerSlot() : -1);
    }

    private ItemStack extractIngredient(EntityPlayerMP player, ItemStack expected, int playerSlot) {
        ItemStack actual;
        if (playerSlot >= 0) {
            actual = player.inventory.decrStackSize(playerSlot, expected.stackSize);
        } else {
            StoredItemStack pulled = terminal.pullStack(new StoredItemStack(expected), expected.stackSize);
            actual = pulled == null ? null : pulled.getActualStack();
        }
        if (actual == null) return null;
        if (actual.stackSize != expected.stackSize || !StorageItemUtils.areItemStacksEqual(expected, actual, true)) {
            terminal.pushOrDrop(actual);
            return null;
        }
        return actual;
    }

    @Override
    public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
        syncAfterCraft = false;
        Arrays.fill(refillPlayerSlots, false);
        ItemStack clicked = super.slotClick(slotId, clickedButton, mode, player);
        if (syncAfterCraft) {
            EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
            for (int i = 0; i < refillPlayerSlots.length; i++) {
                if (!refillPlayerSlots[i]) continue;
                Slot slot = getSlotFromInventory(player.inventory, i);
                serverPlayer.playerNetServerHandler
                    .sendPacket(new S2FPacketSetSlot(windowId, slot.slotNumber, slot.getStack()));
            }
            // Refilling can restore the pre-click stack, hiding changes from vanilla delta sync.
            for (int i = 0; i < craftMatrix.getSizeInventory(); i++) {
                int slot = GRID_SLOT_START + i;
                serverPlayer.playerNetServerHandler
                    .sendPacket(new S2FPacketSetSlot(windowId, slot, craftMatrix.getStackInSlot(i)));
            }
            serverPlayer.playerNetServerHandler
                .sendPacket(new S2FPacketSetSlot(windowId, resultSlot.slotNumber, resultSlot.getStack()));
        }
        return clicked;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex == resultSlot.slotNumber && resultSlot.getHasStack()) {
            if (world.isRemote) return null;
            ItemStack output = resultSlot.getStack()
                .copy();
            int crafts = Math.max(1, output.getMaxStackSize() / output.stackSize);
            boolean crafted = false;
            for (int i = 0; i < crafts && resultSlot.getHasStack(); i++) {
                ItemStack current = resultSlot.getStack()
                    .copy();
                if (!StorageItemUtils.areItemStacksEqual(output, current, true)
                    || current.stackSize != output.stackSize) break;
                if (!canFitInPlayerInventory(current)) break;
                player.inventory.addItemStackToInventory(current.copy());
                resultSlot.decrStackSize(current.stackSize);
                resultSlot.onPickupFromSlot(player, current);
                crafted = true;
            }
            detectAndSendChanges();
            return crafted ? output : null;
        }
        return super.transferStackInSlot(player, slotIndex);
    }

    private boolean canFitInPlayerInventory(ItemStack output) {
        int remaining = output.stackSize;
        int limit = Math.min(playerInventory.getInventoryStackLimit(), output.getMaxStackSize());
        for (ItemStack stack : playerInventory.mainInventory) {
            if (stack == null) {
                remaining -= limit;
            } else if (output.isStackable() && StorageItemUtils.areItemStacksEqual(output, stack, true)) {
                remaining -= Math.max(0, limit - stack.stackSize);
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    @Override
    protected void retrySlotClick(int slotId, int clickedButton, boolean shift, EntityPlayer player) {
        // Refilling keeps the result populated; bound shift-crafting in transferStackInSlot.
        if (slotId != resultSlot.slotNumber) super.retrySlotClick(slotId, clickedButton, shift, player);
    }
}
