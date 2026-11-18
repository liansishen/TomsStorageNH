package com.hepdd.toms_storage.crafting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Bootstrap;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.hepdd.toms_storage.ModNetwork;
import com.hepdd.toms_storage.StorageItemUtils;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.client.SearchMatcher;
import com.hepdd.toms_storage.client.SearchMatcher.SearchQuery;
import com.hepdd.toms_storage.client.SearchMatcher.SearchTarget;
import com.hepdd.toms_storage.crafting.CraftingInventorySnapshot.Extraction;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.inventory.CombinedStorageInventory;
import com.hepdd.toms_storage.inventory.IStorageInventory;
import com.hepdd.toms_storage.inventory.StorageInventoryProvider;
import com.hepdd.toms_storage.inventory.StorageTestInventory;
import com.hepdd.toms_storage.nei.AutoCraftPreviewInventory;
import com.hepdd.toms_storage.nei.RecipeNbtSerializer;
import com.hepdd.toms_storage.network.PacketAutoCraftRequest;
import com.hepdd.toms_storage.network.PacketAutoCraftResult;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.Side;

public class AutoCraftAndSearchOptimizationTest {

    private static Item itemLog;
    private static Item itemPlank;
    private static Item itemStick;
    private static Item itemChest;
    private static Item itemPlate;
    private static Item itemWrench;
    private static Item itemCasing;
    private static Item itemEmptyBucket;
    private static Item itemWaterBucket;
    private static Item itemWetDough;
    private static Item itemReusableMold;
    private static Item itemRepairablePick;

    private SimpleNetworkWrapper previousChannel;
    private World serverWorld;
    private EntityPlayerMP player;

    @BeforeClass
    public static void initTestItems() throws ReflectiveOperationException {
        Field sideField = FMLRelaunchLog.class.getDeclaredField("side");
        sideField.setAccessible(true);
        if (sideField.get(null) == null) {
            sideField.set(null, Side.SERVER);
        }

        Field loaderField = Loader.class.getDeclaredField("instance");
        loaderField.setAccessible(true);
        if (loaderField.get(null) == null) {
            loaderField.set(null, mock(Loader.class));
        }

        Class.forName(EnumHelper.class.getName(), true, EnumHelper.class.getClassLoader());
        Field ctorAccessor = EnumHelper.class.getDeclaredField("newConstructorAccessor");
        ctorAccessor.setAccessible(true);
        if (ctorAccessor.get(null) == null) {
            Field commonTypes = EnumHelper.class.getDeclaredField("commonTypes");
            commonTypes.setAccessible(true);
            commonTypes.set(null, new Class[0][]);
        }

        Bootstrap.func_151354_b();

        itemLog = registerTestItem(3901, "log", 64);
        itemPlank = registerTestItem(3902, "plank", 64);
        itemStick = registerTestItem(3903, "stick", 64);
        itemChest = registerTestItem(3904, "chest", 64);
        itemPlate = registerTestItem(3905, "plate", 64);
        itemWrench = registerTestItem(3906, "gt_wrench", 1);
        itemCasing = registerTestItem(3907, "casing", 64);
        itemEmptyBucket = registerTestItem(3908, "bucket_empty", 16);
        itemWaterBucket = registerTestItem(3909, "bucket_water", 1);
        itemWaterBucket.setContainerItem(itemEmptyBucket);
        itemWetDough = registerTestItem(3910, "wet_dough", 64);
        itemReusableMold = registerTestItem(3911, "reusable_mold", 1);
        itemReusableMold.setContainerItem(itemReusableMold);
        itemRepairablePick = registerTestItem(3912, "repairable_pick", 1);
        itemRepairablePick.setMaxDamage(100);
    }

    private static Item registerTestItem(int id, String name, int maxStack) throws ReflectiveOperationException {
        Item existing = Item.getItemById(id);
        if (existing != null) return existing;
        Item created = new Item().setMaxStackSize(maxStack)
            .setUnlocalizedName("test." + name);
        Method addRaw = FMLControlledNamespacedRegistry.class
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        addRaw.setAccessible(true);
        addRaw.invoke(Item.itemRegistry, id, "tomsstorage_test:" + name, created);
        return created;
    }

    @Before
    public void setUp() {
        previousChannel = ModNetwork.channel;
        ModNetwork.channel = mock(SimpleNetworkWrapper.class);
        serverWorld = mock(World.class);
        player = mock(EntityPlayerMP.class);
        player.worldObj = serverWorld;
        player.inventory = new InventoryPlayer(player);
        player.capabilities = new PlayerCapabilities();
        player.playerNetServerHandler = mock(NetHandlerPlayServer.class);
    }

    @After
    public void tearDown() {
        ServerThreadUtil.clear();
        ModNetwork.channel = previousChannel;
    }

    @Test
    public void batchCraftDeliversFinalOutputDirectlyToPlayerBypassingVoidDrawer() {
        StorageTestInventory plankChest = new StorageTestInventory(9);
        for (int i = 0; i < 8; i++) {
            plankChest.stacks[i] = new ItemStack(itemPlank, 64);
        }
        VoidSinkInventory voidDrawer = new VoidSinkInventory(itemChest);
        CombinedStorageInventory network = new CombinedStorageInventory();
        network.add(StorageInventoryProvider.getInventory(voidDrawer, ForgeDirection.NORTH));
        network.add(StorageInventoryProvider.getInventory(plankChest, ForgeDirection.NORTH));
        CountingTerminal terminal = new CountingTerminal(serverWorld, network);

        ItemStack[][] chestGrid = new ItemStack[9][];
        for (int i = 0; i < 9; i++) {
            chestGrid[i] = i == 4 ? new ItemStack[0] : new ItemStack[] { new ItemStack(itemPlank, 1) };
        }
        CraftingPattern rootPattern = new CraftingPattern(new ItemStack(itemChest, 1), chestGrid);
        CountingRecipe chestRecipe = new CountingRecipe(chestGrid, new ItemStack(itemChest, 1));

        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);
        CraftingPlanner planner = new CraftingPlanner(
            player,
            snapshot,
            Collections.emptyList(),
            false,
            Collections.singletonList(chestRecipe));
        CraftingPlanner.CraftResult planned = planner.plan(rootPattern, 64);
        assertTrue(planned.isSuccess());
        // First validation matches the recipe; remaining 63 identical crafts hit the grid signature cache.
        assertEquals(1, chestRecipe.matchCalls);

        SingleAutoCraftExecutor.CraftResult executed = SingleAutoCraftExecutor
            .executePlan(terminal, player, planned.getPlan());
        assertTrue(executed.isSuccess());

        // Final output went straight into player inventory: 0 voided items, 0 push calls, only 1 batch pull!
        assertEquals(0, voidDrawer.voidedItems);
        assertEquals(1, terminal.pullCalls);
        assertEquals(0, terminal.pushCalls);
        assertEquals(64, countInPlayerInventory(itemChest));
        assertEquals(0, countInStorage(plankChest, itemPlank));
    }

    @Test
    public void gtToolDurabilityFlowsThroughVirtualPoolInSinglePullAndSingleReturn() {
        StorageTestInventory storage = new StorageTestInventory(4);
        storage.stacks[0] = new ItemStack(itemPlate, 64);
        storage.stacks[1] = createGtWrench(0L, 1000L);
        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(storage, ForgeDirection.NORTH));

        ItemStack[][] grid = emptyGrid();
        grid[0] = new ItemStack[] { new ItemStack(itemPlate, 1) };
        grid[1] = new ItemStack[] { createGtWrench(0L, 1000L) };
        // Recipe pattern uses damage-agnostic / tagless wrench matcher or exact initial tool; test GT tool recipe:
        ItemStack[][] patternGrid = emptyGrid();
        patternGrid[0] = new ItemStack[] { new ItemStack(itemPlate, 1) };
        patternGrid[1] = new ItemStack[] { new ItemStack(itemWrench, 1) };

        CraftingPattern pattern = new CraftingPattern(new ItemStack(itemCasing, 1), patternGrid);
        IRecipe casingRecipe = new SimpleGtToolRecipe(itemPlate, itemWrench, new ItemStack(itemCasing, 1));

        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);
        CraftingPlanner planner = new CraftingPlanner(
            player,
            snapshot,
            Collections.emptyList(),
            false,
            Collections.singletonList(casingRecipe));
        CraftingPlanner.CraftResult planned = planner.plan(pattern, 64);
        assertTrue(planned.isSuccess());

        SingleAutoCraftExecutor.CraftResult executed = SingleAutoCraftExecutor
            .executePlan(terminal, player, planned.getPlan());
        assertTrue(executed.isSuccess());

        // 1 pull for 64 plates + 1 pull for the single wrench = 2 pulls total; 1 push returning Damage=64 wrench!
        assertEquals(2, terminal.pullCalls);
        assertEquals(1, terminal.pushCalls);
        assertEquals(64, countInPlayerInventory(itemCasing));

        ItemStack returnedWrench = findItemInStorage(storage, itemWrench);
        assertNotNull(returnedWrench);
        assertEquals(936L, CraftingRemainderHandler.getGTDurability(returnedWrench));
    }

    @Test
    public void fullNetworkReturnsDamagedPlayerGtToolBackToPlayerInventoryWithoutBreakingChain() {
        StorageTestInventory fullStorage = new StorageTestInventory(1);
        fullStorage.stacks[0] = new ItemStack(itemLog, 64);
        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(fullStorage, ForgeDirection.NORTH));

        player.inventory.mainInventory[0] = new ItemStack(itemPlate, 16);
        player.inventory.mainInventory[1] = createGtWrench(0L, 100L);

        ItemStack[][] patternGrid = emptyGrid();
        patternGrid[0] = new ItemStack[] { new ItemStack(itemPlate, 1) };
        patternGrid[1] = new ItemStack[] { new ItemStack(itemWrench, 1) };
        CraftingPattern pattern = new CraftingPattern(new ItemStack(itemCasing, 1), patternGrid);
        IRecipe casingRecipe = new SimpleGtToolRecipe(itemPlate, itemWrench, new ItemStack(itemCasing, 1));

        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);
        CraftingPlanner planner = new CraftingPlanner(
            player,
            snapshot,
            Collections.emptyList(),
            false,
            Collections.singletonList(casingRecipe));
        CraftingPlanner.CraftResult planned = planner.plan(pattern, 16);
        assertTrue(planned.isSuccess());

        SingleAutoCraftExecutor.CraftResult executed = SingleAutoCraftExecutor
            .executePlan(terminal, player, planned.getPlan());
        assertTrue(executed.isSuccess());
        assertEquals(16, countInPlayerInventory(itemCasing));

        ItemStack playerWrench = findItemInPlayer(itemWrench);
        assertNotNull(playerWrench);
        assertEquals(84L, CraftingRemainderHandler.getGTDurability(playerWrench));
    }

    @Test
    public void subRecipeAndBucketRemaindersFlowInMemoryAndReturnCleanly() {
        StorageTestInventory storage = new StorageTestInventory(20);
        storage.stacks[0] = new ItemStack(itemLog, 2);
        for (int i = 1; i <= 5; i++) {
            storage.stacks[i] = new ItemStack(itemWaterBucket, 1);
        }
        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(storage, ForgeDirection.NORTH));

        ItemStack[][] plankGrid = emptyGrid();
        plankGrid[0] = new ItemStack[] { new ItemStack(itemLog, 1) };
        CraftingPattern plankPattern = new CraftingPattern(new ItemStack(itemPlank, 4), plankGrid);

        ItemStack[][] doughGrid = emptyGrid();
        doughGrid[0] = new ItemStack[] { new ItemStack(itemPlank, 1) };
        doughGrid[1] = new ItemStack[] { new ItemStack(itemWaterBucket, 1) };
        CraftingPattern doughPattern = new CraftingPattern(new ItemStack(itemWetDough, 1), doughGrid);

        List<IRecipe> recipes = Arrays.asList(
            new CountingRecipe(plankGrid, new ItemStack(itemPlank, 4)),
            new CountingRecipe(doughGrid, new ItemStack(itemWetDough, 1)));

        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);
        CraftingPlanner planner = new CraftingPlanner(
            player,
            snapshot,
            Collections.singletonList(plankPattern),
            true,
            recipes);
        // 5 wet dough needs 5 planks -> 2 logs produce 8 planks -> 5 consumed, 3 excess planks + 5 empty buckets
        // returned
        CraftingPlanner.CraftResult planned = planner.plan(doughPattern, 5);
        assertTrue(planned.isSuccess());

        SingleAutoCraftExecutor.CraftResult executed = SingleAutoCraftExecutor
            .executePlan(terminal, player, planned.getPlan());
        assertTrue(executed.isSuccess());
        assertEquals(5, countInPlayerInventory(itemWetDough));
        assertEquals(3, countInStorage(storage, itemPlank));
        assertEquals(5, countInStorage(storage, itemEmptyBucket));
    }

    @Test
    public void atomicRollbackRestoresExtractedTerminalItemsWithoutMutatingPlayerOnPartialShortage() {
        StorageTestInventory storage = new StorageTestInventory(4);
        storage.stacks[0] = new ItemStack(itemPlank, 32);
        storage.stacks[1] = new ItemStack(itemStick, 32);
        player.inventory.mainInventory[0] = new ItemStack(itemPlate, 16);

        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(storage, ForgeDirection.NORTH));

        ItemStack[][] grid = emptyGrid();
        grid[0] = new ItemStack[] { new ItemStack(itemPlate, 1) };
        grid[1] = new ItemStack[] { new ItemStack(itemPlank, 1) };
        grid[2] = new ItemStack[] { new ItemStack(itemStick, 1) };
        CraftingPattern pattern = new CraftingPattern(new ItemStack(itemChest, 1), grid);

        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);
        CraftingPlanner planner = new CraftingPlanner(
            player,
            snapshot,
            Collections.emptyList(),
            false,
            Collections.singletonList(new CountingRecipe(grid, new ItemStack(itemChest, 1))));
        CraftingPlanner.CraftResult planned = planner.plan(pattern, 16);
        assertTrue(planned.isSuccess());

        // Simulate concurrent external hopper draining sticks from 32 down to 5 before execution:
        storage.stacks[1].stackSize = 5;

        SingleAutoCraftExecutor.CraftResult executed = SingleAutoCraftExecutor
            .executePlan(terminal, player, planned.getPlan());
        assertFalse(executed.isSuccess());
        assertEquals("apply_missing_terminal_item", executed.getReason());

        // Player slot must remain untouched (16 plates), and all 16 planks + 5 sticks must be restored to storage!
        assertEquals(16, player.inventory.mainInventory[0].stackSize);
        assertEquals(32, countInStorage(storage, itemPlank));
        assertEquals(5, countInStorage(storage, itemStick));
        assertEquals(0, countInPlayerInventory(itemChest));
    }

    @Test
    public void packetAutoCraftRequestSchedulesToMainThreadAndRepliesViaChannel() throws Exception {
        MinecraftServer mockServer = mock(MinecraftServer.class);
        when(mockServer.isServerStopped()).thenReturn(false);
        ServerThreadUtil.setup(mockServer, new Thread("MockDedicatedServerThread"));

        StorageTestInventory storage = new StorageTestInventory(2);
        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(storage, ForgeDirection.NORTH));
        NoRecipeCraftingContainer container = new NoRecipeCraftingContainer(player.inventory, terminal);
        container.windowId = 12;
        player.openContainer = container;

        List<PacketAutoCraftResult> sentResults = new ArrayList<>();
        doAnswer(invocation -> {
            sentResults.add(invocation.getArgument(0));
            return null;
        }).when(ModNetwork.channel)
            .sendTo(any(PacketAutoCraftResult.class), eq(player));

        // Request with mismatched windowId (99 != 12)
        NBTTagCompound tag = RecipeNbtSerializer.writeRecipe(emptyGrid(), new ItemStack(itemChest, 1), 1);
        PacketAutoCraftRequest badWindowReq = new PacketAutoCraftRequest(42, 99, tag, false, true);

        MessageContext ctx = createServerMessageContext(player);
        IMessage syncReply = new PacketAutoCraftRequest.Handler().onMessage(badWindowReq, ctx);
        assertNull(syncReply);
        // Must NOT have executed yet on the simulated Netty thread!
        assertTrue(sentResults.isEmpty());

        // Now drain the server main thread queue:
        ServerThreadUtil.runJobs();
        assertEquals(1, sentResults.size());
        assertEquals(
            42,
            sentResults.get(0)
                .getRequestId());
        assertFalse(
            sentResults.get(0)
                .isSuccess());
        assertEquals(
            "not_crafting_terminal",
            sentResults.get(0)
                .getReason());
    }

    @Test
    public void snapshotBucketIndexPerformsZeroDeepCopiesOnConstructionAndSelectsLargest() {
        AtomicInteger nbtCopies = new AtomicInteger();
        List<StoredItemStack> storedStacks = new ArrayList<>(2000);
        for (int i = 0; i < 2000; i++) {
            Item baseItem = i == 500 ? itemPlank : itemLog;
            ItemStack template = new ItemStack(baseItem, 1, i);
            CountingNBTTagCompound tag = new CountingNBTTagCompound(nbtCopies);
            tag.setInteger("idx", i);
            template.setTagCompound(tag);
            // StoredItemStack constructor copies once to create the client stack list; reset counter afterwards!
            storedStacks.add(new StoredItemStack(template, i == 500 ? 120L : (i % 50) + 1L));
        }

        List<ItemStack> playerStacks = new ArrayList<>(36);
        ItemStack playerPlankVariant = new ItemStack(itemPlank, 50, 500);
        CountingNBTTagCompound playerTag = new CountingNBTTagCompound(nbtCopies);
        playerTag.setInteger("idx", 500);
        playerPlankVariant.setTagCompound(playerTag);
        playerStacks.add(playerPlankVariant);

        nbtCopies.set(0);

        // Constructing CraftingInventorySnapshot over 2,000 NBT items must perform ZERO NBT deep copies!
        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(storedStacks, playerStacks, true);
        assertEquals(0, nbtCopies.get());

        // Variant meta=500 has 120 (terminal) + 50 (player) = 170; meta=10 has 11.
        ItemStack candSmall = new ItemStack(itemLog, 1, 10);
        NBTTagCompound smallTag = new NBTTagCompound();
        smallTag.setInteger("idx", 10);
        candSmall.setTagCompound(smallTag);

        ItemStack candLarge = new ItemStack(itemPlank, 1, 500);
        NBTTagCompound largeTag = new NBTTagCompound();
        largeTag.setInteger("idx", 500);
        candLarge.setTagCompound(largeTag);

        ItemStack[] candidates = new ItemStack[] { candSmall, candLarge };
        for (int slot = 0; slot < 9; slot++) {
            Extraction reserved = snapshot.reserveLargest(candidates);
            assertNotNull(reserved);
            assertEquals(
                itemPlank,
                reserved.getStack()
                    .getItem());
            assertEquals(
                500,
                reserved.getStack()
                    .getItemDamage());
            assertFalse(reserved.isFromPlayer());
        }
        // Only the 9 returned Extraction stacks are copied (instead of 11 * 2,000 = 22,000 copies)!
        assertEquals(9, nbtCopies.get());
    }

    @Test
    public void recipeNbtSerializerOmitsRedundantPatternsAndPrunesCandidatesPreservingSubRecipes() {
        // 150 heavy GT tool candidates in slot 0, 100 plank variants in slot 1 (where stick is sub-crafted)
        ItemStack[] heavyTools = new ItemStack[150];
        for (int i = 0; i < 150; i++) {
            ItemStack tool = createGtWrench(0L, 1000L + i);
            tool.getTagCompound()
                .setString("ExtraMaterialLore", "Material_" + i + "_LongDescriptionPaddingForSizeCheck");
            heavyTools[i] = tool;
        }
        ItemStack ownedTool = heavyTools[77].copy();

        ItemStack[] stickVariants = new ItemStack[80];
        for (int i = 0; i < 80; i++) {
            stickVariants[i] = new ItemStack(itemStick, 1, i);
        }
        ItemStack subCraftedStick = new ItemStack(itemStick, 2, 42);

        ItemStack[][] rawRoot = emptyGrid();
        rawRoot[0] = heavyTools;
        rawRoot[1] = stickVariants;

        // Transfer recipe mode: no "patterns" tag, empty slots omitted
        CraftingInventorySnapshot localSnapshot = new CraftingInventorySnapshot(
            Collections.singletonList(new StoredItemStack(ownedTool, 1L)),
            Collections.emptyList(),
            true);
        ItemStack[][] prunedForFill = RecipeNbtSerializer.pruneIngredients(rawRoot, localSnapshot, null, 8);
        assertEquals(1, prunedForFill[0].length);
        assertEquals(1, prunedForFill[1].length);

        NBTTagCompound fillTag = RecipeNbtSerializer.writeRecipe(prunedForFill, null, 64);
        assertFalse(fillTag.hasKey("patterns"));
        assertEquals(
            2,
            fillTag.getTagList("ingredients", 10)
                .tagCount());

        // Autocraft mode with reachable vs unreachable history patterns
        ItemStack[][] stickSubGrid = emptyGrid();
        stickSubGrid[0] = new ItemStack[] { new ItemStack(itemPlank, 1) };
        NBTTagCompound reachableSubTag = RecipeNbtSerializer.writeRecipe(stickSubGrid, subCraftedStick, 1);

        ItemStack[][] unrelatedGrid = emptyGrid();
        unrelatedGrid[0] = new ItemStack[] { new ItemStack(itemLog, 1) };
        NBTTagCompound unrelatedSubTag = RecipeNbtSerializer.writeRecipe(unrelatedGrid, new ItemStack(itemChest, 1), 1);

        List<NBTTagCompound> filteredSubs = RecipeNbtSerializer
            .filterReachablePatterns(rawRoot, Arrays.asList(reachableSubTag, unrelatedSubTag));
        assertEquals(1, filteredSubs.size());
        assertEquals(
            42,
            RecipeNbtSerializer.readOutput(filteredSubs.get(0))
                .getItemDamage());

        ItemStack[][] prunedForCraft = RecipeNbtSerializer
            .pruneIngredients(rawRoot, localSnapshot, Collections.singletonList(subCraftedStick), 8);
        assertEquals(1, prunedForCraft[0].length);
        // Sub-crafted stick (meta=42) must be preserved even though local inventory has 0 sticks!
        assertEquals(1, prunedForCraft[1].length);
        assertEquals(42, prunedForCraft[1][0].getItemDamage());

        NBTTagCompound craftTag = RecipeNbtSerializer.writeRecipe(prunedForCraft, new ItemStack(itemCasing, 1), 16);
        RecipeNbtSerializer.setPatterns(craftTag, filteredSubs);
        // Root recipe is NOT duplicated inside "patterns"
        assertEquals(
            1,
            RecipeNbtSerializer.readPatterns(craftTag)
                .size());
        assertTrue(RecipeNbtSerializer.estimateCompressedSize(craftTag) < 2_000);
    }

    @Test
    public void autoCraftPreviewSupportsFailureClearAndTenSecondTimeoutAndZeroCopyViews() {
        AutoCraftPreviewInventory preview = new AutoCraftPreviewInventory();
        List<StoredItemStack> baseStacks = Collections
            .singletonList(new StoredItemStack(new ItemStack(itemPlank, 1), 20L));

        ItemStack[][] grid = emptyGrid();
        grid[0] = new ItemStack[] { new ItemStack(itemPlank, 1) };
        assertTrue(preview.applyRequest(5, baseStacks, player.inventory, grid, new ItemStack(itemStick, 4), 2));
        assertTrue(preview.isActive());
        assertEquals(5, preview.getActiveRequestId());
        assertEquals(
            2,
            preview.getStacksView(baseStacks)
                .size());

        // Failure on an earlier or matching step (e.g. req #3 <= active #5) immediately clears stale preview
        preview.clearOnFailure(3);
        assertFalse(preview.isActive());

        // Re-apply and verify 10-second timeout auto-expiry
        assertTrue(preview.applyRequest(6, baseStacks, player.inventory, grid, new ItemStack(itemStick, 4), 1));
        assertTrue(preview.isActive());
        preview.expireIfStale(System.currentTimeMillis() + 10_500L);
        assertFalse(preview.isActive());
    }

    @Test
    public void searchQuerySupportsModAndTooltipPrefixesWithNameShortCircuit() {
        AtomicInteger tooltipCalls = new AtomicInteger();
        SupplierCounter modSupplier = new SupplierCounter(Arrays.asList("gregtech", "GregTech", "gt.metatool.01"));
        SupplierCounter tooltipSupplier = new SupplierCounter(
            Arrays.asList("Electric Wrench", "Voltage: HV", "Durability: 1000/1000"));

        // 1. Name match MUST short-circuit without invoking tooltipSupplier!
        SearchQuery nameQuery = SearchMatcher.parseSearchQuery("wrench");
        assertEquals(SearchTarget.NAME_AND_TOOLTIP, nameQuery.target);
        assertTrue(SearchMatcher.matchesSearch("Electric Wrench", modSupplier::get, () -> {
            tooltipCalls.incrementAndGet();
            return tooltipSupplier.get();
        }, nameQuery));
        assertEquals(0, tooltipCalls.get());
        assertEquals(0, modSupplier.calls);

        // 2. @mod prefix searches only modTexts without invoking tooltipSupplier!
        SearchQuery modQuery = SearchMatcher.parseSearchQuery("@greg");
        assertEquals(SearchTarget.MOD_ONLY, modQuery.target);
        assertTrue(SearchMatcher.matchesSearch("Electric Wrench", modSupplier::get, () -> {
            tooltipCalls.incrementAndGet();
            return tooltipSupplier.get();
        }, modQuery));
        assertEquals(0, tooltipCalls.get());
        assertEquals(1, modSupplier.calls);

        // 3. #tooltip prefix skips displayName and matches tooltip lines!
        SearchQuery tipOnlyQuery = SearchMatcher.parseSearchQuery("#voltage: hv");
        assertEquals(SearchTarget.TOOLTIP_ONLY, tipOnlyQuery.target);
        assertFalse(
            SearchMatcher.matchesSearch(
                "Voltage: HV",
                modSupplier::get,
                () -> Collections.singletonList("No match here"),
                tipOnlyQuery));
        assertTrue(SearchMatcher.matchesSearch("Unrelated Name", modSupplier::get, tooltipSupplier::get, tipOnlyQuery));
        assertEquals(1, tooltipSupplier.calls);

        // 4. Unsafe / malformed regexes degrade safely without throwing
        assertTrue(SearchMatcher.matchesName("Item (a+)+ test", "(a+)+", SearchMatcher.compilePattern("(a+)+")));
        assertTrue(SearchMatcher.matchesName("Broken [bracket", "[bracket", SearchMatcher.compilePattern("[bracket")));
        assertEquals("9999", SearchMatcher.formatNumber(9999L));
        assertEquals("10K", SearchMatcher.formatNumber(10_000L));
    }

    @Test
    public void pruneIngredientsPreservesNineDistinctSingleVariantsAcrossAllModesAndFallbacks() {
        ItemStack[] allCandidates = new ItemStack[25];
        for (int i = 0; i < allCandidates.length; i++) {
            allCandidates[i] = new ItemStack(itemPlank, 1, i);
        }
        ItemStack[][] rawNineSlotGrid = new ItemStack[RecipeNbtSerializer.GRID_SIZE][];
        for (int slot = 0; slot < RecipeNbtSerializer.GRID_SIZE; slot++) {
            rawNineSlotGrid[slot] = allCandidates;
        }

        StorageTestInventory storage = new StorageTestInventory(9);
        for (int i = 0; i < 7; i++) {
            storage.stacks[i] = new ItemStack(itemPlank, 1, i);
        }
        player.inventory.mainInventory[0] = new ItemStack(itemPlank, 1, 7);
        player.inventory.mainInventory[1] = new ItemStack(itemPlank, 1, 8);

        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(storage, ForgeDirection.NORTH));
        CraftingInventorySnapshot localSnapshot = new CraftingInventorySnapshot(terminal, player, true);

        // 1. Fill mode across default (unbounded local), fallback=2, and extreme fallback=0
        for (int cap : new int[] { Integer.MAX_VALUE, 2, 0 }) {
            ItemStack[][] prunedFill = RecipeNbtSerializer
                .pruneIngredients(rawNineSlotGrid, localSnapshot, null, 1, true, cap);
            if (cap == Integer.MAX_VALUE) {
                assertEquals(9, prunedFill[8].length);
            } else if (cap == 0) {
                assertEquals(1, prunedFill[8].length);
            }
            CraftingInventorySnapshot verifyFill = localSnapshot.copy();
            for (int slot = 0; slot < RecipeNbtSerializer.GRID_SIZE; slot++) {
                assertNotNull(
                    "Slot " + slot + " must fill for cap=" + cap,
                    verifyFill.reserveLargest(prunedFill[slot]));
            }
        }

        // Source snapshot must remain completely unmutated after repeated pruning simulations
        for (int i = 0; i < 9; i++) {
            assertEquals(1L, localSnapshot.getAvailableAmount(new ItemStack(itemPlank, 1, i)));
        }

        // Verify real server-side ContainerCraftingTerminal.fillRecipe fills all 9 slots without leaving slot 8 empty
        ItemStack[][] prunedForContainer = RecipeNbtSerializer
            .pruneIngredients(rawNineSlotGrid, localSnapshot, null, 1, true, 0);
        NoRecipeCraftingContainer container = new NoRecipeCraftingContainer(player.inventory, terminal);
        container.fillRecipe(player, prunedForContainer, 1);
        for (int slot = 0; slot < RecipeNbtSerializer.GRID_SIZE; slot++) {
            assertNotNull(container.craftMatrix.getStackInSlot(slot));
        }
        container.clearGrid();

        CraftingInventorySnapshot craftSnapshot = new CraftingInventorySnapshot(terminal, player, true);
        ItemStack[][] wildcardGrid = new ItemStack[RecipeNbtSerializer.GRID_SIZE][];
        for (int slot = 0; slot < RecipeNbtSerializer.GRID_SIZE; slot++) {
            wildcardGrid[slot] = new ItemStack[] { new ItemStack(itemPlank, 1, OreDictionary.WILDCARD_VALUE) };
        }
        IRecipe ninePlankRecipe = new CountingRecipe(wildcardGrid, new ItemStack(itemChest, 1));

        // 2. Autocraft mode across default (unbounded), legacy 4-arg (cap=8), fallback=2, and extreme fallback=0
        ItemStack[][] legacyPruned = RecipeNbtSerializer.pruneIngredients(rawNineSlotGrid, craftSnapshot, null, 8);
        List<ItemStack[][]> craftVariants = Arrays.asList(
            RecipeNbtSerializer.pruneIngredients(rawNineSlotGrid, craftSnapshot, null, 1, false, Integer.MAX_VALUE),
            legacyPruned,
            RecipeNbtSerializer.pruneIngredients(rawNineSlotGrid, craftSnapshot, null, 1, false, 2),
            RecipeNbtSerializer.pruneIngredients(rawNineSlotGrid, craftSnapshot, null, 1, false, 0));

        for (ItemStack[][] prunedCraft : craftVariants) {
            AutoCraftPreviewInventory preview = new AutoCraftPreviewInventory();
            assertTrue(
                preview.applyRequest(
                    1,
                    terminal.getStacks(),
                    player.inventory,
                    prunedCraft,
                    new ItemStack(itemChest, 1),
                    1));

            CraftingPlanner planner = new CraftingPlanner(
                player,
                craftSnapshot.copy(),
                Collections.emptyList(),
                false,
                Collections.singletonList(ninePlankRecipe));
            CraftingPlanner.CraftResult planned = planner
                .plan(new CraftingPattern(new ItemStack(itemChest, 1), prunedCraft), 1);
            assertTrue(planned.isSuccess());
        }
    }

    @Test
    public void batchAutoCraftAcrossFifteenPlankVariantsSucceedsWhilePruningZeroStockNoise() {
        ItemStack[] hundredPlankCandidates = new ItemStack[100];
        for (int i = 0; i < hundredPlankCandidates.length; i++) {
            hundredPlankCandidates[i] = new ItemStack(itemPlank, 1, i);
        }
        ItemStack[][] rawChestGrid = emptyGrid();
        ItemStack[][] wildcardChestGrid = emptyGrid();
        for (int slot = 0; slot < RecipeNbtSerializer.GRID_SIZE; slot++) {
            if (slot != 4) {
                rawChestGrid[slot] = hundredPlankCandidates;
                wildcardChestGrid[slot] = new ItemStack[] { new ItemStack(itemPlank, 1, OreDictionary.WILDCARD_VALUE) };
            }
        }

        // 15 owned variants * 40 planks = 600 planks (>= 512 needed for 64 chests; top 8 alone only had 320 < 512!)
        StorageTestInventory storage = new StorageTestInventory(20);
        for (int i = 0; i < 15; i++) {
            storage.stacks[i] = new ItemStack(itemPlank, 40, 10 + i);
        }
        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(storage, ForgeDirection.NORTH));
        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);

        ItemStack[][] prunedDefault = RecipeNbtSerializer
            .pruneIngredients(rawChestGrid, snapshot, null, 64, false, Integer.MAX_VALUE);
        // All 15 owned variants kept; all 85 zero-stock noise variants pruned!
        assertEquals(15, prunedDefault[0].length);
        assertEquals(0, prunedDefault[4].length);

        AutoCraftPreviewInventory preview = new AutoCraftPreviewInventory();
        assertTrue(
            preview.applyRequest(
                10,
                terminal.getStacks(),
                player.inventory,
                prunedDefault,
                new ItemStack(itemChest, 1),
                64));

        // Extreme packet-size fallback (cap=0) also retains all simulated variants needed across 64 crafts
        ItemStack[][] prunedTight = RecipeNbtSerializer.pruneIngredients(rawChestGrid, snapshot, null, 64, false, 0);
        IRecipe chestRecipe = new CountingRecipe(wildcardChestGrid, new ItemStack(itemChest, 1));
        CraftingPlanner tightPlanner = new CraftingPlanner(
            player,
            snapshot.copy(),
            Collections.emptyList(),
            false,
            Collections.singletonList(chestRecipe));
        assertTrue(
            tightPlanner.plan(new CraftingPattern(new ItemStack(itemChest, 1), prunedTight), 64)
                .isSuccess());

        // Execute full 64-chest batch craft end-to-end
        CraftingPlanner planner = new CraftingPlanner(
            player,
            snapshot,
            Collections.emptyList(),
            false,
            Collections.singletonList(chestRecipe));
        CraftingPlanner.CraftResult planned = planner
            .plan(new CraftingPattern(new ItemStack(itemChest, 1), prunedDefault), 64);
        assertTrue(planned.isSuccess());

        SingleAutoCraftExecutor.CraftResult executed = SingleAutoCraftExecutor
            .executePlan(terminal, player, planned.getPlan());
        assertTrue(executed.isSuccess());
        assertEquals(64, countInPlayerInventory(itemChest));
        assertEquals(88, countInStorage(storage, itemPlank));
    }

    @Test
    public void plannerRespectsRecipeListPriorityWhenBroadSubRecipePrecedesSpecificRootRecipe() {
        StorageTestInventory storage = new StorageTestInventory(6);
        storage.stacks[0] = new ItemStack(itemPlank, 16, 0);
        storage.stacks[1] = new ItemStack(itemPlank, 16, 1);

        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(storage, ForgeDirection.NORTH));

        ItemStack[][] oakChestGrid = emptyGrid();
        ItemStack[][] spruceChestGrid = emptyGrid();
        ItemStack[][] wildcardPlankGrid = emptyGrid();
        for (int slot = 0; slot < RecipeNbtSerializer.GRID_SIZE; slot++) {
            if (slot != 4) {
                oakChestGrid[slot] = new ItemStack[] { new ItemStack(itemPlank, 1, 0) };
                spruceChestGrid[slot] = new ItemStack[] { new ItemStack(itemPlank, 1, 1) };
                wildcardPlankGrid[slot] = new ItemStack[] { new ItemStack(itemPlank, 1, OreDictionary.WILDCARD_VALUE) };
            }
        }

        ItemStack[][] rootCombineGrid = emptyGrid();
        rootCombineGrid[0] = new ItemStack[] { new ItemStack(itemChest, 1) };
        rootCombineGrid[1] = new ItemStack[] { new ItemStack(itemCasing, 1) };

        // High-priority specific recipe (index 0) MUST beat low-priority wildcard recipe (index 1) on pure spruce grid!
        CountingRecipe specificSpruceChestRecipe = new CountingRecipe(spruceChestGrid, new ItemStack(itemCasing, 1));
        CountingRecipe broadVanillaChestRecipe = new CountingRecipe(wildcardPlankGrid, new ItemStack(itemChest, 1));
        CountingRecipe rootCombineRecipe = new CountingRecipe(rootCombineGrid, new ItemStack(itemWetDough, 1));
        List<IRecipe> orderedRecipes = Arrays
            .asList(specificSpruceChestRecipe, broadVanillaChestRecipe, rootCombineRecipe);

        List<CraftingPattern> subPatterns = Arrays.asList(
            new CraftingPattern(new ItemStack(itemChest, 1), oakChestGrid),
            new CraftingPattern(new ItemStack(itemCasing, 1), spruceChestGrid));
        CraftingPattern rootPattern = new CraftingPattern(new ItemStack(itemWetDough, 1), rootCombineGrid);

        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);
        CraftingPlanner planner = new CraftingPlanner(player, snapshot, subPatterns, true, orderedRecipes);
        CraftingPlanner.CraftResult planned = planner.plan(rootPattern, 2);
        assertTrue(planned.isSuccess());

        // Round 1 validates oak chest (misses idx 0, hits idx 1), spruce chest (hits idx 0), root (hits idx 2);
        // Round 2 hits GridSignature cache for all 3 operations (0 extra recipe match calls!).
        assertEquals(3, specificSpruceChestRecipe.matchCalls);
        assertEquals(2, broadVanillaChestRecipe.matchCalls);
        assertEquals(1, rootCombineRecipe.matchCalls);

        SingleAutoCraftExecutor.CraftResult executed = SingleAutoCraftExecutor
            .executePlan(terminal, player, planned.getPlan());
        assertTrue(executed.isSuccess());
        assertEquals(2, countInPlayerInventory(itemWetDough));
    }

    @Test
    public void pruneAndPreviewDeferRemaindersUntilRoundEndPreservingFallbackAlternatives() {
        StorageTestInventory storage = new StorageTestInventory(6);
        storage.stacks[0] = new ItemStack(itemReusableMold, 1);
        storage.stacks[1] = new ItemStack(itemPlate, 1, 1);
        storage.stacks[2] = new ItemStack(itemPlate, 1, 2);

        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(storage, ForgeDirection.NORTH));
        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);

        ItemStack[] slot0Candidates = new ItemStack[21];
        slot0Candidates[0] = new ItemStack(itemReusableMold, 1);
        for (int i = 1; i < slot0Candidates.length; i++) {
            slot0Candidates[i] = new ItemStack(itemStick, 1, 100 + i);
        }

        // Slot 1 prefers self-returning mold first, then fallback plates (only meta=1 and meta=2 exist in stock)
        ItemStack[] slot1Candidates = new ItemStack[21];
        slot1Candidates[0] = new ItemStack(itemReusableMold, 1);
        for (int i = 0; i < 20; i++) {
            slot1Candidates[i + 1] = new ItemStack(itemPlate, 1, i);
        }

        ItemStack[][] rawGrid = emptyGrid();
        rawGrid[0] = slot0Candidates;
        rawGrid[1] = slot1Candidates;

        // Single-round extreme fallback (cap=0) must NOT reuse slot 0's remainder in slot 1 and drop plate(meta=1)
        ItemStack[][] prunedSingle = RecipeNbtSerializer.pruneIngredients(rawGrid, snapshot, null, 1, false, 0);
        assertEquals(1, prunedSingle[0].length);
        assertEquals(itemReusableMold, prunedSingle[0][0].getItem());
        assertEquals(1, prunedSingle[1].length);
        assertEquals(itemPlate, prunedSingle[1][0].getItem());
        assertEquals(1, prunedSingle[1][0].getItemDamage());

        // Multi-round extreme fallback (count=2, cap=0) reuses mold across rounds while preserving both plate variants
        ItemStack[][] prunedMulti = RecipeNbtSerializer.pruneIngredients(rawGrid, snapshot, null, 2, false, 0);
        assertEquals(1, prunedMulti[0].length);
        assertEquals(2, prunedMulti[1].length);
        assertEquals(1, prunedMulti[1][0].getItemDamage());
        assertEquals(2, prunedMulti[1][1].getItemDamage());

        // Preview on unpruned rawGrid must consume both fallback plates rather than reusing slot 0's mold in-round
        AutoCraftPreviewInventory preview = new AutoCraftPreviewInventory();
        assertTrue(
            preview.applyRequest(7, terminal.getStacks(), player.inventory, rawGrid, new ItemStack(itemCasing, 1), 2));
        List<StoredItemStack> previewRemaining = preview.getStacks(terminal.getStacks());
        assertEquals(1L, countStoredByItem(previewRemaining, itemReusableMold));
        assertEquals(0L, countStoredByItem(previewRemaining, itemPlate));
        assertEquals(2L, countStoredByItem(previewRemaining, itemCasing));

        // Server planner + executor succeed end-to-end on the tightly pruned (cap=0) pattern
        IRecipe moldRecipe = new MoldAndFallbackRecipe(itemReusableMold, itemPlate, new ItemStack(itemCasing, 1));
        CraftingPlanner planner = new CraftingPlanner(
            player,
            snapshot,
            Collections.emptyList(),
            false,
            Collections.singletonList(moldRecipe));
        CraftingPlanner.CraftResult planned = planner
            .plan(new CraftingPattern(new ItemStack(itemCasing, 1), prunedMulti), 2);
        assertTrue(planned.isSuccess());

        SingleAutoCraftExecutor.CraftResult executed = SingleAutoCraftExecutor
            .executePlan(terminal, player, planned.getPlan());
        assertTrue(executed.isSuccess());
        assertEquals(2, countInPlayerInventory(itemCasing));
        assertEquals(1, countInStorage(storage, itemReusableMold));
        assertEquals(0, countInStorage(storage, itemPlate));
    }

    @Test
    public void previewAndPlannerBothRejectSingleSelfReturningItemAcrossTwoSlotsInSameRound() {
        // Case 1: 1 self-returning mold cannot simultaneously occupy slot 0 and slot 1
        StorageTestInventory moldStorage = new StorageTestInventory(2);
        moldStorage.stacks[0] = new ItemStack(itemReusableMold, 1);
        CountingTerminal moldTerminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(moldStorage, ForgeDirection.NORTH));

        ItemStack[][] doubleMoldGrid = emptyGrid();
        doubleMoldGrid[0] = new ItemStack[] { new ItemStack(itemReusableMold, 1) };
        doubleMoldGrid[1] = new ItemStack[] { new ItemStack(itemReusableMold, 1) };

        AutoCraftPreviewInventory moldPreview = new AutoCraftPreviewInventory();
        assertFalse(
            moldPreview.applyRequest(
                1,
                moldTerminal.getStacks(),
                player.inventory,
                doubleMoldGrid,
                new ItemStack(itemCasing, 1),
                1));
        assertFalse(moldPreview.isActive());

        CraftingPlanner moldPlanner = new CraftingPlanner(
            player,
            new CraftingInventorySnapshot(moldTerminal, player, true),
            Collections.emptyList(),
            false,
            Collections
                .singletonList(new MoldAndFallbackRecipe(itemReusableMold, itemPlate, new ItemStack(itemCasing, 1))));
        CraftingPlanner.CraftResult moldPlanned = moldPlanner
            .plan(new CraftingPattern(new ItemStack(itemCasing, 1), doubleMoldGrid), 1);
        assertFalse(moldPlanned.isSuccess());
        assertEquals("missing_ingredients", moldPlanned.getReason());

        // Case 2: 1 GT wrench (whose damaged remainder still matches tagless wrench template) cannot fill two slots
        StorageTestInventory wrenchStorage = new StorageTestInventory(2);
        wrenchStorage.stacks[0] = createGtWrench(0L, 100L);
        CountingTerminal wrenchTerminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(wrenchStorage, ForgeDirection.NORTH));

        ItemStack[][] doubleWrenchGrid = emptyGrid();
        doubleWrenchGrid[0] = new ItemStack[] { new ItemStack(itemWrench, 1) };
        doubleWrenchGrid[1] = new ItemStack[] { new ItemStack(itemWrench, 1) };

        AutoCraftPreviewInventory wrenchPreview = new AutoCraftPreviewInventory();
        assertFalse(
            wrenchPreview.applyRequest(
                2,
                wrenchTerminal.getStacks(),
                player.inventory,
                doubleWrenchGrid,
                new ItemStack(itemCasing, 1),
                1));
        assertFalse(wrenchPreview.isActive());

        CraftingPlanner wrenchPlanner = new CraftingPlanner(
            player,
            new CraftingInventorySnapshot(wrenchTerminal, player, true),
            Collections.emptyList(),
            false,
            Collections.singletonList(new CountingRecipe(doubleWrenchGrid, new ItemStack(itemCasing, 1))));
        CraftingPlanner.CraftResult wrenchPlanned = wrenchPlanner
            .plan(new CraftingPattern(new ItemStack(itemCasing, 1), doubleWrenchGrid), 1);
        assertFalse(wrenchPlanned.isSuccess());
        assertEquals("missing_ingredients", wrenchPlanned.getReason());
    }

    @Test
    public void gridSignatureIncludesPerSlotStackSizeAcrossCustomAndVanillaRepairRecipes() {
        // 1. Same plan with 1x-plate sub-recipe and 2x-plate sub-recipe must not collide in GridSignature cache
        StorageTestInventory plateStorage = new StorageTestInventory(6);
        for (int i = 0; i < 4; i++) {
            plateStorage.stacks[i] = new ItemStack(itemPlate, 64);
        }
        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(plateStorage, ForgeDirection.NORTH));

        ItemStack[][] onePlateGrid = emptyGrid();
        onePlateGrid[0] = new ItemStack[] { new ItemStack(itemPlate, 1) };
        ItemStack[][] twoPlateGrid = emptyGrid();
        twoPlateGrid[0] = new ItemStack[] { new ItemStack(itemPlate, 2) };
        ItemStack[][] combineGrid = emptyGrid();
        combineGrid[0] = new ItemStack[] { new ItemStack(itemStick, 1) };
        combineGrid[1] = new ItemStack[] { new ItemStack(itemCasing, 1) };

        ExactCountSingleSlotRecipe onePlateRecipe = new ExactCountSingleSlotRecipe(
            itemPlate,
            1,
            new ItemStack(itemStick, 1));
        ExactCountSingleSlotRecipe twoPlateRecipe = new ExactCountSingleSlotRecipe(
            itemPlate,
            2,
            new ItemStack(itemCasing, 1));
        CountingRecipe combineRecipe = new CountingRecipe(combineGrid, new ItemStack(itemChest, 1));

        List<CraftingPattern> subPatterns = Arrays.asList(
            new CraftingPattern(new ItemStack(itemStick, 1), onePlateGrid),
            new CraftingPattern(new ItemStack(itemCasing, 1), twoPlateGrid));
        CraftingPattern rootPattern = new CraftingPattern(new ItemStack(itemChest, 1), combineGrid);

        CraftingPlanner batchPlanner = new CraftingPlanner(
            player,
            new CraftingInventorySnapshot(terminal, player, true),
            subPatterns,
            true,
            Arrays.asList(onePlateRecipe, twoPlateRecipe, combineRecipe));
        CraftingPlanner.CraftResult batchPlanned = batchPlanner.plan(rootPattern, 64);
        assertTrue(batchPlanned.isSuccess());

        // Round 1 validates 1x-plate (hits idx 0), 2x-plate (misses idx 0, hits idx 1), combine (misses 0/1, hits 2);
        // Rounds 2..64 hit GridSignature cache with zero extra recipe matches.
        assertEquals(3, onePlateRecipe.matchCalls);
        assertEquals(2, twoPlateRecipe.matchCalls);
        assertEquals(1, combineRecipe.matchCalls);

        SingleAutoCraftExecutor.CraftResult batchExecuted = SingleAutoCraftExecutor
            .executePlan(terminal, player, batchPlanned.getPlan());
        assertTrue(batchExecuted.isSuccess());
        assertEquals(64, countInPlayerInventory(itemChest));
        assertEquals(64, countInStorage(plateStorage, itemPlate));

        // 2. Real vanilla CraftingManager item-repair checks stackSize == 1 on both damaged tools
        StorageTestInventory pickStorage = new StorageTestInventory(6);
        for (int i = 0; i < 6; i++) {
            pickStorage.stacks[i] = new ItemStack(itemRepairablePick, 1, 40);
        }
        CountingTerminal pickTerminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(pickStorage, ForgeDirection.NORTH));

        ItemStack[][] validRepairGrid = emptyGrid();
        validRepairGrid[0] = new ItemStack[] { new ItemStack(itemRepairablePick, 1, 40) };
        validRepairGrid[1] = new ItemStack[] { new ItemStack(itemRepairablePick, 1, 40) };
        CraftingPattern validRepairPattern = new CraftingPattern(
            new ItemStack(itemRepairablePick, 1, 0),
            validRepairGrid);

        ItemStack[][] invalidStackedRepairGrid = emptyGrid();
        invalidStackedRepairGrid[0] = new ItemStack[] { new ItemStack(itemRepairablePick, 2, 40) };
        invalidStackedRepairGrid[1] = new ItemStack[] { new ItemStack(itemRepairablePick, 1, 40) };
        CraftingPattern invalidStackedRepairPattern = new CraftingPattern(
            new ItemStack(itemRepairablePick, 1, 0),
            invalidStackedRepairGrid);

        CraftingPlanner vanillaPlanner = new CraftingPlanner(
            player,
            new CraftingInventorySnapshot(pickTerminal, player, true),
            Collections.emptyList(),
            false);
        assertTrue(
            vanillaPlanner.plan(validRepairPattern, 1)
                .isSuccess());

        CraftingPlanner.CraftResult invalidRepairResult = vanillaPlanner.plan(invalidStackedRepairPattern, 1);
        assertFalse(invalidRepairResult.isSuccess());
        assertEquals("recipe_mismatch", invalidRepairResult.getReason());
    }

    @Test
    public void incompleteSimulationRoundStopsWithoutBreakingToolsOrSpawningGhostRemainders() {
        // 1a. Slot 1 only has stock for 1 round (no sub-recipe): round 2 must abort before recording spare GT wrench
        ItemStack wrenchLastHit = createGtWrench(999L, 1000L);
        ItemStack wrenchSpare = createGtWrench(0L, 2000L);

        StorageTestInventory toolStorage = new StorageTestInventory(4);
        toolStorage.stacks[0] = wrenchLastHit.copy();
        toolStorage.stacks[1] = wrenchSpare.copy();
        toolStorage.stacks[2] = new ItemStack(itemPlate, 1, 0);

        CountingTerminal toolTerminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(toolStorage, ForgeDirection.NORTH));
        CraftingInventorySnapshot toolSnapshot = new CraftingInventorySnapshot(toolTerminal, player, true);

        ItemStack[][] toolPlateGrid = emptyGrid();
        toolPlateGrid[0] = new ItemStack[] { wrenchLastHit.copy(), wrenchSpare.copy(), createGtWrench(0L, 3000L) };
        toolPlateGrid[1] = new ItemStack[] { new ItemStack(itemPlate, 1, 0), new ItemStack(itemPlate, 1, 1) };

        ItemStack[][] prunedNoSub = RecipeNbtSerializer
            .pruneIngredients(toolPlateGrid, toolSnapshot, null, 5, false, 0);
        assertEquals(1, prunedNoSub[0].length);
        assertEquals(1L, CraftingRemainderHandler.getGTDurability(prunedNoSub[0][0]));
        assertEquals(1, prunedNoSub[1].length);

        // 1b. Uncompleted round 2 must NOT return ghost empty bucket or record round-2 candidates into other slots
        StorageTestInventory bucketStorage = new StorageTestInventory(6);
        bucketStorage.stacks[0] = new ItemStack(itemWaterBucket, 1);
        bucketStorage.stacks[1] = new ItemStack(itemWaterBucket, 1);
        bucketStorage.stacks[2] = new ItemStack(itemPlank, 1);
        bucketStorage.stacks[3] = new ItemStack(itemStick, 2);

        CountingTerminal bucketTerminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(bucketStorage, ForgeDirection.NORTH));
        CraftingInventorySnapshot bucketSnapshot = new CraftingInventorySnapshot(bucketTerminal, player, true);

        ItemStack[][] bucketGrid = emptyGrid();
        bucketGrid[0] = new ItemStack[] { new ItemStack(itemWaterBucket, 1), new ItemStack(itemLog, 1, 99) };
        bucketGrid[1] = new ItemStack[] { new ItemStack(itemEmptyBucket, 1), new ItemStack(itemStick, 1) };
        bucketGrid[2] = new ItemStack[] { new ItemStack(itemPlank, 1), new ItemStack(itemPlank, 1, 1) };

        ItemStack[][] prunedBucket = RecipeNbtSerializer
            .pruneIngredients(bucketGrid, bucketSnapshot, null, 5, false, 0);
        assertEquals(1, prunedBucket[0].length);
        assertEquals(itemWaterBucket, prunedBucket[0][0].getItem());
        // Only round 1 is completable (using stick); round 2 lacks plank so empty bucket must NOT be recorded in slot 1
        assertEquals(1, prunedBucket[1].length);
        assertEquals(itemStick, prunedBucket[1][0].getItem());

        // 2. When slot 1 has 0 local stock but IS satisfied by subOutputs, multi-round tool depletion keeps spare tool
        StorageTestInventory subToolStorage = new StorageTestInventory(4);
        subToolStorage.stacks[0] = wrenchLastHit.copy();
        subToolStorage.stacks[1] = wrenchSpare.copy();
        CountingTerminal subToolTerminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(subToolStorage, ForgeDirection.NORTH));
        CraftingInventorySnapshot subToolSnapshot = new CraftingInventorySnapshot(subToolTerminal, player, true);

        ItemStack[][] toolStickGrid = emptyGrid();
        toolStickGrid[0] = new ItemStack[] { wrenchLastHit.copy(), wrenchSpare.copy(), createGtWrench(0L, 3000L) };
        toolStickGrid[1] = new ItemStack[] { new ItemStack(itemStick, 1, 0), new ItemStack(itemStick, 1, 1) };

        List<ItemStack> stickSubOutputs = Collections.singletonList(new ItemStack(itemStick, 2, 0));
        ItemStack[][] prunedWithSub = RecipeNbtSerializer
            .pruneIngredients(toolStickGrid, subToolSnapshot, stickSubOutputs, 2, false, 0);
        assertEquals(2, prunedWithSub[0].length);
        assertEquals(1L, CraftingRemainderHandler.getGTDurability(prunedWithSub[0][0]));
        assertEquals(2000L, CraftingRemainderHandler.getGTDurability(prunedWithSub[0][1]));
        assertEquals(1, prunedWithSub[1].length);
        assertEquals(0, prunedWithSub[1][0].getItemDamage());
    }

    @Test
    public void pruneIngredientsPreservesDistinctStackSizesOfSameItemAcrossAllCapsAndExecutesTwoRounds() {
        StorageTestInventory storage = new StorageTestInventory(4);
        storage.stacks[0] = new ItemStack(itemPlate, 3);
        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(storage, ForgeDirection.NORTH));
        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);

        ItemStack[][] rawGrid = emptyGrid();
        rawGrid[0] = new ItemStack[] { new ItemStack(itemPlate, 2), new ItemStack(itemPlate, 1) };
        ItemStack output = new ItemStack(itemCasing, 1);
        IRecipe oneOrTwoPlateRecipe = new RangeCountSingleSlotRecipe(itemPlate, 1, 2, output);

        for (int cap : new int[] { Integer.MAX_VALUE, 2, 0 }) {
            ItemStack[][] pruned = RecipeNbtSerializer.pruneIngredients(rawGrid, snapshot, null, 2, false, cap);
            assertEquals("cap=" + cap + " must keep both 2x and 1x candidates", 2, pruned[0].length);
            assertEquals(2, pruned[0][0].stackSize);
            assertEquals(1, pruned[0][1].stackSize);

            NBTTagCompound serialized = RecipeNbtSerializer.writeRecipe(pruned, output, 2);
            CraftingPattern deserializedPattern = RecipeNbtSerializer.readPattern(serialized);
            ItemStack[][] roundTripped = deserializedPattern.getIngredients();
            assertEquals(2, roundTripped[0].length);
            assertEquals(2, roundTripped[0][0].stackSize);
            assertEquals(1, roundTripped[0][1].stackSize);

            AutoCraftPreviewInventory preview = new AutoCraftPreviewInventory();
            assertTrue(
                "Preview must succeed for 2 rounds under cap=" + cap,
                preview.applyRequest(1, terminal.getStacks(), player.inventory, roundTripped, output, 2));
            List<StoredItemStack> remainingPreview = preview.getStacks(terminal.getStacks());
            assertEquals(0L, countStoredByItem(remainingPreview, itemPlate));
            assertEquals(2L, countStoredByItem(remainingPreview, itemCasing));

            CraftingPlanner planner = new CraftingPlanner(
                player,
                snapshot.copy(),
                Collections.emptyList(),
                false,
                Collections.singletonList(oneOrTwoPlateRecipe));
            CraftingPlanner.CraftResult planned = planner.plan(deserializedPattern, 2);
            assertTrue("Planner must succeed for 2 rounds under cap=" + cap, planned.isSuccess());
        }

        // End-to-end execution on default unshrunk path (cap = Integer.MAX_VALUE)
        ItemStack[][] defaultPruned = RecipeNbtSerializer
            .pruneIngredients(rawGrid, snapshot, null, 2, false, Integer.MAX_VALUE);
        CraftingPattern finalPattern = RecipeNbtSerializer
            .readPattern(RecipeNbtSerializer.writeRecipe(defaultPruned, output, 2));
        CraftingPlanner finalPlanner = new CraftingPlanner(
            player,
            snapshot,
            Collections.emptyList(),
            false,
            Collections.singletonList(oneOrTwoPlateRecipe));
        CraftingPlanner.CraftResult finalPlanned = finalPlanner.plan(finalPattern, 2);
        assertTrue(finalPlanned.isSuccess());

        SingleAutoCraftExecutor.CraftResult executed = SingleAutoCraftExecutor
            .executePlan(terminal, player, finalPlanned.getPlan());
        assertTrue(executed.isSuccess());
        assertEquals(2, countInPlayerInventory(itemCasing));
        assertEquals(0, countInStorage(storage, itemPlate));
    }

    @Test
    public void pruneIngredientsPreservesMultiCountSubCraftCandidatesAndRanksLocalMatchesByCraftableRounds() {
        // 1. Sub-recipe matching preserves both [2xStick, 1xStick] with their own stackSizes (not subOut's 4x)
        CraftingInventorySnapshot emptySnapshot = new CraftingInventorySnapshot(
            Collections.emptyList(),
            Collections.emptyList(),
            true);
        ItemStack[][] subGrid = emptyGrid();
        subGrid[0] = new ItemStack[] { new ItemStack(itemStick, 2), new ItemStack(itemStick, 1),
            new ItemStack(itemPlank, 1) };
        List<ItemStack> subOutputs = Collections.singletonList(new ItemStack(itemStick, 4));

        for (int cap : new int[] { Integer.MAX_VALUE, 0 }) {
            ItemStack[][] prunedSub = RecipeNbtSerializer
                .pruneIngredients(subGrid, emptySnapshot, subOutputs, 2, false, cap);
            assertEquals(2, prunedSub[0].length);
            assertEquals(itemStick, prunedSub[0][0].getItem());
            assertEquals(2, prunedSub[0][0].stackSize);
            assertEquals(itemStick, prunedSub[0][1].getItem());
            assertEquals(1, prunedSub[0][1].stackSize);
        }

        // 2a. Pure localMatches ranking (slot 1 missing aborts round 1 so requiredBySlot is empty):
        // [4xPlank(meta=0, stock=3 -> crafts=0), 4xPlank(meta=1, stock=3 -> crafts=0), 1xPlank(meta=2, stock=2 ->
        // crafts=2)]
        StorageTestInventory storage = new StorageTestInventory(6);
        storage.stacks[0] = new ItemStack(itemPlank, 3, 0);
        storage.stacks[1] = new ItemStack(itemPlank, 3, 1);
        storage.stacks[2] = new ItemStack(itemPlank, 2, 2);
        storage.stacks[3] = new ItemStack(itemPlank, 1, 9);
        CountingTerminal terminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(storage, ForgeDirection.NORTH));
        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(terminal, player, true);

        ItemStack[][] pureLocalGrid = emptyGrid();
        pureLocalGrid[0] = new ItemStack[] { new ItemStack(itemPlank, 4, 0), new ItemStack(itemPlank, 4, 1),
            new ItemStack(itemPlank, 1, 2) };
        pureLocalGrid[1] = new ItemStack[] { new ItemStack(itemStick, 1, 0) };

        ItemStack[][] prunedTopOne = RecipeNbtSerializer.pruneIngredients(pureLocalGrid, snapshot, null, 1, false, 1);
        assertEquals(1, prunedTopOne[0].length);
        assertEquals(2, prunedTopOne[0][0].getItemDamage());
        assertEquals(1, prunedTopOne[0][0].stackSize);

        // 2b. Legacy/capped 1-round prune (count=1, cap=2) keeps 1x(meta=9) from round 1 + 1x(meta=2) by crafts=2,
        // enabling a 3-round plan (whereas picking 4x(meta=0) with stock=3 would fail on round 2).
        ItemStack[][] multiRoundGrid = emptyGrid();
        multiRoundGrid[0] = new ItemStack[] { new ItemStack(itemPlank, 1, 9), new ItemStack(itemPlank, 4, 0),
            new ItemStack(itemPlank, 4, 1), new ItemStack(itemPlank, 1, 2) };
        ItemStack[][] prunedCapTwo = RecipeNbtSerializer.pruneIngredients(multiRoundGrid, snapshot, null, 1, false, 2);
        assertEquals(2, prunedCapTwo[0].length);
        assertEquals(9, prunedCapTwo[0][0].getItemDamage());
        assertEquals(2, prunedCapTwo[0][1].getItemDamage());

        ItemStack[][] wildcardPlankSlot = emptyGrid();
        wildcardPlankSlot[0] = new ItemStack[] { new ItemStack(itemPlank, 1, OreDictionary.WILDCARD_VALUE) };
        CraftingPlanner planner = new CraftingPlanner(
            player,
            snapshot.copy(),
            Collections.emptyList(),
            false,
            Collections.singletonList(new CountingRecipe(wildcardPlankSlot, new ItemStack(itemChest, 1))));
        assertTrue(
            planner.plan(new CraftingPattern(new ItemStack(itemChest, 1), prunedCapTwo), 3)
                .isSuccess());
    }

    @Test
    public void zeroAndOneOreDictStacksDeduplicateWithoutMutatingCallerStacksAndRememberPatternKeepsLatestOutput() {
        // 1. 200 mixed 0x and 1x duplicate stacks deduplicate to 1 normalized stack without mutating caller inputs
        ItemStack[] mixedOreDict = new ItemStack[200];
        for (int i = 0; i < mixedOreDict.length; i++) {
            mixedOreDict[i] = new ItemStack(itemPlank, i % 2 == 0 ? 0 : 1, 5);
        }
        ItemStack singleZeroStack = new ItemStack(itemStick, 0, 0);
        ItemStack fallbackZeroFirst = new ItemStack(itemChest, 0, 0);
        ItemStack fallbackOneSecond = new ItemStack(itemChest, 1, 0);

        ItemStack[][] rawGrid = emptyGrid();
        rawGrid[0] = mixedOreDict;
        rawGrid[1] = new ItemStack[] { singleZeroStack };
        rawGrid[2] = new ItemStack[] { fallbackZeroFirst, fallbackOneSecond };

        CraftingInventorySnapshot snapshot = new CraftingInventorySnapshot(
            Arrays.asList(
                new StoredItemStack(new ItemStack(itemPlank, 1, 5), 10L),
                new StoredItemStack(new ItemStack(itemStick, 1, 0), 10L)),
            Collections.emptyList(),
            true);

        ItemStack[][] pruned = RecipeNbtSerializer
            .pruneIngredients(rawGrid, snapshot, null, 1, false, Integer.MAX_VALUE);
        assertEquals(1, pruned[0].length);
        assertEquals(1, pruned[0][0].stackSize);
        assertEquals(1, pruned[1].length);
        assertEquals(1, pruned[1][0].stackSize);
        assertEquals(1, pruned[2].length);
        assertEquals(1, pruned[2][0].stackSize);

        // Direct writeRecipe -> readIngredients on raw zero-sized stacks also normalizes NBT Count to 1
        NBTTagCompound rawTag = RecipeNbtSerializer.writeRecipe(rawGrid, new ItemStack(itemCasing, 1), 1);
        ItemStack[][] deserializedRaw = RecipeNbtSerializer.readIngredients(rawTag);
        assertEquals(1, deserializedRaw[0][0].stackSize);
        assertEquals(1, deserializedRaw[1][0].stackSize);
        assertEquals(1, deserializedRaw[2][0].stackSize);

        // Caller-owned stacks MUST remain completely unmutated (0 stays 0)
        for (int i = 0; i < mixedOreDict.length; i++) {
            assertEquals(i % 2 == 0 ? 0 : 1, mixedOreDict[i].stackSize);
        }
        assertEquals(0, singleZeroStack.stackSize);
        assertEquals(0, fallbackZeroFirst.stackSize);
        assertEquals(1, fallbackOneSecond.stackSize);

        // 2. AutoCraftPreviewInventory.rememberPattern + filterReachablePatterns keeps the latest pattern (4x -> 6x)
        ItemStack[][] handPlankGrid = emptyGrid();
        handPlankGrid[0] = new ItemStack[] { new ItemStack(itemLog, 1) };
        NBTTagCompound handPlankTag = RecipeNbtSerializer.writeRecipe(handPlankGrid, new ItemStack(itemPlank, 4), 1);

        ItemStack[][] sawPlankGrid = emptyGrid();
        sawPlankGrid[0] = new ItemStack[] { new ItemStack(itemLog, 1) };
        sawPlankGrid[1] = new ItemStack[] { new ItemStack(itemWrench, 1) };
        NBTTagCompound sawPlankTag = RecipeNbtSerializer.writeRecipe(sawPlankGrid, new ItemStack(itemPlank, 6), 1);

        ItemStack[][] chestGrid = emptyGrid();
        for (int slot = 0; slot < RecipeNbtSerializer.GRID_SIZE; slot++) {
            if (slot != 4) {
                chestGrid[slot] = new ItemStack[] { new ItemStack(itemPlank, 1) };
            }
        }

        AutoCraftPreviewInventory preview = new AutoCraftPreviewInventory();
        preview.rememberPattern(handPlankTag);
        preview.rememberPattern(sawPlankTag);

        List<NBTTagCompound> reachableLatestSaw = RecipeNbtSerializer
            .filterReachablePatterns(chestGrid, preview.getPatterns());
        assertEquals(1, reachableLatestSaw.size());
        assertEquals(6, RecipeNbtSerializer.readOutput(reachableLatestSaw.get(0)).stackSize);

        // End-to-end proof: with only 1 log + 2 planks + 1 GT wrench, 1 chest (8 planks) ONLY succeeds via 6xPlank!
        StorageTestInventory sawTestStorage = new StorageTestInventory(4);
        sawTestStorage.stacks[0] = new ItemStack(itemLog, 1);
        sawTestStorage.stacks[1] = new ItemStack(itemPlank, 2);
        sawTestStorage.stacks[2] = createGtWrench(0L, 100L);
        CountingTerminal sawTerminal = new CountingTerminal(
            serverWorld,
            StorageInventoryProvider.getInventory(sawTestStorage, ForgeDirection.NORTH));

        NBTTagCompound chestCraftTag = RecipeNbtSerializer.writeRecipe(chestGrid, new ItemStack(itemChest, 1), 1);
        RecipeNbtSerializer.setPatterns(chestCraftTag, reachableLatestSaw);
        CraftingPlanner sawPlanner = new CraftingPlanner(
            player,
            new CraftingInventorySnapshot(sawTerminal, player, true),
            RecipeNbtSerializer.readPatterns(chestCraftTag),
            true,
            Arrays.asList(
                new SimpleGtToolRecipe(itemLog, itemWrench, new ItemStack(itemPlank, 6)),
                new CountingRecipe(handPlankGrid, new ItemStack(itemPlank, 4)),
                new CountingRecipe(chestGrid, new ItemStack(itemChest, 1))));
        assertTrue(
            sawPlanner.plan(RecipeNbtSerializer.readPattern(chestCraftTag), 1)
                .isSuccess());

        // Re-remembering 4xPlank refreshes recency back to 4xPlank
        preview.rememberPattern(handPlankTag);
        List<NBTTagCompound> reachableRefreshedHand = RecipeNbtSerializer
            .filterReachablePatterns(chestGrid, preview.getPatterns());
        assertEquals(1, reachableRefreshedHand.size());
        assertEquals(4, RecipeNbtSerializer.readOutput(reachableRefreshedHand.get(0)).stackSize);
    }

    private static ItemStack[][] emptyGrid() {
        ItemStack[][] grid = new ItemStack[RecipeNbtSerializer.GRID_SIZE][];
        for (int i = 0; i < grid.length; i++) grid[i] = new ItemStack[0];
        return grid;
    }

    private static ItemStack createGtWrench(long damage, long maxDamage) {
        ItemStack stack = new ItemStack(itemWrench, 1);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound stats = new NBTTagCompound();
        stats.setLong("Damage", damage);
        stats.setLong("MaxDamage", maxDamage);
        tag.setTag("GT.ToolStats", stats);
        stack.setTagCompound(tag);
        return stack;
    }

    private int countInPlayerInventory(Item target) {
        int total = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.getItem() == target) total += stack.stackSize;
        }
        return total;
    }

    private ItemStack findItemInPlayer(Item target) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.getItem() == target) return stack;
        }
        return null;
    }

    private static int countInStorage(StorageTestInventory inv, Item target) {
        int total = 0;
        for (ItemStack stack : inv.stacks) {
            if (stack != null && stack.getItem() == target) total += stack.stackSize;
        }
        return total;
    }

    private static long countStoredByItem(List<StoredItemStack> storedStacks, Item target) {
        long total = 0L;
        for (StoredItemStack stored : storedStacks) {
            if (stored != null && stored.getStack() != null
                && stored.getStack()
                    .getItem() == target) {
                total += stored.getQuantity();
            }
        }
        return total;
    }

    private static ItemStack findItemInStorage(StorageTestInventory inv, Item target) {
        for (ItemStack stack : inv.stacks) {
            if (stack != null && stack.getItem() == target) return stack;
        }
        return null;
    }

    private static MessageContext createServerMessageContext(EntityPlayerMP serverPlayer) throws Exception {
        NetHandlerPlayServer netHandler = mock(NetHandlerPlayServer.class);
        netHandler.playerEntity = serverPlayer;
        Constructor<MessageContext> ctor = MessageContext.class.getDeclaredConstructor(INetHandler.class, Side.class);
        ctor.setAccessible(true);
        return ctor.newInstance(netHandler, Side.SERVER);
    }

    private static final class CountingNBTTagCompound extends NBTTagCompound {

        private final AtomicInteger copyCounter;

        private CountingNBTTagCompound(AtomicInteger copyCounter) {
            this.copyCounter = copyCounter;
        }

        @Override
        public NBTBase copy() {
            copyCounter.incrementAndGet();
            CountingNBTTagCompound copy = new CountingNBTTagCompound(copyCounter);
            for (Object keyObj : func_150296_c()) {
                String key = (String) keyObj;
                copy.setTag(key, getTag(key).copy());
            }
            return copy;
        }
    }

    private static final class SupplierCounter {

        private final List<String> values;
        private int calls;

        private SupplierCounter(List<String> values) {
            this.values = values;
        }

        private List<String> get() {
            calls++;
            return values;
        }
    }

    private static final class VoidSinkInventory extends StorageTestInventory {

        private final Item acceptedItem;
        private int voidedItems;

        private VoidSinkInventory(Item acceptedItem) {
            super(1);
            this.acceptedItem = acceptedItem;
            this.stacks[0] = new ItemStack(acceptedItem, 64);
        }

        @Override
        public boolean isItemValidForSlot(int index, ItemStack stack) {
            return stack != null && stack.getItem() == acceptedItem;
        }

        @Override
        public ItemStack getStackInSlot(int index) {
            // Looks empty/available for insertion or acts as a void sink while refusing extraction
            return null;
        }

        @Override
        public void setInventorySlotContents(int index, ItemStack stack) {
            if (stack != null && stack.getItem() == acceptedItem) {
                voidedItems += stack.stackSize;
            }
        }
    }

    private static final class CountingTerminal extends TileEntityCraftingTerminal {

        private final IStorageInventory attached;
        private int pullCalls;
        private int pushCalls;

        private CountingTerminal(World world, IStorageInventory attached) {
            setWorldObj(world);
            this.attached = attached;
        }

        @Override
        protected IStorageInventory getAttachedInventory() {
            return attached;
        }

        @Override
        public StoredItemStack pullStack(StoredItemStack stack, long maxAmount) {
            pullCalls++;
            return super.pullStack(stack, maxAmount);
        }

        @Override
        public ItemStack pushStack(ItemStack stack) {
            pushCalls++;
            return super.pushStack(stack);
        }
    }

    private static final class NoRecipeCraftingContainer extends ContainerCraftingTerminal {

        private NoRecipeCraftingContainer(InventoryPlayer inventory, TileEntityCraftingTerminal terminal) {
            super(inventory, terminal);
        }

        @Override
        public void onCraftMatrixChanged(IInventory inventory) {}

        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
            return true;
        }
    }

    private static final class CountingRecipe implements IRecipe {

        private final ItemStack[][] expectedGrid;
        private final ItemStack output;
        private int matchCalls;

        private CountingRecipe(ItemStack[][] expectedGrid, ItemStack output) {
            this.expectedGrid = expectedGrid;
            this.output = output.copy();
        }

        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            matchCalls++;
            for (int i = 0; i < RecipeNbtSerializer.GRID_SIZE; i++) {
                ItemStack actual = inv.getStackInSlot(i);
                ItemStack[] cands = expectedGrid[i];
                if (cands == null || cands.length == 0) {
                    if (actual != null) return false;
                } else {
                    if (actual == null || !CraftingStackMatcher.matchesIngredient(cands[0], actual)) return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            return output.copy();
        }

        @Override
        public int getRecipeSize() {
            return 9;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return output;
        }
    }

    private static final class SimpleGtToolRecipe implements IRecipe {

        private final Item plate;
        private final Item tool;
        private final ItemStack output;

        private SimpleGtToolRecipe(Item plate, Item tool, ItemStack output) {
            this.plate = plate;
            this.tool = tool;
            this.output = output.copy();
        }

        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            ItemStack s0 = inv.getStackInSlot(0);
            ItemStack s1 = inv.getStackInSlot(1);
            if (s0 == null || s0.getItem() != plate) return false;
            if (s1 == null || s1.getItem() != tool || CraftingRemainderHandler.getGTDurability(s1) <= 0) return false;
            for (int i = 2; i < 9; i++) {
                if (inv.getStackInSlot(i) != null) return false;
            }
            return true;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            return StorageItemUtils.copyWithSize(output, output.stackSize);
        }

        @Override
        public int getRecipeSize() {
            return 2;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return output;
        }
    }

    private static final class MoldAndFallbackRecipe implements IRecipe {

        private final Item mold;
        private final Item fallback;
        private final ItemStack output;

        private MoldAndFallbackRecipe(Item mold, Item fallback, ItemStack output) {
            this.mold = mold;
            this.fallback = fallback;
            this.output = output.copy();
        }

        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            ItemStack s0 = inv.getStackInSlot(0);
            ItemStack s1 = inv.getStackInSlot(1);
            if (s0 == null || s0.getItem() != mold) return false;
            if (s1 == null || (s1.getItem() != mold && s1.getItem() != fallback)) return false;
            for (int i = 2; i < RecipeNbtSerializer.GRID_SIZE; i++) {
                if (inv.getStackInSlot(i) != null) return false;
            }
            return true;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            return output.copy();
        }

        @Override
        public int getRecipeSize() {
            return 2;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return output;
        }
    }

    private static final class ExactCountSingleSlotRecipe implements IRecipe {

        private final Item expectedItem;
        private final int expectedCount;
        private final ItemStack output;
        private int matchCalls;

        private ExactCountSingleSlotRecipe(Item expectedItem, int expectedCount, ItemStack output) {
            this.expectedItem = expectedItem;
            this.expectedCount = expectedCount;
            this.output = output.copy();
        }

        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            matchCalls++;
            ItemStack s0 = inv.getStackInSlot(0);
            if (s0 == null || s0.getItem() != expectedItem || s0.stackSize != expectedCount) return false;
            for (int i = 1; i < RecipeNbtSerializer.GRID_SIZE; i++) {
                if (inv.getStackInSlot(i) != null) return false;
            }
            return true;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            return output.copy();
        }

        @Override
        public int getRecipeSize() {
            return 1;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return output;
        }
    }

    private static final class RangeCountSingleSlotRecipe implements IRecipe {

        private final Item expectedItem;
        private final int minCount;
        private final int maxCount;
        private final ItemStack output;

        private RangeCountSingleSlotRecipe(Item expectedItem, int minCount, int maxCount, ItemStack output) {
            this.expectedItem = expectedItem;
            this.minCount = minCount;
            this.maxCount = maxCount;
            this.output = output.copy();
        }

        @Override
        public boolean matches(InventoryCrafting inv, World world) {
            ItemStack s0 = inv.getStackInSlot(0);
            if (s0 == null || s0.getItem() != expectedItem || s0.stackSize < minCount || s0.stackSize > maxCount) {
                return false;
            }
            for (int i = 1; i < RecipeNbtSerializer.GRID_SIZE; i++) {
                if (inv.getStackInSlot(i) != null) return false;
            }
            return true;
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            return output.copy();
        }

        @Override
        public int getRecipeSize() {
            return 1;
        }

        @Override
        public ItemStack getRecipeOutput() {
            return output;
        }
    }
}
