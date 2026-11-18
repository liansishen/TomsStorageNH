package com.hepdd.toms_storage.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S2FPacketSetSlot;

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
    private final Map<StoredItemStack, StoredItemStack> lastSyncedByKind = new LinkedHashMap<>();
    private boolean initialSyncSent;
    private final AutoCraftPreviewInventory autoCraftPreview = new AutoCraftPreviewInventory();

    public List<StoredItemStack> clientStacks = new ArrayList<>();
    private final Map<StoredItemStack, StoredItemStack> clientStackMap = new LinkedHashMap<>();
    private List<StoredItemStack> stagingClientStacks;
    private int stagingWindowId = -1;
    private boolean pendingDeltaKindsChanged;
    private int clientDataRevision;
    private int clientKindsRevision;

    public ContainerStorageTerminal(InventoryPlayer playerInventory, TileEntityStorageTerminal terminal) {
        this.playerInventory = playerInventory;
        this.terminal = terminal;

        addPlayerSlots(playerInventory, 8, getPlayerSlotsY());
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (player != null && player.worldObj != null && player.worldObj.isRemote) {
            return true;
        }
        return terminal != null && terminal.canInteractWith(player);
    }

    @Override
    public void addCraftingToCrafters(ICrafting crafter) {
        boolean wasInitialized = this.initialSyncSent;
        super.addCraftingToCrafters(crafter);
        if (wasInitialized && crafter instanceof EntityPlayerMP) {
            sendPacketsToCrafter(
                (EntityPlayerMP) crafter,
                PacketTerminalData.createFullSnapshots(windowId, new ArrayList<>(lastSyncedByKind.values())));
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (terminal == null || isClientSide() || crafters.isEmpty()) return;

        List<StoredItemStack> stacks = terminal.getStacks();
        if (!initialSyncSent) {
            rebuildServerBaseline(stacks);
            initialSyncSent = true;
            broadcastTerminalPackets(PacketTerminalData.createFullSnapshots(windowId, stacks));
            return;
        }

        if (isUnchangedFromBaseline(stacks)) {
            return;
        }

        List<StoredItemStack> deltaChanges = computeAndUpdateServerBaseline(stacks);
        if (deltaChanges.isEmpty()) {
            return;
        }

        int currentKindCount = stacks == null ? 0 : stacks.size();
        List<PacketTerminalData> packets = deltaChanges.size() > currentKindCount
            ? PacketTerminalData.createFullSnapshots(windowId, stacks)
            : PacketTerminalData.createDeltas(windowId, deltaChanges);
        broadcastTerminalPackets(packets);
    }

    @Override
    public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
        if (mode == 1 && slotId >= 0
            && slotId < inventorySlots.size()
            && ((Slot) inventorySlots.get(slotId)).inventory == player.inventory) {
            // Network storage has no client slot mirror; both sides acknowledge a null prediction.
            if (!player.worldObj.isRemote) {
                super.slotClick(slotId, clickedButton, mode, player);
                if (player instanceof EntityPlayerMP) {
                    // Accepted vanilla transactions suppress the normal slot delta.
                    ((EntityPlayerMP) player).playerNetServerHandler
                        .sendPacket(new S2FPacketSetSlot(windowId, slotId, getSlot(slotId).getStack()));
                }
            }
            return null;
        }
        return super.slotClick(slotId, clickedButton, mode, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
        if (terminal == null || (player != null && player.worldObj != null && player.worldObj.isRemote)) {
            return null;
        }
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
        if (terminal == null) return;
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
            syncPlayer(player, true);
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

        boolean syncHeld = false;
        if (action == SlotAction.SHIFT_PULL) {
            if (!player.inventory.addItemStackToInventory(result)) terminal.pushOrDrop(result);
        } else if (held == null) {
            player.inventory.setItemStack(result);
            syncHeld = true;
        } else {
            terminal.pushOrDrop(result);
        }
        syncPlayer(player, syncHeld);
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

    public boolean applyTerminalDataPacket(PacketTerminalData packet) {
        if (packet == null) return false;
        int currentWin = this.windowId & 0xFF;
        if (currentWin != (packet.getWindowId() & 0xFF)) {
            return false;
        }

        if (!packet.isDelta()) {
            if (packet.isReset()) {
                stagingClientStacks = new ArrayList<>();
                stagingWindowId = currentWin;
            } else if (stagingClientStacks == null || stagingWindowId != currentWin) {
                return false;
            }

            List<StoredItemStack> incoming = packet.getEntries();
            for (int i = 0, size = incoming.size(); i < size; i++) {
                StoredItemStack entry = incoming.get(i);
                if (entry != null && entry.getStack() != null && entry.getQuantity() > 0) {
                    stagingClientStacks.add(entry);
                }
            }

            if (packet.isCommit()) {
                List<StoredItemStack> completed = stagingClientStacks;
                stagingClientStacks = null;
                stagingWindowId = -1;
                setClientStacks(completed);
                return true;
            }
            return false;
        }

        boolean kindsChanged = pendingDeltaKindsChanged;
        List<StoredItemStack> incoming = packet.getEntries();
        for (int i = 0, size = incoming.size(); i < size; i++) {
            StoredItemStack entry = incoming.get(i);
            if (entry == null || entry.getStack() == null) continue;
            if (entry.getQuantity() <= 0) {
                if (clientStackMap.remove(entry) != null) {
                    kindsChanged = true;
                }
            } else {
                StoredItemStack existing = clientStackMap.get(entry);
                if (existing != null) {
                    existing.setQuantity(entry.getQuantity());
                } else {
                    StoredItemStack owned = new StoredItemStack(entry.getStack(), entry.getQuantity());
                    clientStackMap.put(owned, owned);
                    kindsChanged = true;
                }
            }
        }

        if (packet.isCommit()) {
            pendingDeltaKindsChanged = false;
            if (kindsChanged) {
                clientStacks = new ArrayList<>(clientStackMap.values());
                clientKindsRevision++;
            }
            clientDataRevision++;
            return true;
        }
        pendingDeltaKindsChanged = kindsChanged;
        return false;
    }

    public void setClientStacks(List<StoredItemStack> stacks) {
        clientStackMap.clear();
        pendingDeltaKindsChanged = false;
        if (stacks != null) {
            for (int i = 0, size = stacks.size(); i < size; i++) {
                StoredItemStack stack = stacks.get(i);
                if (stack == null || stack.getStack() == null || stack.getQuantity() <= 0) continue;
                StoredItemStack existing = clientStackMap.get(stack);
                if (existing == null) {
                    clientStackMap.put(stack, stack);
                } else {
                    existing.setQuantity(existing.getQuantity() + stack.getQuantity());
                }
            }
        }
        clientStacks = new ArrayList<>(clientStackMap.values());
        clientKindsRevision++;
        clientDataRevision++;
    }

    public int getClientDataRevision() {
        return clientDataRevision;
    }

    public int getClientKindsRevision() {
        return clientKindsRevision;
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
        syncPlayer(player, false);
    }

    private void pushMatchingFromPlayer(EntityPlayerMP player, ItemStack template) {
        if (template == null) return;
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (StorageItemUtils.areItemStacksEqual(template, stack, true)) {
                player.inventory.mainInventory[i] = terminal.pushStack(stack.copy());
            }
        }
        syncPlayer(player, false);
    }

    private void syncPlayer(EntityPlayerMP player, boolean syncHeldItem) {
        player.inventory.markDirty();
        detectAndSendChanges();
        if (syncHeldItem) {
            player.updateHeldItem();
        }
    }

    private boolean isClientSide() {
        return playerInventory != null && playerInventory.player != null
            && playerInventory.player.worldObj != null
            && playerInventory.player.worldObj.isRemote;
    }

    private void rebuildServerBaseline(List<StoredItemStack> stacks) {
        lastSyncedByKind.clear();
        if (stacks != null) {
            for (int i = 0, size = stacks.size(); i < size; i++) {
                StoredItemStack s = stacks.get(i);
                if (s == null || s.getStack() == null || s.getQuantity() <= 0) continue;
                StoredItemStack existing = lastSyncedByKind.get(s);
                if (existing == null) {
                    StoredItemStack copy = new StoredItemStack(s.getStack(), s.getQuantity());
                    lastSyncedByKind.put(copy, copy);
                } else {
                    existing.grow(s.getQuantity());
                }
            }
        }
        lastStacks = new ArrayList<>(lastSyncedByKind.values());
    }

    private boolean isUnchangedFromBaseline(List<StoredItemStack> stacks) {
        if (stacks == null) return lastSyncedByKind.isEmpty();
        if (stacks.size() != lastSyncedByKind.size()) return false;
        for (int i = 0, size = stacks.size(); i < size; i++) {
            StoredItemStack s = stacks.get(i);
            if (s == null || s.getStack() == null || s.getQuantity() <= 0) return false;
            StoredItemStack baseline = lastSyncedByKind.get(s);
            if (baseline == null || baseline.getQuantity() != s.getQuantity()) return false;
        }
        return true;
    }

    private List<StoredItemStack> computeAndUpdateServerBaseline(List<StoredItemStack> stacks) {
        int previousSize = lastSyncedByKind.size();
        int matchedOld = 0;
        List<StoredItemStack> changes = new ArrayList<>();

        if (stacks != null && !stacks.isEmpty()) {
            for (int i = 0, size = stacks.size(); i < size; i++) {
                StoredItemStack s = stacks.get(i);
                if (s == null || s.getStack() == null || s.getQuantity() <= 0) continue;
                StoredItemStack existing = lastSyncedByKind.get(s);
                if (existing == null) {
                    StoredItemStack added = new StoredItemStack(s.getStack(), s.getQuantity());
                    lastSyncedByKind.put(added, added);
                    changes.add(added);
                } else {
                    matchedOld++;
                    if (existing.getQuantity() != s.getQuantity()) {
                        existing.setQuantity(s.getQuantity());
                        changes.add(existing);
                    }
                }
            }
        }

        if (matchedOld < previousSize) {
            Set<StoredItemStack> currentSeen = stacks == null || stacks.isEmpty() ? Collections.emptySet()
                : new HashSet<>(stacks);
            Iterator<Map.Entry<StoredItemStack, StoredItemStack>> it = lastSyncedByKind.entrySet()
                .iterator();
            while (it.hasNext()) {
                StoredItemStack oldEntry = it.next()
                    .getValue();
                if (!currentSeen.contains(oldEntry)) {
                    it.remove();
                    changes.add(StoredItemStack.wrapOwned(oldEntry.getStack(), 0L));
                }
            }
        }

        lastStacks = new ArrayList<>(lastSyncedByKind.values());
        return changes;
    }

    private void broadcastTerminalPackets(List<PacketTerminalData> packets) {
        if (packets == null || packets.isEmpty()) return;
        for (Object crafter : crafters) {
            if (crafter instanceof EntityPlayerMP) {
                sendPacketsToCrafter((EntityPlayerMP) crafter, packets);
            } else if (crafter instanceof ICrafting && playerInventory.player instanceof EntityPlayerMP) {
                sendPacketsToCrafter((EntityPlayerMP) playerInventory.player, packets);
            }
        }
    }

    private static void sendPacketsToCrafter(EntityPlayerMP target, List<PacketTerminalData> packets) {
        if (ModNetwork.channel == null || target == null) return;
        for (int i = 0, size = packets.size(); i < size; i++) {
            ModNetwork.channel.sendTo(packets.get(i), target);
        }
    }
}
