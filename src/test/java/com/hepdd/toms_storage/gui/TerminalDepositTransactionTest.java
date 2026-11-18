package com.hepdd.toms_storage.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.util.IntHashMap;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.hepdd.toms_storage.ModNetwork;
import com.hepdd.toms_storage.inventory.CombinedStorageInventory;
import com.hepdd.toms_storage.inventory.IStorageInventory;
import com.hepdd.toms_storage.inventory.StorageInventoryProvider;
import com.hepdd.toms_storage.inventory.StorageTestInventory;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;
import com.hepdd.toms_storage.tile.TileEntityInventoryConnector;

import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;

@RunWith(Parameterized.class)
public class TerminalDepositTransactionTest {

    @Parameters(name = "crafting={0}")
    public static Collection<Object[]> terminals() {
        return Arrays.asList(new Object[] { false }, new Object[] { true });
    }

    private final boolean crafting;
    private final Item item = new Item();
    private final List<Packet> replies = new ArrayList<>();
    private EntityPlayerMP client;
    private EntityPlayerMP server;
    private ContainerStorageTerminal clientContainer;
    private ContainerStorageTerminal serverContainer;
    private AttachedTerminal clientTerminal;
    private StorageTestInventory storage;
    private NetHandlerPlayServer handler;
    private SimpleNetworkWrapper previousChannel;
    private short transaction;

    public TerminalDepositTransactionTest(boolean crafting) {
        this.crafting = crafting;
    }

    @Before
    public void setup() throws ReflectiveOperationException {
        previousChannel = ModNetwork.channel;
        ModNetwork.channel = mock(SimpleNetworkWrapper.class);
        World clientWorld = mock(World.class);
        clientWorld.isRemote = true;
        World serverWorld = mock(World.class);
        client = player(clientWorld);
        server = player(serverWorld);
        storage = new StorageTestInventory(3);
        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        connector.setWorldObj(clientWorld);
        clientTerminal = new AttachedTerminal(clientWorld, connector.getStorageInventory());
        AttachedTerminal serverTerminal = new AttachedTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(storage, ForgeDirection.NORTH));
        clientContainer = container(client.inventory, clientTerminal);
        serverContainer = container(server.inventory, serverTerminal);
        clientContainer.windowId = serverContainer.windowId = 7;
        client.openContainer = clientContainer;
        server.openContainer = serverContainer;
        server.playerNetServerHandler = mock(NetHandlerPlayServer.class);
        doAnswer(invocation -> {
            replies.add(invocation.getArgument(0));
            return null;
        }).when(server.playerNetServerHandler)
            .sendPacket(any(Packet.class));
        doCallRealMethod().when(server)
            .sendSlotContents(any(), anyInt(), any());
        doCallRealMethod().when(server)
            .updateHeldItem();
        serverContainer.addCraftingToCrafters(server);
        handler = mock(NetHandlerPlayServer.class, CALLS_REAL_METHODS);
        handler.playerEntity = server;
        Field pending = NetHandlerPlayServer.class.getDeclaredField("field_147372_n");
        pending.setAccessible(true);
        pending.set(handler, new IntHashMap());
        replies.clear();
        clearInvocations(server, server.playerNetServerHandler);
    }

    @After
    public void restoreChannel() {
        ModNetwork.channel = previousChannel;
    }

    private EntityPlayerMP player(World world) {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        player.worldObj = world;
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities();
        return player;
    }

    private ContainerStorageTerminal container(InventoryPlayer inventory, AttachedTerminal terminal) {
        return crafting ? new TransactionCraftingContainer(inventory, terminal)
            : new ContainerStorageTerminal(inventory, terminal);
    }

    @Test
    public void ordinaryDragSplittingRetainsVanillaDistribution() {
        client.inventory.setItemStack(stack(8));
        clientContainer.slotClick(-999, 0, 5, client);
        clientContainer.slotClick(0, 1, 5, client);
        clientContainer.slotClick(1, 1, 5, client);
        clientContainer.slotClick(-999, 2, 5, client);
        assertEquals(
            4,
            clientContainer.getSlot(0)
                .getStack().stackSize);
        assertEquals(
            4,
            clientContainer.getSlot(1)
                .getStack().stackSize);
        assertNull(client.inventory.getItemStack());
        assertEquals(0, clientTerminal.pushCalls);
    }

    @Test
    public void craftingResultShiftClickKeepsResultTransferRoute() {
        if (!crafting) return;
        TransactionCraftingContainer container = (TransactionCraftingContainer) clientContainer;
        int result = ContainerCraftingTerminal.GRID_SLOT_START - 1;
        container.getSlot(result)
            .putStack(stack(16));
        assertNull(container.slotClick(result, 0, 1, client));
        assertEquals(1, container.resultTransfers);
        assertEquals(
            16,
            container.getSlot(result)
                .getStack().stackSize);
        assertEquals(0, clientTerminal.pushCalls);
    }

    private ItemStack stack(int amount) {
        return new ItemStack(item, amount);
    }

    private void source(int slot, ItemStack stack) {
        clientContainer.getSlot(slot)
            .putStack(stack.copy());
        serverContainer.getSlot(slot)
            .putStack(stack.copy());
    }

    private void click(int slot) {
        ItemStack predicted = clientContainer.slotClick(slot, 0, 1, client);
        assertNull(predicted);
        handler.processClickWindow(new C0EPacketClickWindow(7, slot, 0, 1, predicted, ++transaction));
        assertTrue(serverContainer.isPlayerNotUsingContainer(server));
        assertFalse(server.isChangingQuantityOnly);
    }

    private void applyReplies(int expectedTransactions) {
        int accepted = 0;
        for (Packet reply : replies) {
            if (reply instanceof S32PacketConfirmTransaction confirmation) {
                assertEquals(7, confirmation.func_148889_c());
                assertTrue(confirmation.func_148888_e());
                accepted++;
            } else if (reply instanceof S2FPacketSetSlot update) {
                assertEquals(7, update.func_149175_c());
                clientContainer.putStackInSlot(update.func_149173_d(), ItemStack.copyItemStack(update.func_149174_e()));
            }
        }
        assertEquals(expectedTransactions, accepted);
        verify(server, never()).sendContainerAndContentsToPlayer(any(), any());
    }

    private int stored() {
        int amount = 0;
        for (ItemStack stack : storage.stacks) {
            if (stack != null) amount += stack.stackSize;
        }
        return amount;
    }

    @Test
    public void sourceReplyPrecedesLargeNetworkAggregation() {
        List<StorageTestInventory> chests = new ArrayList<>();
        CombinedStorageInventory network = new CombinedStorageInventory();
        for (int index = 0; index < 100; index++) {
            StorageTestInventory chest = new StorageTestInventory(27);
            for (int slot = 0; slot < 27; slot++) chest.stacks[slot] = stack(16);
            chests.add(chest);
            network.add(StorageInventoryProvider.getInventory(chest, ForgeDirection.NORTH));
        }
        ((AttachedTerminal) serverContainer.getTerminal()).attached = network;
        source(0, stack(1));
        int[] beforeReply = { -1, -1 };
        doAnswer(invocation -> {
            Packet reply = invocation.getArgument(0);
            replies.add(reply);
            if (reply instanceof S2FPacketSetSlot update && update.func_149173_d() == 0) {
                beforeReply[0] = chests.stream()
                    .mapToInt(chest -> chest.stackReads)
                    .sum();
                beforeReply[1] = chests.stream()
                    .mapToInt(chest -> chest.sizeQueries)
                    .sum();
            }
            return null;
        }).when(server.playerNetServerHandler)
            .sendPacket(any(Packet.class));
        click(0);
        assertEquals(2, beforeReply[0]);
        assertEquals(5, beforeReply[1]);
        int afterReads = chests.stream()
            .mapToInt(chest -> chest.stackReads)
            .sum();
        int afterSizes = chests.stream()
            .mapToInt(chest -> chest.sizeQueries)
            .sum();
        assertEquals(2702, afterReads);
        assertEquals(5505, afterSizes);
        assertEquals(17, chests.get(0).stacks[0].stackSize);
        applyReplies(1);
        assertNull(
            clientContainer.getSlot(0)
                .getStack());
        serverContainer.getTerminal()
            .updateEntity();
        assertEquals(
            afterReads,
            chests.stream()
                .mapToInt(chest -> chest.stackReads)
                .sum());
        System.out.println(
            "DEPOSIT beforeSourceReply stackReads=" + beforeReply[0]
                + " sizeQueries="
                + beforeReply[1]
                + "; afterRequest stackReads="
                + afterReads
                + " sizeQueries="
                + afterSizes);
    }

    @Test
    public void continuousDragDepositsEveryBackpackAndHotbarSlotWithoutWaiting() {
        for (int slot = 0; slot < 36; slot++) source(slot, stack(1));
        for (int slot = 0; slot < 36; slot++) click(slot);
        assertEquals(36, stored());
        applyReplies(36);
        for (int slot = 0; slot < 36; slot++) {
            assertNull(
                serverContainer.getSlot(slot)
                    .getStack());
            assertNull(
                clientContainer.getSlot(slot)
                    .getStack());
        }
        assertEquals(0, clientTerminal.pushCalls);
    }

    @Test
    public void partialCapacitySynchronizesRemainderAndAcceptsFollowingClick() {
        storage.stacks[0] = stack(60);
        storage.stacks[1] = stack(64);
        storage.stacks[2] = stack(64);
        source(0, stack(10));
        source(1, stack(5));
        click(0);
        click(1);
        assertEquals(192, stored());
        applyReplies(2);
        assertEquals(
            6,
            clientContainer.getSlot(0)
                .getStack().stackSize);
        assertEquals(
            5,
            clientContainer.getSlot(1)
                .getStack().stackSize);
        assertEquals(
            6,
            serverContainer.getSlot(0)
                .getStack().stackSize);
    }

    @Test
    public void fullStorageKeepsItemsAndAcceptsTransactions() {
        Arrays.fill(storage.stacks, stack(64));
        source(0, stack(16));
        click(0);
        applyReplies(1);
        assertEquals(192, stored());
        assertEquals(
            16,
            clientContainer.getSlot(0)
                .getStack().stackSize);
        assertEquals(
            16,
            serverContainer.getSlot(0)
                .getStack().stackSize);
    }

    @Test
    public void sidedInsertionRestrictionsKeepItemsInPlayerInventory() {
        storage.input = false;
        source(0, stack(16));
        click(0);
        applyReplies(1);
        assertEquals(0, stored());
        assertEquals(
            16,
            clientContainer.getSlot(0)
                .getStack().stackSize);
        assertEquals(
            16,
            serverContainer.getSlot(0)
                .getStack().stackSize);
    }

    @Test
    public void disconnectedStorageAndEmptySlotsDoNotBlockFollowingClicks() {
        ((AttachedTerminal) serverContainer.getTerminal()).attached = new CombinedStorageInventory();
        source(0, stack(16));
        click(0);
        click(1);
        applyReplies(2);
        assertEquals(0, stored());
        assertEquals(
            16,
            clientContainer.getSlot(0)
                .getStack().stackSize);
        assertNull(
            clientContainer.getSlot(1)
                .getStack());
    }

    @Test
    public void repeatedClicksBeforeSlotUpdatesDepositOnlyOnce() {
        source(0, stack(16));
        for (int attempt = 0; attempt < 4; attempt++) click(0);
        applyReplies(4);
        assertEquals(16, stored());
        assertNull(
            clientContainer.getSlot(0)
                .getStack());
        assertNull(
            serverContainer.getSlot(0)
                .getStack());
    }

    @Test
    public void depositsPreserveDistinctItemTags() {
        ItemStack first = stack(16);
        first.setTagCompound(new NBTTagCompound());
        first.getTagCompound()
            .setInteger("variant", 1);
        ItemStack second = first.copy();
        second.getTagCompound()
            .setInteger("variant", 2);
        source(0, first);
        source(1, second);
        click(0);
        click(1);
        applyReplies(2);
        assertEquals(32, stored());
        assertEquals(
            1,
            storage.stacks[0].getTagCompound()
                .getInteger("variant"));
        assertEquals(
            2,
            storage.stacks[1].getTagCompound()
                .getInteger("variant"));
    }

    @Test
    public void shiftDepositLeavesDirectlyAttachedClientInventoryUntouched() {
        StorageTestInventory clientChest = new StorageTestInventory(1);
        clientChest.stacks[0] = stack(60);
        clientTerminal.attached = StorageInventoryProvider.getInventory(clientChest, ForgeDirection.NORTH);
        source(0, stack(16));
        assertNull(clientContainer.slotClick(0, 0, 1, client));
        assertEquals(60, clientChest.stacks[0].stackSize);
        assertEquals(
            16,
            clientContainer.getSlot(0)
                .getStack().stackSize);
        assertEquals(0, clientTerminal.pushCalls);
    }

    @Test
    public void ordinaryLeftAndRightClicksRetainVanillaPrediction() {
        source(0, stack(16));
        assertEquals(16, clientContainer.slotClick(0, 0, 0, client).stackSize);
        assertNull(
            clientContainer.getSlot(0)
                .getStack());
        assertEquals(16, client.inventory.getItemStack().stackSize);
        clientContainer.slotClick(0, 0, 0, client);
        assertNull(client.inventory.getItemStack());
        clientContainer.slotClick(0, 1, 0, client);
        assertEquals(
            8,
            clientContainer.getSlot(0)
                .getStack().stackSize);
        assertEquals(8, client.inventory.getItemStack().stackSize);
        assertEquals(0, clientTerminal.pushCalls);
    }

    @Test
    public void hotbarNumberKeysRetainVanillaSwap() {
        source(0, stack(16));
        Item other = new Item();
        client.inventory.mainInventory[0] = new ItemStack(other, 3);
        clientContainer.slotClick(0, 0, 2, client);
        assertSame(
            other,
            clientContainer.getSlot(0)
                .getStack()
                .getItem());
        assertEquals(
            3,
            clientContainer.getSlot(0)
                .getStack().stackSize);
        assertSame(item, client.inventory.mainInventory[0].getItem());
        assertEquals(16, client.inventory.mainInventory[0].stackSize);
        assertEquals(0, clientTerminal.pushCalls);
    }

    @Test
    public void packetsForAnotherWindowKeepVanillaWindowGuard() {
        source(0, stack(16));
        handler.processClickWindow(new C0EPacketClickWindow(8, 0, 0, 1, null, (short) 1));
        assertTrue(replies.isEmpty());
        assertEquals(0, stored());
        assertEquals(
            16,
            serverContainer.getSlot(0)
                .getStack().stackSize);
    }

    @Test
    public void craftingGridSlotsRetainOrdinaryPickup() {
        if (!crafting) return;
        int slot = ContainerCraftingTerminal.GRID_SLOT_START;
        clientContainer.getSlot(slot)
            .putStack(stack(16));
        assertEquals(16, clientContainer.slotClick(slot, 0, 0, client).stackSize);
        assertNull(
            clientContainer.getSlot(slot)
                .getStack());
        assertEquals(16, client.inventory.getItemStack().stackSize);
        assertEquals(0, clientTerminal.pushCalls);
    }

    private static class AttachedTerminal extends TileEntityCraftingTerminal {

        private IStorageInventory attached;
        private int pushCalls;

        AttachedTerminal(World world, IStorageInventory attached) {
            setWorldObj(world);
            this.attached = attached;
        }

        @Override
        protected IStorageInventory getAttachedInventory() {
            return attached;
        }

        @Override
        public ItemStack pushStack(ItemStack stack) {
            pushCalls++;
            return super.pushStack(stack);
        }
    }

    private static class TransactionCraftingContainer extends ContainerCraftingTerminal {

        private int resultTransfers;

        @Override
        public ItemStack transferStackInSlot(EntityPlayer player, int slotIndex) {
            if (slotIndex == GRID_SLOT_START - 1) resultTransfers++;
            return super.transferStackInSlot(player, slotIndex);
        }

        TransactionCraftingContainer(InventoryPlayer inventory, AttachedTerminal terminal) {
            super(inventory, terminal);
        }

        @Override
        public void onCraftMatrixChanged(IInventory inventory) {
            // These click tests run without Minecraft's recipe registry.
        }
    }
}
