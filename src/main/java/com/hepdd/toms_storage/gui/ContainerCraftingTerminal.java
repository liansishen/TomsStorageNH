package com.hepdd.toms_storage.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.world.World;

import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.nei.NEIDebug;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;

public class ContainerCraftingTerminal extends ContainerStorageTerminal {

    private static final int TERMINAL_SOURCE_SLOT_BASE = -2000;

    public final InventoryCrafting craftMatrix = new InventoryCrafting(this, 3, 3);
    public final InventoryCraftResult craftResult = new InventoryCraftResult();
    private final World world;
    private final Slot resultSlot;
    private List<StoredItemStack> neiTransferStacks;

    public ContainerCraftingTerminal(InventoryPlayer playerInventory, TileEntityCraftingTerminal terminal) {
        super(playerInventory, terminal);
        world = terminal.getWorldObj();

        resultSlot = addSlotToContainer(
            new SlotCrafting(playerInventory.player, craftMatrix, craftResult, 0, 120, 129));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlotToContainer(new Slot(craftMatrix, col + row * 3, 26 + col * 18, 111 + row * 18));
            }
        }
        onCraftMatrixChanged(craftMatrix);
    }

    @Override
    protected int getPlayerSlotsY() {
        return 174;
    }

    @Override
    public void onCraftMatrixChanged(net.minecraft.inventory.IInventory inventory) {
        craftResult.setInventorySlotContents(
            0,
            CraftingManager.getInstance()
                .findMatchingRecipe(craftMatrix, world));
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (!world.isRemote) {
            for (int i = 0; i < craftMatrix.getSizeInventory(); i++) {
                ItemStack stack = craftMatrix.getStackInSlotOnClosing(i);
                if (stack != null) terminal.pushOrDrop(stack);
            }
        }
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

    public void beginNeiTransfer() {
        neiTransferStacks = copyStoredStacks(world.isRemote ? clientStacks : lastStacks);
        NEIDebug.log("beginNeiTransfer snapshot=" + neiTransferStacks.size() + " remote=" + world.isRemote);
    }

    public void endNeiTransfer(EntityPlayer player) {
        NEIDebug.log("endNeiTransfer remote=" + world.isRemote);
        neiTransferStacks = null;
        if (!world.isRemote) syncTerminalSourceClick(player);
    }

    public List<StoredItemStack> getNeiTransferStacks() {
        return neiTransferStacks != null ? neiTransferStacks : clientStacks;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (slotIndex == resultSlot.slotNumber && resultSlot.getHasStack()) {
            ItemStack output = resultSlot.getStack()
                .copy();
            resultSlot.decrStackSize(output.stackSize);
            terminal.pushOrDrop(output.copy());
            resultSlot.onPickupFromSlot(player, output);
            detectAndSendChanges();
            return output;
        }
        return super.transferStackInSlot(player, slotIndex);
    }

    @Override
    public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
        if (isTerminalSourceSlot(slotId)) {
            NEIDebug.log(
                "ContainerCraftingTerminal.slotClick terminalSource slotId=" + slotId
                    + " button="
                    + clickedButton
                    + " mode="
                    + mode
                    + " remote="
                    + world.isRemote
                    + " held="
                    + NEIDebug.stack(player.inventory.getItemStack()));
            return handleTerminalSourceClick(slotId, clickedButton, mode, player);
        }
        return super.slotClick(slotId, clickedButton, mode, player);
    }

    public static int getTerminalSourceSlotId(int index) {
        return TERMINAL_SOURCE_SLOT_BASE - index;
    }

    private boolean isTerminalSourceSlot(int slotId) {
        return slotId <= TERMINAL_SOURCE_SLOT_BASE;
    }

    private ItemStack handleTerminalSourceClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
        if (mode != 0) return null;
        int index = TERMINAL_SOURCE_SLOT_BASE - slotId;
        return world.isRemote ? handleClientTerminalSourceClick(index, clickedButton, player)
            : handleServerTerminalSourceClick(index, clickedButton, player);
    }

    private ItemStack handleClientTerminalSourceClick(int index, int clickedButton, EntityPlayer player) {
        ItemStack held = player.inventory.getItemStack();
        if (held == null) {
            ItemStack stack = getClientTerminalSourceStack(index);
            NEIDebug.log(
                "client terminal source pickup index=" + index
                    + " source="
                    + NEIDebug.stack(stack)
                    + " clientStacks="
                    + clientStacks.size());
            if (stack == null) return null;
            ItemStack result = stack.copy();
            result.stackSize = clickedButton == 1 ? 1 : Math.min(stack.stackSize, stack.getMaxStackSize());
            player.inventory.setItemStack(result);
            NEIDebug.log("client terminal source picked=" + NEIDebug.stack(result));
            return result;
        }

        NEIDebug.log("client terminal source return held=" + NEIDebug.stack(held) + " button=" + clickedButton);
        if (clickedButton == 1) {
            held.stackSize--;
            if (held.stackSize <= 0) player.inventory.setItemStack(null);
        } else {
            player.inventory.setItemStack(null);
        }
        return held;
    }

    private ItemStack handleServerTerminalSourceClick(int index, int clickedButton, EntityPlayer player) {
        ItemStack held = player.inventory.getItemStack();
        if (held == null) {
            ItemStack stack = getServerTerminalSourceStack(index);
            NEIDebug.log(
                "server terminal source pickup index=" + index
                    + " source="
                    + NEIDebug.stack(stack)
                    + " lastStacks="
                    + lastStacks.size());
            if (stack == null) return null;

            int amount = clickedButton == 1 ? 1 : Math.min(stack.stackSize, stack.getMaxStackSize());
            StoredItemStack pulled = terminal.pullStack(new StoredItemStack(stack), amount);
            NEIDebug.log(
                "server terminal source pull amount=" + amount
                    + " pulled="
                    + (pulled == null ? "null" : NEIDebug.stack(pulled.getActualStack())));
            if (pulled == null) return null;

            ItemStack result = pulled.getActualStack();
            player.inventory.setItemStack(result);
            syncTerminalSourceClick(player);
            NEIDebug.log("server terminal source picked=" + NEIDebug.stack(result));
            return result;
        }

        ItemStack moving = held.copy();
        if (clickedButton == 1) moving.stackSize = 1;
        ItemStack remainder = terminal.pushStack(moving);
        int moved = moving.stackSize - (remainder == null ? 0 : remainder.stackSize);
        NEIDebug.log(
            "server terminal source return moving=" + NEIDebug
                .stack(moving) + " remainder=" + NEIDebug.stack(remainder) + " moved=" + moved);
        if (moved <= 0) return null;

        held.stackSize -= moved;
        if (held.stackSize <= 0) player.inventory.setItemStack(null);
        syncTerminalSourceClick(player);
        return moving;
    }

    private ItemStack getClientTerminalSourceStack(int index) {
        List<StoredItemStack> stacks = neiTransferStacks != null ? neiTransferStacks : clientStacks;
        return index >= 0 && index < stacks.size() ? stacks.get(index)
            .getActualStack() : null;
    }

    private ItemStack getServerTerminalSourceStack(int index) {
        List<StoredItemStack> stacks = neiTransferStacks != null ? neiTransferStacks : lastStacks;
        return index >= 0 && index < stacks.size() ? stacks.get(index)
            .getActualStack() : null;
    }

    private static List<StoredItemStack> copyStoredStacks(List<StoredItemStack> stacks) {
        List<StoredItemStack> copy = new ArrayList<>();
        for (StoredItemStack stack : stacks) {
            copy.add(new StoredItemStack(stack.getStack(), stack.getQuantity()));
        }
        return copy;
    }

    private void syncTerminalSourceClick(EntityPlayer player) {
        player.inventory.markDirty();
        detectAndSendChanges();
        if (player instanceof EntityPlayerMP) ((EntityPlayerMP) player).sendContainerToPlayer(this);
    }
}
