package com.hepdd.toms_storage.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import net.blay09.mods.cookingforblockheads.tile.TileFridge;
import net.minecraft.block.Block;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityHopper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Before;
import org.junit.Test;

import com.hepdd.toms_storage.Config;
import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.block.BlockInventoryCable;
import com.hepdd.toms_storage.inventory.StorageTestInventory.BulkIO;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;
import com.hepdd.toms_storage.tile.TileEntityInventoryConnector;
import com.hepdd.toms_storage.tile.TileEntityInventoryHopper;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.common.tileentities.storage.MTEDigitalChestBase;
import jds.bibliocraft.tileentities.TileEntityFramedChest;
import mcp.mobius.betterbarrels.common.blocks.TileEntityBarrel;

public class StorageInventoryTest {

    private final Item item = new Item();
    private final Map<String, TileEntity> tiles = new HashMap<>();
    private final Map<String, Block> blocks = new HashMap<>();
    private World world;
    private IChunkProvider chunks;
    private Block ordinaryBlock;

    @Before
    public void setup() {
        world = mock(World.class);
        chunks = mock(IChunkProvider.class);
        when(world.getChunkProvider()).thenReturn(chunks);
        when(chunks.chunkExists(anyInt(), anyInt())).thenReturn(true);
        when(world.getTotalWorldTime()).thenReturn(100L);
        ordinaryBlock = mock(Block.class);
        ModRegistry.trim = mock(Block.class);
        ModRegistry.inventoryCable = mock(Block.class);
        when(world.getTileEntity(anyInt(), anyInt(), anyInt())).thenAnswer(
            invocation -> tiles
                .get(key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))));
        when(world.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(
            invocation -> blocks.getOrDefault(
                key(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)),
                ordinaryBlock));
        Config.onlyTrimsConnect = false;
        Config.inventoryConnectorRange = 16;
        Config.maxInventoryCableNodes = 1024;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private void put(int x, int y, int z, TileEntity tile) {
        tile.setWorldObj(world);
        tile.xCoord = x;
        tile.yCoord = y;
        tile.zCoord = z;
        tiles.put(key(x, y, z), tile);
    }

    private ItemStack stack(int count) {
        return new ItemStack(item, count);
    }

    private IStorageInventory storage(StorageTestInventory inventory) {
        return StorageInventoryProvider.getInventory(inventory, ForgeDirection.NORTH);
    }

    private static long count(IStorageInventory inventory) {
        long count = 0;
        for (StoredItemStack stack : StorageInventoryUtils.getStacks(inventory)) count += stack.getQuantity();
        return count;
    }

    @Test
    public void acceptsVanillaChestsAndSubclasses() {
        assertNotNull(StorageInventoryProvider.getInventory(new TileEntityChest(), ForgeDirection.NORTH));
        assertNotNull(storage(new StorageTestInventory()));
    }

    @Test
    public void rejectsProcessingAndAutomationInventories() {
        assertNull(StorageInventoryProvider.getInventory(new TileEntityFurnace(), ForgeDirection.NORTH));
        assertNull(StorageInventoryProvider.getInventory(new TileEntityHopper(), ForgeDirection.NORTH));
        assertNull(StorageInventoryProvider.getInventory(new TileEntityDispenser(), ForgeDirection.NORTH));
        assertNull(StorageInventoryProvider.getInventory(null, ForgeDirection.NORTH));
    }

    @Test
    public void rejectsUnknownInventoriesEvenWithCapabilities() {
        TileEntity tile = mock(
            TileEntity.class,
            org.mockito.Mockito.withSettings()
                .extraInterfaces(IInventory.class));
        assertNull(StorageInventoryProvider.getInventory(tile, ForgeDirection.NORTH));
    }

    @Test
    public void acceptsOnlyStorageMetaTileEntities() {
        BaseMetaTileEntity chest = new BaseMetaTileEntity(new MTEDigitalChestBase());
        chest.io = new BulkIO(stack(10000), stack(64));
        chest.stacks[2] = stack(64);
        assertEquals(10064, count(storage(chest)));
        BaseMetaTileEntity machine = new BaseMetaTileEntity(new Object());
        machine.io = new BulkIO(stack(10000));
        assertNull(storage(machine));
    }

    @Test
    public void binnieCompartmentsExposeEveryTabOnEverySide() {
        for (int slots : new int[] { 100, 150, 200, 200, 300, 400 }) {
            binnie.core.machines.TileEntityMachine chest = new binnie.core.machines.TileEntityMachine(slots, true);
            chest.stacks[slots - 1] = stack(63);
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                IStorageInventory inventory = StorageInventoryProvider.getInventory(chest, side);
                assertEquals(slots, inventory.getSlots());
                assertEquals(63, count(inventory));
                assertEquals(1, inventory.insertItem(slots - 1, stack(2), true).stackSize);
                assertEquals(63, count(inventory));
                assertEquals(5, inventory.extractItem(slots - 1, 5, true).stackSize);
            }
            IStorageInventory inventory = storage(chest);
            assertEquals(1, inventory.insertItem(slots - 1, stack(2), false).stackSize);
            assertEquals(64, count(inventory));
            assertEquals(5, inventory.extractItem(slots - 1, 5, false).stackSize);
            assertEquals(59, count(inventory));
        }
    }

    @Test
    public void binnieMachineWrappersRequireCompartmentInventory() {
        binnie.core.machines.TileEntityMachine machine = new binnie.core.machines.TileEntityMachine(3, false);
        machine.stacks[0] = stack(10);
        assertNull(storage(machine));
        machine.machine = null;
        assertNull(storage(machine));
    }

    @Test
    public void binnieCompartmentsWorkWithNetworksAndDirectTerminals() {
        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        binnie.core.machines.TileEntityMachine chest = new binnie.core.machines.TileEntityMachine(400, true);
        chest.stacks[399] = stack(30);
        binnie.core.machines.TileEntityMachine machine = new binnie.core.machines.TileEntityMachine(3, false);
        machine.stacks[0] = stack(50);
        StorageTestInventory beyondMachine = new StorageTestInventory();
        beyondMachine.stacks[0] = stack(100);
        TileEntityStorageTerminal terminal = new TileEntityStorageTerminal();
        TileEntityCraftingTerminal crafting = new TileEntityCraftingTerminal();
        put(0, 0, 0, connector);
        put(1, 0, 0, chest);
        put(2, 0, 0, machine);
        put(3, 0, 0, beyondMachine);
        put(0, 1, 0, terminal);
        put(1, 1, 0, crafting);
        when(world.getBlockMetadata(anyInt(), anyInt(), anyInt())).thenReturn(ForgeDirection.DOWN.ordinal());
        assertEquals(30, count(connector.getStorageInventory()));
        assertEquals(
            30,
            terminal.getStacks()
                .get(0)
                .getQuantity());
        assertEquals(
            30,
            crafting.getStacks()
                .get(0)
                .getQuantity());
        assertEquals(
            5,
            terminal.pullStack(new StoredItemStack(stack(1)), 5)
                .getQuantity());
        assertEquals(
            5,
            crafting.pullStack(new StoredItemStack(stack(1)), 5)
                .getQuantity());
        assertEquals(20, chest.stacks[399].stackSize);
        assertEquals(20, count(connector.getStorageInventory()));
    }

    @Test
    public void barrelsRequireRealQuantityInterface() {
        assertNull(storage(new TileEntityBarrel()));
    }

    @Test
    public void barrelsReportAndExtractFullQuantity() {
        TileEntityBarrel barrel = new TileEntityBarrel();
        BulkIO io = new BulkIO(stack(10000));
        barrel.io = io;
        barrel.stacks[1] = stack(64);
        IStorageInventory inventory = storage(barrel);
        assertEquals(1, inventory.getSlots());
        assertEquals(10000, count(inventory));
        assertEquals(576, inventory.extractItem(0, 576, true).stackSize);
        assertEquals(10000, count(inventory));
        assertEquals(576, inventory.extractItem(0, 576, false).stackSize);
        assertEquals(9424, count(inventory));
    }

    @Test
    public void bulkInsertSimulationDoesNotMutate() {
        TileEntityBarrel barrel = new TileEntityBarrel();
        BulkIO io = new BulkIO(stack(99990));
        barrel.io = io;
        IStorageInventory inventory = storage(barrel);
        ItemStack offered = stack(64);
        assertEquals(54, inventory.insertItem(0, offered, true).stackSize);
        assertEquals(99990, count(inventory));
        assertEquals(54, inventory.insertItem(0, offered, false).stackSize);
        assertEquals(100000, count(inventory));
        assertEquals(64, offered.stackSize);
    }

    @Test
    public void bulkCapabilitiesRespectSidedPermissions() {
        TileEntityBarrel barrel = new TileEntityBarrel();
        barrel.io = new BulkIO(stack(1000));
        IStorageInventory inventory = storage(barrel);
        barrel.output = false;
        assertEquals(0, count(inventory));
        assertNull(inventory.extractItem(0, 1, false));
        barrel.input = false;
        assertEquals(64, inventory.insertItem(0, stack(64), false).stackSize);
        barrel.output = true;
        barrel.exposed = new int[] { 0 };
        assertNull(inventory.getStackInSlot(0));
    }

    @Test
    public void capabilitiesResetPriorSlotFilters() {
        TileEntityBarrel barrel = new TileEntityBarrel();
        barrel.io = new BulkIO(stack(1000));
        barrel.io.setAllowedSourceSlots(new int[0]);
        barrel.io.setAllowedSinkSlots(new int[0]);
        IStorageInventory inventory = storage(barrel);
        assertEquals(1000, count(inventory));
        assertNull(inventory.insertItem(0, stack(64), false));
        assertEquals(1064, count(inventory));
    }

    @Test
    public void drawersUseOneRealSlotPerDrawer() {
        StorageTestInventory tile = new StorageTestInventory(4);
        BulkIO io = new BulkIO(stack(1000), stack(2000));
        IStorageInventory inventory = new CapabilityInventoryAdapter(tile, tile, ForgeDirection.NORTH, io, true);
        assertEquals(2, inventory.getSlots());
        assertEquals(3000, count(inventory));
        assertEquals(500, inventory.extractItem(1, 500, false).stackSize);
        assertEquals(2500, count(inventory));
        assertNull(inventory.getStackInSlot(2));
    }

    @Test
    public void filtersInaccessibleStorageSlots() {
        StorageTestInventory chest = new StorageTestInventory();
        chest.stacks[0] = stack(32);
        chest.stacks[1] = stack(16);
        chest.exposed = new int[] { 0 };
        IStorageInventory inventory = storage(chest);
        assertEquals(32, count(inventory));
        assertNull(inventory.extractItem(1, 1, true));
        assertEquals(64, inventory.insertItem(1, stack(64), false).stackSize);
    }

    @Test
    public void lockedContainersStayLockedAfterAdapterCreation() {
        TileEntityFramedChest chest = new TileEntityFramedChest();
        chest.stacks[0] = stack(32);
        IStorageInventory inventory = storage(chest);
        assertEquals(32, count(inventory));
        chest.locked = true;
        assertEquals(0, count(inventory));
        assertNull(inventory.extractItem(0, 1, false));
        assertEquals(64, inventory.insertItem(0, stack(64), false).stackSize);
        chest.locked = false;
        assertEquals(32, count(inventory));
    }

    @Test
    public void auxiliarySlotsAreExcludedFromAllOperations() {
        StorageTestInventory chest = new StorageTestInventory();
        chest.stacks[0] = stack(5);
        chest.stacks[1] = stack(50);
        AtomicBoolean unlocked = new AtomicBoolean(true);
        IStorageInventory inventory = new StorageInventoryAdapter(
            chest,
            chest,
            ForgeDirection.NORTH,
            chest,
            unlocked::get,
            1);
        assertEquals(5, count(inventory));
        assertNull(inventory.getStackInSlot(1));
        assertNull(inventory.extractItem(1, 1, false));
        assertFalse(inventory.isItemValid(1, stack(1)));
        assertEquals(1, inventory.insertItem(1, stack(1), false).stackSize);
    }

    @Test
    public void largeStorageSlotsSupportMoreThanOneStack() {
        StorageTestInventory chest = new StorageTestInventory();
        chest.limit = Integer.MAX_VALUE;
        chest.stacks[0] = stack(10000);
        IStorageInventory inventory = storage(chest);
        assertNull(inventory.insertItem(0, stack(1000), false));
        assertEquals(11000, count(inventory));
        assertEquals(1000, inventory.extractItem(0, 1000, false).stackSize);
        assertEquals(10000, count(inventory));
    }

    @Test
    public void preservesNbtAndRejectsDifferentNbt() {
        StorageTestInventory chest = new StorageTestInventory();
        ItemStack tagged = stack(10);
        tagged.stackTagCompound = new NBTTagCompound();
        tagged.stackTagCompound.setString("variant", "a");
        chest.stacks[0] = tagged;
        IStorageInventory inventory = storage(chest);
        ItemStack offered = stack(1);
        assertEquals(1, inventory.insertItem(0, offered, false).stackSize);
        ItemStack extracted = inventory.extractItem(0, 5, false);
        assertEquals("a", extracted.stackTagCompound.getString("variant"));
    }

    @Test
    public void returnsOnlyItemsActuallyRemovedByContainer() {
        StorageTestInventory chest = new StorageTestInventory();
        chest.stacks[0] = stack(1000);
        chest.extractionLimit = 64;
        InventoryAdapter inventory = new InventoryAdapter(chest, ForgeDirection.NORTH);
        assertEquals(64, inventory.extractItem(0, 200, false).stackSize);
        assertEquals(936, chest.stacks[0].stackSize);
    }

    @Test
    public void doubleFridgeHasOneCopyOfEachInternalInventory() {
        TileFridge bottom = new TileFridge();
        TileFridge top = new TileFridge();
        bottom.neighbour = top;
        top.neighbour = bottom;
        bottom.internal.stacks[0] = stack(20);
        top.internal.stacks[0] = stack(30);
        IStorageInventory attached = storage(bottom);
        assertEquals(54, attached.getSlots());
        assertEquals(50, count(attached));
        CombinedStorageInventory network = new CombinedStorageInventory();
        network.add(attached);
        network.add(storage(top));
        assertEquals(54, network.getSlots());
        assertEquals(50, count(network));
        assertTrue(network.containsInventory(bottom));
        assertTrue(network.containsInventory(top));
    }

    @Test
    public void sharedStorageIdentityPreventsDoubleCountingAndLoops() {
        StorageTestInventory first = new StorageTestInventory();
        StorageTestInventory second = new StorageTestInventory();
        Object identity = new Object();
        first.stacks[0] = stack(20);
        second.stacks[0] = stack(20);
        CombinedStorageInventory combined = new CombinedStorageInventory();
        combined.add(new StorageInventoryAdapter(first, first, ForgeDirection.NORTH, identity, () -> true, 3));
        combined.add(new StorageInventoryAdapter(second, second, ForgeDirection.NORTH, identity, () -> true, 3));
        assertEquals(20, count(combined));
        assertEquals(
            1,
            combined.getInventories()
                .size());
        assertTrue(combined.containsInventory(first));
    }

    @Test
    public void scanStopsAtMachinesAndFindsLegalPaths() {
        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        StorageTestInventory adjacent = new StorageTestInventory();
        adjacent.stacks[0] = stack(10);
        StorageTestInventory beyondMachine = new StorageTestInventory();
        beyondMachine.stacks[0] = stack(20);
        put(0, 0, 0, connector);
        put(1, 0, 0, adjacent);
        put(2, 0, 0, new TileEntityFurnace());
        put(3, 0, 0, beyondMachine);
        assertEquals(10, count(connector.getStorageInventory()));
        tiles.remove(key(2, 0, 0));
        blocks.put(key(2, 0, 0), ModRegistry.trim);
        when(world.getTotalWorldTime()).thenReturn(120L);
        assertEquals(30, count(connector.getStorageInventory()));
    }

    @Test
    public void onlyTrimsRuleStillWorks() {
        Config.onlyTrimsConnect = true;
        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        StorageTestInventory chest = new StorageTestInventory();
        chest.stacks[0] = stack(10);
        put(0, 0, 0, connector);
        put(1, 0, 0, chest);
        assertEquals(0, count(connector.getStorageInventory()));
        tiles.remove(key(1, 0, 0));
        blocks.put(key(1, 0, 0), ModRegistry.trim);
        put(2, 0, 0, chest);
        when(world.getTotalWorldTime()).thenReturn(120L);
        assertEquals(10, count(connector.getStorageInventory()));
    }

    @Test
    public void cablesDoNotDirectlyExposeInventories() {
        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        StorageTestInventory chest = new StorageTestInventory();
        chest.stacks[0] = stack(10);
        put(0, 0, 0, connector);
        blocks.put(key(1, 0, 0), ModRegistry.inventoryCable);
        put(2, 0, 0, chest);
        assertEquals(0, count(connector.getStorageInventory()));
    }

    @Test
    public void directStorageAndCraftingTerminalsRejectMachines() {
        TileEntityStorageTerminal terminal = new TileEntityStorageTerminal();
        TileEntityCraftingTerminal crafting = new TileEntityCraftingTerminal();
        put(0, 0, 0, terminal);
        put(0, 1, 0, crafting);
        put(1, 0, 0, new TileEntityFurnace());
        put(1, 1, 0, new TileEntityFurnace());
        when(world.getBlockMetadata(anyInt(), anyInt(), anyInt())).thenReturn(ForgeDirection.EAST.ordinal());
        assertTrue(
            terminal.getStacks()
                .isEmpty());
        assertTrue(
            crafting.getStacks()
                .isEmpty());
        ItemStack offered = stack(10);
        assertSame(offered, terminal.pushStack(offered));
        StorageTestInventory chest = new StorageTestInventory();
        chest.stacks[0] = stack(10);
        put(1, 0, 0, chest);
        assertEquals(
            10,
            terminal.getStacks()
                .get(0)
                .getQuantity());
        assertEquals(
            5,
            terminal.pullStack(new StoredItemStack(stack(1)), 5)
                .getQuantity());
        assertEquals(5, chest.stacks[0].stackSize);
    }

    @Test
    public void networkHopperStillSuppliesMachines() {
        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        StorageTestInventory chest = new StorageTestInventory();
        chest.stacks[0] = stack(10);
        TileEntityInventoryHopper hopper = new TileEntityInventoryHopper();
        TileEntityFurnace furnace = new TileEntityFurnace();
        put(0, 0, 0, connector);
        put(-1, 0, 0, chest);
        put(0, -1, 0, hopper);
        put(0, -2, 0, furnace);
        when(world.getBlockMetadata(anyInt(), anyInt(), anyInt())).thenReturn(ForgeDirection.DOWN.ordinal());
        hopper.setFilter(stack(1));
        connector.getStorageInventory();
        hopper.updateEntity();
        assertEquals(9, chest.stacks[0].stackSize);
        assertEquals(1, furnace.getStackInSlot(0).stackSize);
        assertFalse(((CombinedStorageInventory) connector.getStorageInventory()).containsInventory(furnace));
    }

    @Test
    public void networkHopperImportsOnlyAccessibleMachineOutputs() {
        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        StorageTestInventory chest = new StorageTestInventory();
        TileEntityInventoryHopper hopper = new TileEntityInventoryHopper();
        TileEntityFurnace furnace = new TileEntityFurnace();
        furnace.setInventorySlotContents(0, stack(10));
        furnace.setInventorySlotContents(2, stack(1));
        put(0, 0, 0, connector);
        put(-1, 0, 0, chest);
        put(0, 1, 0, hopper);
        put(0, 2, 0, furnace);
        when(world.getBlockMetadata(anyInt(), anyInt(), anyInt())).thenReturn(ForgeDirection.DOWN.ordinal());
        IStorageInventory network = connector.getStorageInventory();
        assertEquals(0, count(network));
        hopper.updateEntity();
        assertEquals(1, count(network));
        assertNull(furnace.getStackInSlot(2));
        assertEquals(10, furnace.getStackInSlot(0).stackSize);
        for (int tick = 0; tick < 10; tick++) hopper.updateEntity();
        assertEquals(1, count(network));
        assertEquals(10, furnace.getStackInSlot(0).stackSize);
        assertFalse(((CombinedStorageInventory) network).containsInventory(furnace));
    }

    @Test
    public void legacyGtChestsExposeRealCountsAndExcludeDisplaySlots() {
        gtPlusPlus.xmod.gregtech.common.tileentities.storage.MTETieredChest meta = new gtPlusPlus.xmod.gregtech.common.tileentities.storage.MTETieredChest();
        meta.mItemCount = 10000;
        meta.mItemStack = stack(1);
        BaseMetaTileEntity tile = new BaseMetaTileEntity(meta);
        tile.stacks[1] = stack(64);
        tile.stacks[2] = stack(64);
        IStorageInventory inventory = storage(tile);
        assertEquals(10064, count(inventory));
        assertEquals(500, inventory.extractItem(0, 500, true).stackSize);
        assertEquals(10000, meta.mItemCount);
        assertEquals(500, inventory.extractItem(0, 500, false).stackSize);
        assertEquals(9564, count(inventory));
        assertEquals(2, inventory.getSlots());
        inventory.extractItem(0, 9500, false);
        assertEquals(0, meta.mItemCount);
        assertNull(meta.mItemStack);
        assertEquals(64, count(inventory));
    }

    @Test
    public void projectRedBarrelsUseRealCountsAndRespectTopBottom() {
        mrtjp.projectred.exploration.TileBarrel barrel = new mrtjp.projectred.exploration.TileBarrel();
        barrel.stored = 1000;
        barrel.stacks[1] = stack(64);
        IStorageInventory bottom = StorageInventoryProvider.getInventory(barrel, ForgeDirection.DOWN);
        assertEquals(1064, count(bottom));
        assertEquals(500, bottom.extractItem(0, 500, true).stackSize);
        assertEquals(1000, barrel.stored);
        assertEquals(500, bottom.extractItem(0, 500, false).stackSize);
        assertEquals(564, count(bottom));
        assertEquals(1, bottom.insertItem(0, stack(1), false).stackSize);
        IStorageInventory top = StorageInventoryProvider.getInventory(barrel, ForgeDirection.UP);
        assertEquals(0, count(top));
        assertNull(top.insertItem(0, stack(1), true));
        assertNull(barrel.stacks[0]);
        assertNull(top.insertItem(0, stack(1), false));
        assertEquals(1, barrel.stacks[0].stackSize);
    }

    @Test
    public void sharedEnderChestsAreCountedOnceAndDetectedByHopper() {
        StorageTestInventory contents = new StorageTestInventory();
        contents.stacks[0] = stack(64);
        codechicken.enderstorage.storage.item.TileEnderChest first = new codechicken.enderstorage.storage.item.TileEnderChest(
            contents);
        codechicken.enderstorage.storage.item.TileEnderChest second = new codechicken.enderstorage.storage.item.TileEnderChest(
            contents);
        CombinedStorageInventory inventory = new CombinedStorageInventory();
        inventory.add(storage(first));
        inventory.add(storage(second));
        assertEquals(64, count(inventory));
        assertTrue(inventory.containsInventory(first));
        assertTrue(inventory.containsInventory(second));
    }

    @Test
    public void bothPresentHalvesUseTheSameParent() {
        team.chisel.block.tileentity.TileEntityPresent parent = new team.chisel.block.tileentity.TileEntityPresent();
        team.chisel.block.tileentity.TileEntityPresent child = new team.chisel.block.tileentity.TileEntityPresent();
        child.parent = parent;
        parent.stacks[0] = stack(20);
        parent.stacks[27] = stack(30);
        assertEquals(50, count(storage(child)));
        CombinedStorageInventory inventory = new CombinedStorageInventory();
        inventory.add(storage(parent));
        inventory.add(storage(child));
        assertEquals(54, inventory.getSlots());
        assertEquals(50, count(inventory));
        assertTrue(inventory.containsInventory(child));
    }

    @Test
    public void filingCabinetsHideInputBuffersAndSimulateRealExtractionLimit() {
        com.rwtema.extrautils.tileentity.TileEntityFilingCabinet cabinet = new com.rwtema.extrautils.tileentity.TileEntityFilingCabinet();
        cabinet.itemSlots.add(stack(1000));
        cabinet.inputSlots.add(stack(30));
        IStorageInventory inventory = storage(cabinet);
        assertEquals(1000, count(inventory));
        assertNull(inventory.getStackInSlot(1));
        assertEquals(64, inventory.extractItem(0, 200, true).stackSize);
        assertEquals(1000, count(inventory));
        assertEquals(64, inventory.extractItem(0, 200, false).stackSize);
        assertEquals(936, count(inventory));
        assertNull(inventory.insertItem(2, stack(64), true));
        assertEquals(1, cabinet.inputSlots.size());
        assertNull(inventory.insertItem(2, stack(64), false));
        assertEquals(2, cabinet.inputSlots.size());
        assertEquals(936, count(inventory));
    }

    @Test
    public void emptyAndInvalidSlotOperationsRemainSafe() {
        StorageTestInventory chest = new StorageTestInventory();
        IStorageInventory inventory = storage(chest);
        assertEquals(0, count(inventory));
        assertNull(inventory.getStackInSlot(-1));
        assertNull(inventory.getStackInSlot(3));
        assertNull(inventory.extractItem(0, 0, false));
        assertNull(inventory.extractItem(0, 64, false));
        assertEquals(1, inventory.insertItem(3, stack(1), false).stackSize);
        assertNull(inventory.insertItem(0, null, false));
    }

    @Test
    public void largeContainersRespectTheirFullIntegerCapacity() {
        StorageTestInventory chest = new StorageTestInventory();
        chest.limit = Integer.MAX_VALUE;
        chest.stacks[0] = stack(Integer.MAX_VALUE - 10);
        IStorageInventory inventory = storage(chest);
        assertEquals(10, inventory.insertItem(0, stack(20), true).stackSize);
        assertEquals(Integer.MAX_VALUE - 10, chest.stacks[0].stackSize);
        assertEquals(10, inventory.insertItem(0, stack(20), false).stackSize);
        assertEquals(Integer.MAX_VALUE, chest.stacks[0].stackSize);
    }

    @Test
    public void largeContainersCanStoreBulkUnstackableItems() {
        Item unstackable = new Item().setMaxStackSize(1);
        StorageTestInventory chest = new StorageTestInventory();
        chest.limit = Integer.MAX_VALUE;
        IStorageInventory inventory = storage(chest);
        assertNull(inventory.insertItem(0, new ItemStack(unstackable, 1000), false));
        assertEquals(1000, count(inventory));
        assertEquals(1000, inventory.extractItem(0, 1000, false).stackSize);
    }

    @Test
    public void linkedJabbaBarrelsShareCountsAndHopperIdentity() {
        TileEntityBarrel first = new TileEntityBarrel();
        TileEntityBarrel second = new TileEntityBarrel();
        BulkIO contents = new BulkIO(stack(10000));
        first.io = contents;
        second.io = contents;
        first.storage = contents;
        second.storage = contents;
        CombinedStorageInventory inventory = new CombinedStorageInventory();
        inventory.add(storage(first));
        inventory.add(storage(second));
        assertEquals(10000, count(inventory));
        assertEquals(1, inventory.getSlots());
        assertTrue(inventory.containsInventory(first));
        assertTrue(inventory.containsInventory(second));
        assertEquals(500, inventory.extractItem(0, 500, false).stackSize);
        assertEquals(9500, count(storage(second)));
    }

    @Test
    public void unboundChestsKeepIndependentInventories() {
        flaxbeard.thaumicexploration.tile.TileEntityBoundChest first = new flaxbeard.thaumicexploration.tile.TileEntityBoundChest();
        flaxbeard.thaumicexploration.tile.TileEntityBoundChest second = new flaxbeard.thaumicexploration.tile.TileEntityBoundChest();
        put(0, 0, 0, first);
        put(1, 0, 0, second);
        first.stacks[0] = stack(20);
        second.stacks[0] = stack(30);
        CombinedStorageInventory inventory = new CombinedStorageInventory();
        inventory.add(storage(first));
        assertFalse(inventory.containsInventory(second));
        inventory.add(storage(second));
        assertEquals(50, count(inventory));
        assertEquals(6, inventory.getSlots());
    }

    @Test
    public void boundChestsDeduplicateOnlyWithinTheirWorldAndId() {
        flaxbeard.thaumicexploration.tile.TileEntityBoundChest first = new flaxbeard.thaumicexploration.tile.TileEntityBoundChest();
        flaxbeard.thaumicexploration.tile.TileEntityBoundChest linked = new flaxbeard.thaumicexploration.tile.TileEntityBoundChest();
        flaxbeard.thaumicexploration.tile.TileEntityBoundChest otherId = new flaxbeard.thaumicexploration.tile.TileEntityBoundChest();
        flaxbeard.thaumicexploration.tile.TileEntityBoundChest otherWorld = new flaxbeard.thaumicexploration.tile.TileEntityBoundChest();
        put(0, 0, 0, first);
        put(1, 0, 0, linked);
        put(2, 0, 0, otherId);
        otherWorld.setWorldObj(mock(World.class));
        first.id = linked.id = otherWorld.id = 1;
        otherId.id = 2;
        first.stacks[0] = stack(20);
        linked.stacks[0] = stack(20);
        otherId.stacks[0] = stack(30);
        otherWorld.stacks[0] = stack(40);
        CombinedStorageInventory inventory = new CombinedStorageInventory();
        inventory.add(storage(first));
        assertTrue(inventory.containsInventory(linked));
        assertFalse(inventory.containsInventory(otherId));
        assertFalse(inventory.containsInventory(otherWorld));
        inventory.add(storage(linked));
        inventory.add(storage(otherId));
        inventory.add(storage(otherWorld));
        assertEquals(90, count(inventory));
        assertEquals(9, inventory.getSlots());
    }

    @Test
    public void connectorThrottlesTopologyScansForTwentyTicksUnlessInvalidated() {
        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        StorageTestInventory first = new StorageTestInventory();
        first.stacks[0] = stack(10);
        StorageTestInventory second = new StorageTestInventory();
        second.stacks[0] = stack(20);
        StorageTestInventory third = new StorageTestInventory();
        third.stacks[0] = stack(40);

        put(0, 0, 0, connector);
        put(1, 0, 0, first);
        assertEquals(10, count(connector.getStorageInventory()));

        put(2, 0, 0, second);
        when(world.getTotalWorldTime()).thenReturn(105L);
        assertEquals(10, count(connector.getStorageInventory()));

        TileEntityInventoryConnector.notifyBlockChanged(world, 2, 0, 0);
        assertEquals(30, count(connector.getStorageInventory()));

        put(3, 0, 0, third);
        when(world.getTotalWorldTime()).thenReturn(124L);
        assertEquals(30, count(connector.getStorageInventory()));

        when(world.getTotalWorldTime()).thenReturn(125L);
        assertEquals(70, count(connector.getStorageInventory()));
    }

    @Test
    public void terminalsShareConnectorSnapshotAndInvalidateImmediatelyOnMutation() {
        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        StorageTestInventory chest = new StorageTestInventory(27);
        chest.stacks[0] = stack(20);
        TileEntityStorageTerminal terminal = new TileEntityStorageTerminal();
        TileEntityCraftingTerminal crafting = new TileEntityCraftingTerminal();

        put(0, 0, 0, connector);
        put(1, 0, 0, chest);
        put(0, 1, 0, terminal);
        put(0, -1, 0, crafting);
        when(world.getBlockMetadata(0, 1, 0)).thenReturn(ForgeDirection.DOWN.ordinal());
        when(world.getBlockMetadata(0, -1, 0)).thenReturn(ForgeDirection.UP.ordinal());

        List<StoredItemStack> firstSnapshot = terminal.getStacks();
        int readsAfterFirst = chest.stackReads;
        assertEquals(27, readsAfterFirst);

        when(world.getTotalWorldTime()).thenReturn(101L);
        List<StoredItemStack> secondSnapshot = crafting.getStacks();
        assertSame(firstSnapshot, secondSnapshot);
        assertEquals(readsAfterFirst, chest.stackReads);

        assertEquals(
            6,
            terminal.pullStack(new StoredItemStack(stack(1)), 6)
                .getQuantity());
        assertEquals(
            14,
            crafting.getStacks()
                .get(0)
                .getQuantity());
    }

    @Test
    public void hopperAndTerminalDoNotForceLoadUnloadedBoundaryChunks() {
        when(chunks.chunkExists(1, 0)).thenReturn(false);

        TileEntityStorageTerminal terminal = new TileEntityStorageTerminal();
        put(15, 0, 0, terminal);
        when(world.getBlockMetadata(15, 0, 0)).thenReturn(ForgeDirection.EAST.ordinal());
        assertTrue(
            terminal.getStacks()
                .isEmpty());
        verify(world, never()).getTileEntity(16, 0, 0);

        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        TileEntityInventoryHopper hopper = new TileEntityInventoryHopper();
        put(14, 1, 0, connector);
        put(15, 1, 0, hopper);
        when(world.getBlockMetadata(15, 1, 0)).thenReturn(ForgeDirection.WEST.ordinal());
        hopper.setFilter(stack(1));
        connector.getStorageInventory();
        hopper.updateEntity();
        verify(world, never()).getTileEntity(16, 1, 0);

        BlockInventoryCable cable = mock(BlockInventoryCable.class, CALLS_REAL_METHODS);
        assertFalse(cable.connectsTo(world, 15, 2, 0, ForgeDirection.EAST));
        verify(world, never()).getBlock(16, 2, 0);
        verify(world, never()).getTileEntity(16, 2, 0);
    }

    @Test
    public void exportHopperShortCircuitsFullTargetsAndBacksOffUntilNetworkMutates() {
        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        StorageTestInventory chest = new StorageTestInventory(27);
        TileEntityInventoryHopper hopper = new TileEntityInventoryHopper();
        TileEntityFurnace furnace = new TileEntityFurnace();
        TileEntityStorageTerminal terminal = new TileEntityStorageTerminal();

        put(0, 0, 0, connector);
        put(-1, 0, 0, chest);
        put(0, -1, 0, hopper);
        put(0, -2, 0, furnace);
        put(0, 1, 0, terminal);
        when(world.getBlockMetadata(anyInt(), anyInt(), anyInt())).thenReturn(ForgeDirection.DOWN.ordinal());

        hopper.setFilter(stack(1));
        connector.getStorageInventory();

        // Full target short-circuits before touching network slots.
        chest.stacks[0] = stack(10);
        furnace.setInventorySlotContents(0, stack(64));
        chest.stackReads = 0;
        hopper.updateEntity();
        assertEquals(0, chest.stackReads);

        // Missing item triggers exponential backoff (10 -> 20 -> 40), then wakes up on terminal deposit.
        furnace.setInventorySlotContents(0, null);
        chest.stacks[0] = null;
        for (int tick = 0; tick < 10; tick++) hopper.updateEntity();
        for (int tick = 0; tick < 20; tick++) hopper.updateEntity();
        chest.stackReads = 0;
        for (int tick = 0; tick < 10; tick++) hopper.updateEntity();
        assertEquals(0, chest.stackReads);

        assertNull(terminal.pushStack(stack(3)));
        for (int tick = 0; tick < 10; tick++) hopper.updateEntity();
        assertNotNull(furnace.getStackInSlot(0));
        assertEquals(1, furnace.getStackInSlot(0).stackSize);
        assertEquals(2, chest.stacks[0].stackSize);
    }

    @Test
    public void hopperRetainsConflictAcrossStaggeredScansAndDirtyRefreshes() {
        Config.maxInventoryCableNodes = 2;

        TileEntityInventoryConnector connectorA = new TileEntityInventoryConnector();
        StorageTestInventory chestA = new StorageTestInventory();
        chestA.stacks[0] = stack(10);
        TileEntityInventoryConnector connectorB = new TileEntityInventoryConnector();
        StorageTestInventory chestB = new StorageTestInventory();
        chestB.stacks[0] = stack(10);
        TileEntityInventoryHopper hopper = new TileEntityInventoryHopper();
        TileEntityFurnace furnace = new TileEntityFurnace();

        put(-2, 1, 0, connectorA);
        put(-3, 1, 0, chestA);
        put(2, 1, 0, connectorB);
        put(3, 1, 0, chestB);
        put(0, 0, 0, hopper);
        put(0, -1, 0, furnace);
        blocks.put(key(-1, 1, 0), ModRegistry.inventoryCable);
        blocks.put(key(0, 1, 0), ModRegistry.inventoryCable);
        blocks.put(key(1, 1, 0), ModRegistry.inventoryCable);
        when(world.getBlockMetadata(0, 0, 0)).thenReturn(ForgeDirection.DOWN.ordinal());

        hopper.setFilter(stack(1));
        connectorA.getStorageInventory();

        when(world.getTotalWorldTime()).thenReturn(105L);
        connectorB.getStorageInventory();
        assertFalse(connectorA.hasCableNetworkConflict());
        assertFalse(connectorB.hasCableNetworkConflict());
        assertEquals("message.tomsstorage.inventory_hopper.conflict", hopper.getStatusTranslationKey());
        assertEquals("message.tomsstorage.inventory_hopper.mode_unknown", hopper.getModeTranslationKey());

        for (int tick = 0; tick < 15; tick++) hopper.updateEntity();
        assertNull(furnace.getStackInSlot(0));
        assertEquals(10, chestA.stacks[0].stackSize);
        assertEquals(10, chestB.stacks[0].stackSize);

        when(world.getTotalWorldTime()).thenReturn(120L);
        connectorA.updateEntity();
        assertEquals("message.tomsstorage.inventory_hopper.conflict", hopper.getStatusTranslationKey());
        for (int tick = 0; tick < 15; tick++) hopper.updateEntity();
        assertNull(furnace.getStackInSlot(0));

        when(world.getTotalWorldTime()).thenReturn(123L);
        connectorB.markTopologyDirty();
        connectorB.updateEntity();
        assertEquals("message.tomsstorage.inventory_hopper.conflict", hopper.getStatusTranslationKey());
        for (int tick = 0; tick < 15; tick++) hopper.updateEntity();
        assertNull(furnace.getStackInSlot(0));

        when(world.getTotalWorldTime()).thenReturn(125L);
        connectorB.getStorageInventory();
        assertEquals("message.tomsstorage.inventory_hopper.conflict", hopper.getStatusTranslationKey());

        blocks.put(key(1, 1, 0), ordinaryBlock);
        TileEntityInventoryConnector.notifyBlockChanged(world, 1, 1, 0);
        assertEquals("message.tomsstorage.inventory_hopper.active", hopper.getStatusTranslationKey());
        for (int tick = 0; tick < 10; tick++) hopper.updateEntity();
        assertNotNull(furnace.getStackInSlot(0));
        assertEquals(1, furnace.getStackInSlot(0).stackSize);
        assertEquals(9, chestA.stacks[0].stackSize);
        assertEquals(10, chestB.stacks[0].stackSize);
    }

    @Test
    public void hopperPreservesCableConflictAcrossStaggeredAttachAndRecoversOnDisconnect() {
        TileEntityInventoryConnector connectorA1 = new TileEntityInventoryConnector();
        TileEntityInventoryConnector connectorA2 = new TileEntityInventoryConnector();
        TileEntityInventoryConnector connectorB = new TileEntityInventoryConnector();
        StorageTestInventory chestB = new StorageTestInventory();
        chestB.stacks[0] = stack(5);
        TileEntityInventoryHopper hopper = new TileEntityInventoryHopper();

        put(-1, 1, 0, connectorA1);
        put(1, 1, 0, connectorA2);
        put(0, -2, 0, connectorB);
        put(0, -3, 0, chestB);
        put(0, 0, 0, hopper);
        blocks.put(key(0, 1, 0), ModRegistry.inventoryCable);
        blocks.put(key(0, -1, 0), ModRegistry.inventoryCable);
        when(world.getBlockMetadata(0, 0, 0)).thenReturn(ForgeDirection.DOWN.ordinal());

        connectorA1.getStorageInventory();
        assertTrue(connectorA1.hasCableNetworkConflict());
        assertEquals("message.tomsstorage.inventory_hopper.conflict", hopper.getStatusTranslationKey());

        when(world.getTotalWorldTime()).thenReturn(105L);
        connectorB.getStorageInventory();
        assertFalse(connectorB.hasCableNetworkConflict());
        assertEquals("message.tomsstorage.inventory_hopper.conflict", hopper.getStatusTranslationKey());
        assertEquals("message.tomsstorage.inventory_hopper.mode_unknown", hopper.getModeTranslationKey());

        when(world.getTotalWorldTime()).thenReturn(108L);
        blocks.put(key(0, 1, 0), ordinaryBlock);
        TileEntityInventoryConnector.notifyBlockChanged(world, 0, 1, 0);
        assertEquals("message.tomsstorage.inventory_hopper.mode_import", hopper.getModeTranslationKey());
        assertEquals("message.tomsstorage.inventory_hopper.active", hopper.getStatusTranslationKey());
    }

    @Test
    public void hopperDistinguishesSameConnectorRewireFromSameScanDualSideLoop() {
        TileEntityInventoryConnector connector = new TileEntityInventoryConnector();
        StorageTestInventory chest = new StorageTestInventory();
        chest.stacks[0] = stack(10);
        TileEntityInventoryHopper hopper = new TileEntityInventoryHopper();

        put(0, 0, -1, connector);
        put(-1, 0, -1, chest);
        put(2, 0, 0, hopper);
        blocks.put(key(1, 0, -1), ModRegistry.inventoryCable);
        blocks.put(key(2, 0, -1), ModRegistry.inventoryCable);
        when(world.getBlockMetadata(2, 0, 0)).thenReturn(ForgeDirection.NORTH.ordinal());

        connector.getStorageInventory();
        assertEquals("message.tomsstorage.inventory_hopper.mode_import", hopper.getModeTranslationKey());
        assertEquals("message.tomsstorage.inventory_hopper.active", hopper.getStatusTranslationKey());

        when(world.getTotalWorldTime()).thenReturn(105L);
        blocks.put(key(0, 0, 0), ModRegistry.inventoryCable);
        blocks.put(key(0, 0, 1), ModRegistry.inventoryCable);
        blocks.put(key(1, 0, 1), ModRegistry.inventoryCable);
        blocks.put(key(2, 0, 1), ModRegistry.inventoryCable);
        TileEntityInventoryConnector.notifyBlockChanged(world, 0, 0, 0);
        connector.getStorageInventory();
        assertEquals("message.tomsstorage.inventory_hopper.conflict", hopper.getStatusTranslationKey());
        assertEquals("message.tomsstorage.inventory_hopper.mode_unknown", hopper.getModeTranslationKey());

        when(world.getTotalWorldTime()).thenReturn(110L);
        blocks.put(key(1, 0, -1), ordinaryBlock);
        TileEntityInventoryConnector.notifyBlockChanged(world, 1, 0, -1);
        connector.getStorageInventory();
        assertEquals("message.tomsstorage.inventory_hopper.mode_export", hopper.getModeTranslationKey());
        assertEquals("message.tomsstorage.inventory_hopper.filter_required", hopper.getStatusTranslationKey());

        hopper.setFilter(stack(1));
        assertEquals("message.tomsstorage.inventory_hopper.active", hopper.getStatusTranslationKey());
    }

    @Test
    public void hopperSwitchesCleanlyAcrossConnectorsEvenWhenNewNetworkScansFirst() {
        TileEntityInventoryConnector connectorA = new TileEntityInventoryConnector();
        StorageTestInventory chestA = new StorageTestInventory();
        chestA.stacks[0] = stack(5);
        TileEntityInventoryConnector connectorB = new TileEntityInventoryConnector();
        StorageTestInventory chestB = new StorageTestInventory();
        chestB.stacks[0] = stack(8);
        TileEntityInventoryHopper hopper = new TileEntityInventoryHopper();
        TileEntityFurnace furnace = new TileEntityFurnace();

        put(-2, 2, 0, connectorA);
        put(-3, 2, 0, chestA);
        put(2, 2, 0, connectorB);
        put(3, 2, 0, chestB);
        put(0, 0, 0, hopper);
        put(0, -1, 0, furnace);
        blocks.put(key(0, 1, 0), ModRegistry.inventoryCable);
        blocks.put(key(0, 2, 0), ModRegistry.inventoryCable);
        blocks.put(key(-1, 2, 0), ModRegistry.inventoryCable);
        when(world.getBlockMetadata(0, 0, 0)).thenReturn(ForgeDirection.DOWN.ordinal());

        hopper.setFilter(stack(1));
        connectorA.getStorageInventory();
        connectorB.getStorageInventory();
        hopper.updateEntity();
        assertEquals(4, chestA.stacks[0].stackSize);
        assertEquals(1, furnace.getStackInSlot(0).stackSize);

        when(world.getTotalWorldTime()).thenReturn(105L);
        blocks.put(key(-1, 2, 0), ordinaryBlock);
        TileEntityInventoryConnector.notifyBlockChanged(world, -1, 2, 0);
        blocks.put(key(1, 2, 0), ModRegistry.inventoryCable);
        TileEntityInventoryConnector.notifyBlockChanged(world, 1, 2, 0);

        // New network B scans before old network A has refreshed its dirty topology.
        connectorB.getStorageInventory();
        assertEquals("message.tomsstorage.inventory_hopper.mode_export", hopper.getModeTranslationKey());
        assertEquals("message.tomsstorage.inventory_hopper.active", hopper.getStatusTranslationKey());

        for (int tick = 0; tick < 10; tick++) hopper.updateEntity();
        assertEquals(2, furnace.getStackInSlot(0).stackSize);
        assertEquals(7, chestB.stacks[0].stackSize);
        assertEquals(4, chestA.stacks[0].stackSize);
    }
}
