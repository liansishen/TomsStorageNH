package com.hepdd.toms_storage.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.Test;

import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.inventory.StorageTestInventory.BulkIO;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

import jds.bibliocraft.tileentities.TileEntityFramedChest;

public class StorageInventoryTraversalTest {

    private final Item item = new Item();

    private ItemStack stack(int amount) {
        return new ItemStack(item, amount);
    }

    private CountingAdapter adapter(StorageTestInventory inventory) {
        return new CountingAdapter(inventory);
    }

    private CombinedStorageInventory combine(IStorageInventory... inventories) {
        CombinedStorageInventory combined = new CombinedStorageInventory();
        for (IStorageInventory inventory : inventories) combined.add(inventory);
        return combined;
    }

    @Test
    public void aggregationVisitsContainersLinearly() {
        for (int count : new int[] { 1, 10, 100 }) {
            List<StorageTestInventory> chests = new ArrayList<>();
            CombinedStorageInventory network = new CombinedStorageInventory();
            for (int index = 0; index < count; index++) {
                StorageTestInventory chest = new StorageTestInventory(27);
                for (int slot = 0; slot < 27; slot++) chest.stacks[slot] = stack(16);
                chests.add(chest);
                network.add(StorageInventoryProvider.getInventory(chest, ForgeDirection.NORTH));
            }
            List<StoredItemStack> result = StorageInventoryUtils.getStacks(network);
            assertEquals(1, result.size());
            assertEquals(
                count * 27 * 16,
                result.get(0)
                    .getQuantity());
            int reads = chests.stream()
                .mapToInt(chest -> chest.stackReads)
                .sum();
            int sizes = chests.stream()
                .mapToInt(chest -> chest.sizeQueries)
                .sum();
            assertEquals(count * 27, reads);
            assertEquals(count * 55, sizes);
            System.out.println(
                "AGGREGATE containers=" + count
                    + " slots="
                    + count * 27
                    + " stackReads="
                    + reads
                    + " sizeQueries="
                    + sizes);
        }
    }

    @Test
    public void insertMergesLaterContainersBeforeEarlierEmptySlots() {
        StorageTestInventory first = new StorageTestInventory(1);
        StorageTestInventory second = new StorageTestInventory(1);
        second.stacks[0] = stack(16);
        CombinedStorageInventory network = combine(adapter(first), adapter(second));
        ItemStack offered = stack(10);
        assertNull(StorageInventoryUtils.insertStacked(network, offered, true));
        assertNull(first.stacks[0]);
        assertEquals(16, second.stacks[0].stackSize);
        assertNull(StorageInventoryUtils.insertStacked(network, offered, false));
        assertNull(first.stacks[0]);
        assertEquals(26, second.stacks[0].stackSize);
        assertEquals(10, offered.stackSize);
    }

    @Test
    public void bothInsertPassesShareOneSlotCountPerContainer() {
        StorageTestInventory first = new StorageTestInventory(1);
        first.stacks[0] = new ItemStack(new Item(), 64);
        StorageTestInventory second = new StorageTestInventory(1);
        CountingAdapter firstView = adapter(first);
        CountingAdapter secondView = adapter(second);
        assertNull(StorageInventoryUtils.insertStacked(combine(firstView, secondView), stack(1), false));
        assertEquals(1, firstView.slotQueries);
        assertEquals(1, secondView.slotQueries);
        assertEquals(1, second.stacks[0].stackSize);
    }

    @Test
    public void successfulMergeStopsBeforeReadingLaterContainers() {
        StorageTestInventory first = new StorageTestInventory(1);
        StorageTestInventory second = new StorageTestInventory(1);
        first.stacks[0] = stack(16);
        second.stacks[0] = stack(16);
        CountingAdapter firstView = adapter(first);
        CountingAdapter secondView = adapter(second);
        assertNull(StorageInventoryUtils.insertStacked(combine(firstView, secondView), stack(1), false));
        assertEquals(1, firstView.slotQueries);
        assertEquals(0, secondView.slotQueries);
        assertEquals(0, second.stackReads);
        assertEquals(17, first.stacks[0].stackSize);
    }

    @Test
    public void partialInsertionCanVisitAppendedFilingCabinetSlots() {
        com.rwtema.extrautils.tileentity.TileEntityFilingCabinet cabinet = new com.rwtema.extrautils.tileentity.TileEntityFilingCabinet();
        IStorageInventory inventory = StorageInventoryProvider.getInventory(cabinet, ForgeDirection.NORTH);
        ItemStack offered = stack(130);
        assertEquals(66, StorageInventoryUtils.insertStacked(inventory, offered, true).stackSize);
        assertTrue(cabinet.inputSlots.isEmpty());
        assertNull(StorageInventoryUtils.insertStacked(inventory, offered, false));
        assertEquals(3, cabinet.inputSlots.size());
        assertEquals(64, cabinet.inputSlots.get(0).stackSize);
        assertEquals(64, cabinet.inputSlots.get(1).stackSize);
        assertEquals(2, cabinet.inputSlots.get(2).stackSize);
        assertEquals(130, offered.stackSize);
    }

    @Test
    public void extractionPreservesSimulationAndContainerOrder() {
        StorageTestInventory first = new StorageTestInventory(1);
        StorageTestInventory second = new StorageTestInventory(1);
        first.stacks[0] = stack(2);
        second.stacks[0] = stack(5);
        CountingAdapter firstView = adapter(first);
        CountingAdapter secondView = adapter(second);
        CombinedStorageInventory network = combine(firstView, secondView);
        assertEquals(5, StorageInventoryUtils.extractMatching(network, stack(1), 5, true).stackSize);
        assertEquals(2, first.stacks[0].stackSize);
        assertEquals(5, second.stacks[0].stackSize);
        assertEquals(1, firstView.slotQueries);
        assertEquals(1, secondView.slotQueries);
        assertEquals(5, StorageInventoryUtils.extractMatching(network, stack(1), 5, false).stackSize);
        assertNull(first.stacks[0]);
        assertEquals(2, second.stacks[0].stackSize);
    }

    @Test
    public void extractionReturnsActualContainerAmounts() {
        StorageTestInventory first = new StorageTestInventory(1);
        StorageTestInventory second = new StorageTestInventory(1);
        first.stacks[0] = stack(5);
        second.stacks[0] = stack(5);
        first.extractionLimit = second.extractionLimit = 1;
        ItemStack extracted = StorageInventoryUtils
            .extractMatching(combine(adapter(first), adapter(second)), stack(1), 5, false);
        assertEquals(2, extracted.stackSize);
        assertEquals(4, first.stacks[0].stackSize);
        assertEquals(4, second.stacks[0].stackSize);
    }

    @Test
    public void nativeAggregationUsesOneIteratorAndKeepsOutputOrdinals() {
        StorageTestInventory drawers = new StorageTestInventory(8);
        drawers.exposed = new int[] { 5, 7 };
        BulkIO io = new BulkIO(stack(10), stack(20), null, stack(30));
        IStorageInventory inventory = new CapabilityInventoryAdapter(drawers, drawers, ForgeDirection.NORTH, io, true);
        List<StoredItemStack> result = StorageInventoryUtils.getStacks(inventory);
        assertEquals(1, io.iteratorCalls);
        assertEquals(
            50,
            result.get(0)
                .getQuantity());
        drawers.output = false;
        assertTrue(
            StorageInventoryUtils.getStacks(inventory)
                .isEmpty());
        assertEquals(2, io.iteratorCalls);
    }

    @Test
    public void nativeSnapshotsKeepDistinctTagsAndStableQuantities() {
        StorageTestInventory drawers = new StorageTestInventory(4);
        ItemStack first = stack(16);
        first.setTagCompound(new NBTTagCompound());
        first.getTagCompound()
            .setInteger("variant", 1);
        ItemStack second = first.copy();
        second.stackSize = 32;
        second.getTagCompound()
            .setInteger("variant", 2);
        BulkIO io = new BulkIO(first, second);
        IStorageInventory inventory = new CapabilityInventoryAdapter(drawers, drawers, ForgeDirection.NORTH, io, true);
        List<StoredItemStack> snapshot = StorageInventoryUtils.getStacks(inventory);
        assertEquals(1, io.iteratorCalls);
        assertEquals(2, snapshot.size());
        assertEquals(
            48,
            snapshot.stream()
                .mapToLong(StoredItemStack::getQuantity)
                .sum());
        io.contents[0].stackSize = 3;
        assertEquals(
            48,
            snapshot.stream()
                .mapToLong(StoredItemStack::getQuantity)
                .sum());
        assertEquals(
            35,
            StorageInventoryUtils.getStacks(inventory)
                .stream()
                .mapToLong(StoredItemStack::getQuantity)
                .sum());
    }

    @Test
    public void slotCountsAreFreshForEachOperation() {
        StorageTestInventory chest = new StorageTestInventory(1);
        CountingAdapter inventory = adapter(chest);
        assertTrue(
            StorageInventoryUtils.getStacks(inventory)
                .isEmpty());
        chest.stacks = new ItemStack[] { null, stack(16) };
        chest.exposed = new int[] { 0, 1 };
        assertEquals(
            16,
            StorageInventoryUtils.getStacks(inventory)
                .get(0)
                .getQuantity());
        assertEquals(2, inventory.slotQueries);
    }

    @Test
    public void idleTerminalTicksPerformNoInventoryReads() {
        StorageTestInventory chest = new StorageTestInventory(27);
        AttachedTerminal terminal = new AttachedTerminal(
            StorageInventoryProvider.getInventory(chest, ForgeDirection.NORTH));
        terminal.updateEntity();
        assertEquals(0, chest.stackReads);
        assertEquals(0, chest.sizeQueries);
    }

    @Test
    public void terminalMutationsDoNotAggregateInventory() {
        StorageTestInventory chest = new StorageTestInventory(27);
        chest.stacks[0] = stack(16);
        AttachedTerminal terminal = new AttachedTerminal(
            StorageInventoryProvider.getInventory(chest, ForgeDirection.NORTH));
        assertNull(terminal.pushStack(stack(1)));
        assertEquals(2, chest.stackReads);
        chest.stackReads = 0;
        assertEquals(
            1,
            terminal.pullStack(new StoredItemStack(stack(1)), 1)
                .getQuantity());
        assertEquals(2, chest.stackReads);
        assertEquals(
            16,
            terminal.getStacks()
                .get(0)
                .getQuantity());
    }

    @Test
    public void terminalQueriesSeeExternalChangesAndPermissionsWithinTheSameTick() {
        StorageTestInventory chest = new StorageTestInventory(1);
        chest.stacks[0] = stack(4);
        AttachedTerminal terminal = new AttachedTerminal(
            StorageInventoryProvider.getInventory(chest, ForgeDirection.NORTH));
        List<StoredItemStack> first = terminal.getStacks();
        chest.stacks[0].stackSize = 7;
        assertEquals(
            7,
            terminal.getStacks()
                .get(0)
                .getQuantity());
        assertEquals(
            4,
            first.get(0)
                .getQuantity());
        chest.output = false;
        assertTrue(
            terminal.getStacks()
                .isEmpty());
        chest.output = true;
        assertEquals(
            7,
            terminal.getStacks()
                .get(0)
                .getQuantity());
        terminal.attached = new CombinedStorageInventory();
        assertTrue(
            terminal.getStacks()
                .isEmpty());
    }

    @Test
    public void largeCapabilityContainerUsesSinglePassIteratorsForInsertExtractAndStats() {
        int drawerCount = 200;
        StorageTestInventory drawers = new StorageTestInventory(drawerCount * 2);
        ItemStack[] contents = new ItemStack[drawerCount];
        contents[drawerCount - 1] = stack(100);
        BulkIO io = new BulkIO(contents);
        IStorageInventory inventory = new CapabilityInventoryAdapter(drawers, drawers, ForgeDirection.NORTH, io, true);

        io.iteratorCalls = 0;
        assertNull(StorageInventoryUtils.insertStacked(inventory, stack(50), false));
        assertEquals(150, contents[drawerCount - 1].stackSize);
        assertEquals(1, io.iteratorCalls);

        io.iteratorCalls = 0;
        ItemStack other = new ItemStack(new Item(), 25);
        assertNull(StorageInventoryUtils.insertStacked(inventory, other, false));
        assertEquals(25, contents[0].stackSize);
        assertEquals(2, io.iteratorCalls);

        io.iteratorCalls = 0;
        ItemStack extracted = StorageInventoryUtils.extractMatching(inventory, stack(1), 60, false);
        assertEquals(60, extracted.stackSize);
        assertEquals(90, contents[drawerCount - 1].stackSize);
        assertEquals(1, io.iteratorCalls);

        io.iteratorCalls = 0;
        long stats = inventory.packSlotStats();
        assertEquals(drawerCount, (int) (stats >>> 32));
        assertEquals(drawerCount - 2, (int) stats);
        assertEquals(1, io.iteratorCalls);
    }

    @Test
    public void aggregationDoesNotDeepCopyMatchingSlotTagsRepeatedly() {
        AtomicInteger chestTagCopies = new AtomicInteger();
        StorageTestInventory chest = new StorageTestInventory(100);
        for (int slot = 0; slot < 100; slot++) {
            ItemStack tagged = stack(16);
            tagged.setTagCompound(new CountingTag(chestTagCopies, 7));
            chest.stacks[slot] = tagged;
        }
        IStorageInventory chestView = StorageInventoryProvider.getInventory(chest, ForgeDirection.NORTH);
        List<StoredItemStack> chestSnapshot = StorageInventoryUtils.getStacks(chestView);
        assertEquals(1, chestSnapshot.size());
        assertEquals(
            1600L,
            chestSnapshot.get(0)
                .getQuantity());
        assertEquals(1, chestTagCopies.get());

        AtomicInteger drawerTagCopies = new AtomicInteger();
        StorageTestInventory drawers = new StorageTestInventory(200);
        ItemStack[] drawerContents = new ItemStack[100];
        for (int slot = 0; slot < 100; slot++) {
            ItemStack tagged = stack(10);
            tagged.setTagCompound(new CountingTag(drawerTagCopies, 9));
            drawerContents[slot] = tagged;
        }
        IStorageInventory drawerView = new CapabilityInventoryAdapter(
            drawers,
            drawers,
            ForgeDirection.NORTH,
            new BulkIO(drawerContents),
            true);
        List<StoredItemStack> drawerSnapshot = StorageInventoryUtils.getStacks(drawerView);
        assertEquals(1, drawerSnapshot.size());
        assertEquals(
            1000L,
            drawerSnapshot.get(0)
                .getQuantity());
        assertEquals(1, drawerTagCopies.get());
    }

    @Test
    public void combinedInventoryBinarySearchSkipsZeroSlotLockedContainers() {
        StorageTestInventory first = new StorageTestInventory(3);
        first.stacks[2] = stack(12);
        TileEntityFramedChest lockedA = new TileEntityFramedChest();
        lockedA.locked = true;
        lockedA.stacks[0] = stack(50);
        TileEntityFramedChest lockedB = new TileEntityFramedChest();
        lockedB.locked = true;
        StorageTestInventory last = new StorageTestInventory(4);
        last.stacks[0] = stack(34);

        CountingAdapter firstView = adapter(first);
        CountingAdapter lastView = adapter(last);
        CombinedStorageInventory combined = new CombinedStorageInventory(() -> 200L, null, null);
        combined.add(firstView);
        combined.add(StorageInventoryProvider.getInventory(lockedA, ForgeDirection.NORTH));
        combined.add(StorageInventoryProvider.getInventory(lockedB, ForgeDirection.NORTH));
        combined.add(lastView);

        assertEquals(7, combined.getSlots());
        assertEquals(12, combined.getStackInSlot(2).stackSize);
        assertEquals(34, combined.getStackInSlot(3).stackSize);
        assertNull(combined.getStackInSlot(7));
        assertEquals(1, firstView.slotQueries);
        assertEquals(1, lastView.slotQueries);
    }

    private static final class CountingTag extends NBTTagCompound {

        private final AtomicInteger copies;

        CountingTag(AtomicInteger copies, int variant) {
            this.copies = copies;
            setInteger("variant", variant);
        }

        @Override
        public NBTBase copy() {
            copies.incrementAndGet();
            return new CountingTag(copies, getInteger("variant"));
        }
    }

    private static class CountingAdapter extends InventoryAdapter {

        private int slotQueries;

        CountingAdapter(StorageTestInventory inventory) {
            super(inventory, ForgeDirection.NORTH);
        }

        @Override
        public int getSlots() {
            slotQueries++;
            return super.getSlots();
        }
    }

    private static class AttachedTerminal extends TileEntityStorageTerminal {

        private IStorageInventory attached;

        AttachedTerminal(IStorageInventory attached) {
            this.attached = attached;
            setWorldObj(mock(World.class));
        }

        @Override
        protected IStorageInventory getAttachedInventory() {
            return attached;
        }
    }
}
