package com.hepdd.toms_storage.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.ModNetwork;
import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.nei.AutoCraftPreviewInventory;
import com.hepdd.toms_storage.network.PacketTerminalData;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

public class ContainerStorageTerminal extends Container {

    public static final int ROWS = 5;
    public static final int COLUMNS = 9;
    public static final int VISIBLE_STACKS = ROWS * COLUMNS;

    protected final TileEntityStorageTerminal terminal;
    protected final InventoryPlayer playerInventory;
    protected List<StoredItemStack> lastStacks = new ArrayList<>();
    private final AutoCraftPreviewInventory autoCraftPreview = new AutoCraftPreviewInventory();

    public List<StoredItemStack> clientStacks = new ArrayList<>();
    public int sorting;
    public String search = "";

    public ContainerStorageTerminal(InventoryPlayer playerInventory, TileEntityStorageTerminal terminal) {
        this.playerInventory = playerInventory;
        this.terminal = terminal;

        addPlayerSlots(playerInventory, 8, getPlayerSlotsY());
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return terminal.canInteractWith(player);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (terminal == null) return;

        List<StoredItemStack> stacks = terminal.getStacks();
        if (!areListsEqual(stacks, lastStacks) || sorting != terminal.getSorting()
            || !search.equals(terminal.getLastSearch())) {
            lastStacks = copyList(stacks);
            sorting = terminal.getSorting();
            search = terminal.getLastSearch();
            PacketTerminalData packet = new PacketTerminalData(stacks, sorting, search);
            for (Object crafter : crafters) {
                if (crafter instanceof EntityPlayerMP) {
                    ModNetwork.channel.sendTo(packet, (EntityPlayerMP) crafter);
                } else if (crafter instanceof ICrafting && playerInventory.player instanceof EntityPlayerMP) {
                    ModNetwork.channel.sendTo(packet, (EntityPlayerMP) playerInventory.player);
                }
            }
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        Slot slot = slotIndex >= 0 && slotIndex < inventorySlots.size() ? (Slot) inventorySlots.get(slotIndex) : null;
        if (slot == null || !slot.getHasStack()) return null;

        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        ItemStack remainder = terminal.pushStack(stack.copy());
        if (remainder != null && remainder.stackSize == stack.stackSize) return null;
        slot.putStack(remainder);
        slot.onSlotChanged();
        return original;
    }

    public void handleTerminalAction(EntityPlayerMP player, SlotAction action, ItemStack clickedStack) {
        if (action == SlotAction.QUICK_DEPOSIT) {
            quickDeposit(player);
            return;
        }

        if (action == SlotAction.PUSH_MATCHING_FROM_PLAYER) {
            pushMatchingFromPlayer(player, clickedStack);
            return;
        }

        ItemStack held = player.inventory.getItemStack();
        if (action == SlotAction.PULL_OR_PUSH_STACK && held != null) {
            player.inventory.setItemStack(terminal.pushStack(held.copy()));
            syncPlayer(player);
            return;
        }

        if (clickedStack == null) return;
        int amount = clickedStack.getMaxStackSize();
        if (action == SlotAction.PULL_ONE) amount = 1;
        if (action == SlotAction.GET_HALF) amount = Math.max(1, clickedStack.getMaxStackSize() / 2);
        if (action == SlotAction.GET_QUARTER) amount = Math.max(1, clickedStack.getMaxStackSize() / 4);

        StoredItemStack pulled = terminal.pullStack(new StoredItemStack(clickedStack), amount);
        if (pulled == null) return;
        ItemStack result = pulled.getActualStack();

        if (action == SlotAction.SHIFT_PULL) {
            if (!player.inventory.addItemStackToInventory(result)) terminal.pushOrDrop(result);
        } else if (held == null) {
            player.inventory.setItemStack(result);
        } else {
            terminal.pushOrDrop(result);
        }
        syncPlayer(player);
    }

    public TileEntityStorageTerminal getTerminal() {
        return terminal;
    }

    public List<StoredItemStack> getPreviewStacks() {
        return autoCraftPreview.getStacks(clientStacks);
    }

    public AutoCraftPreviewInventory getAutoCraftPreview() {
        return autoCraftPreview;
    }

    protected int getPlayerSlotsY() {
        return 120;
    }

    protected void addPlayerSlots(InventoryPlayer inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(inventory, col, x + col * 18, y + 58));
        }
    }

    private void quickDeposit(EntityPlayerMP player) {
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null) {
                player.inventory.mainInventory[i] = terminal.pushStack(stack.copy());
            }
        }
        syncPlayer(player);
    }

    private void pushMatchingFromPlayer(EntityPlayerMP player, ItemStack template) {
        if (template == null) return;
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (StorageItemUtils.areItemStacksEqual(template, stack, true)) {
                player.inventory.mainInventory[i] = terminal.pushStack(stack.copy());
            }
        }
        syncPlayer(player);
    }

    private void syncPlayer(EntityPlayerMP player) {
        player.inventory.markDirty();
        detectAndSendChanges();
        player.sendContainerToPlayer(this);
    }

    private boolean areListsEqual(List<StoredItemStack> first, List<StoredItemStack> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            StoredItemStack a = first.get(i);
            StoredItemStack b = second.get(i);
            if (!a.equals(b) || a.getQuantity() != b.getQuantity()) return false;
        }
        return true;
    }

    private List<StoredItemStack> copyList(List<StoredItemStack> stacks) {
        List<StoredItemStack> copy = new ArrayList<>();
        for (StoredItemStack stack : stacks) {
            copy.add(new StoredItemStack(stack.getStack(), stack.getQuantity()));
        }
        return copy;
    }
}
