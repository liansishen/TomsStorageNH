package com.hepdd.toms_storage.inventory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.capability.Capabilities;
import com.gtnewhorizon.gtnhlib.capability.item.ItemIO;

public final class StorageInventoryProvider {

    private enum Kind {
        UNSUPPORTED,
        NORMAL,
        LOCKED,
        WEAPON_CASE,
        KITCHEN,
        FRIDGE,
        PRESENT,
        ENDER,
        BOUND,
        MAGIC,
        FILING,
        BARREL,
        DRAWERS,
        GREGTECH,
        BINNIE_MACHINE,
        BINNIE_COMPARTMENT,
        DIGITAL_CHEST,
        LEGACY_CHEST,
        PROJECTRED_BARREL
    }

    private static final Map<String, Kind> TYPES = new HashMap<>();

    private static final ClassValue<Kind> KIND_CACHE = new ClassValue<Kind>() {

        @Override
        protected Kind computeValue(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                Kind kind = TYPES.get(current.getName());
                if (kind != null) return kind;
            }
            return Kind.UNSUPPORTED;
        }
    };

    static {
        register(
            Kind.NORMAL,
            "net.minecraft.tileentity.TileEntityChest",
            "cpw.mods.ironchest.TileEntityIronChest",
            "ganymedes01.etfuturum.tileentities.TileEntityBarrel",
            "ganymedes01.etfuturum.tileentities.TileEntityShulkerBox",
            "appeng.tile.storage.TileSkyChest",
            "wanion.avaritiaddons.block.chest.TileEntityAvaritiaddonsChest",
            "com.rwtema.extrautils.tileentity.chests.TileFullChest",
            "com.rwtema.extrautils.tileentity.chests.TileMiniChest",
            "tconstruct.tools.logic.TiCChestLogic",
            "forestry.core.tiles.TileNaturalistChest",
            "thaumcraft.common.tiles.TileChestHungry",
            "tuhljin.automagy.tiles.TileEntityEagerChest",
            "tuhljin.automagy.tiles.TileEntityGreedyChest",
            "tuhljin.automagy.tiles.TileEntityTenaciousChest",
            "com.emoniph.witchery.blocks.BlockLeechChest$TileEntityLeechChest",
            "com.emoniph.witchery.blocks.BlockPoppetShelf$TileEntityPoppetShelf",
            "micdoodle8.mods.galacticraft.core.tile.TileEntityTreasureChest",
            "de.katzenpapst.amunra.tile.TileEntityARChest",
            "mods.railcraft.common.blocks.machine.beta.TileChestRailcraft",
            "mods.railcraft.common.blocks.machine.beta.TileChestMetals",
            "mods.railcraft.common.blocks.machine.beta.TileChestVoid",
            "com.carpentersblocks.tileentity.TECarpentersSafe",
            "com.dreammaster.modbabychest.TileEntityBabyChest",
            "crazypants.enderio.machine.vacuum.TileVacuumChest",
            "pcl.openprinter.tileentity.FileCabinetTE",
            "gtPlusPlus.core.tileentities.general.TileEntityDecayablesChest",
            "com.brandon3055.draconicevolution.common.tileentities.TileDraconiumChest",
            "com.darkona.adventurebackpack.block.TileAdventureBackpack",
            "net.blay09.mods.cookingforblockheads.tile.TileToolRack",
            "jds.bibliocraft.tileentities.TileEntityCookieJar",
            "jds.bibliocraft.tileentities.TileEntityDiscRack",
            "jds.bibliocraft.tileentities.TileEntityDinnerPlate");
        register(
            Kind.LOCKED,
            "jds.bibliocraft.tileentities.TileEntityFramedChest",
            "jds.bibliocraft.tileentities.TileEntityBookcase",
            "jds.bibliocraft.tileentities.TileEntityGenericShelf",
            "jds.bibliocraft.tileentities.TileEntityPotionShelf",
            "jds.bibliocraft.tileentities.TileEntityWeaponRack",
            "jds.bibliocraft.tileentities.TileEntityArmorStand",
            "jds.bibliocraft.tileentities.TileEntitySwordPedestal");
        register(Kind.WEAPON_CASE, "jds.bibliocraft.tileentities.TileEntityWeaponCase");
        register(
            Kind.KITCHEN,
            "net.blay09.mods.cookingforblockheads.tile.TileCounter",
            "net.blay09.mods.cookingforblockheads.tile.TileCabinet",
            "net.blay09.mods.cookingforblockheads.tile.TileCounterCorner",
            "net.blay09.mods.cookingforblockheads.tile.TileCabinetCorner");
        register(Kind.FRIDGE, "net.blay09.mods.cookingforblockheads.tile.TileFridge");
        register(Kind.PRESENT, "team.chisel.block.tileentity.TileEntityPresent");
        register(Kind.ENDER, "codechicken.enderstorage.storage.item.TileEnderChest");
        register(Kind.BOUND, "flaxbeard.thaumicexploration.tile.TileEntityBoundChest");
        register(Kind.MAGIC, "thaumcraft.common.tiles.TileMagicBox");
        register(Kind.FILING, "com.rwtema.extrautils.tileentity.TileEntityFilingCabinet");
        register(Kind.BARREL, "mcp.mobius.betterbarrels.common.blocks.TileEntityBarrel");
        register(Kind.DRAWERS, "com.jaquadro.minecraft.storagedrawers.block.tile.TileEntityDrawers");
        register(Kind.GREGTECH, "gregtech.api.metatileentity.BaseMetaTileEntity");
        register(Kind.DIGITAL_CHEST, "gregtech.common.tileentities.storage.MTEDigitalChestBase");
        register(Kind.LEGACY_CHEST, "gtPlusPlus.xmod.gregtech.common.tileentities.storage.MTETieredChest");
        register(Kind.PROJECTRED_BARREL, "mrtjp.projectred.exploration.TileBarrel");
        register(Kind.BINNIE_MACHINE, "binnie.core.machines.TileEntityMachine");
        register(Kind.BINNIE_COMPARTMENT, "binnie.core.machines.storage.ComponentCompartmentInventory");
    }

    private StorageInventoryProvider() {}

    private static void register(Kind kind, String... names) {
        for (String name : names) TYPES.put(name, kind);
    }

    private static Kind kind(Object object) {
        if (object == null) return null;
        Kind resolved = KIND_CACHE.get(object.getClass());
        return resolved == Kind.UNSUPPORTED ? null : resolved;
    }

    public static IStorageInventory getInventory(TileEntity tile, ForgeDirection side) {
        if (!(tile instanceof IInventory)) return null;
        IInventory inventory = (IInventory) tile;
        Kind kind = kind(tile);
        if (kind == null) return null;
        Object storage = tile;
        if (kind == Kind.GREGTECH) {
            storage = StorageCompatibility.call(tile, "getMetaTileEntity");
            kind = kind(storage);
            if (kind != Kind.DIGITAL_CHEST && kind != Kind.LEGACY_CHEST) return null;
        }
        if (kind == Kind.BINNIE_MACHINE && !isBinnieCompartment(tile)) return null;
        if (kind == Kind.FRIDGE) {
            CombinedStorageInventory combined = new CombinedStorageInventory();
            combined.add(kitchenInventory(tile, side));
            TileEntity neighbour = (TileEntity) StorageCompatibility.call(tile, "findNeighbourFridge");
            if (neighbour != null) combined.add(kitchenInventory(neighbour, side));
            return combined;
        }
        if (kind == Kind.KITCHEN) return kitchenInventory(tile, side);
        Object identity = getStorageIdentity(inventory);
        if (kind == Kind.PRESENT) inventory = (IInventory) identity;
        BooleanSupplier accessible = kind == Kind.LOCKED || kind == Kind.WEAPON_CASE
            ? () -> !(Boolean) StorageCompatibility.call(tile, "isLocked")
            : () -> true;
        if (kind == Kind.BARREL || kind == Kind.DRAWERS || kind == Kind.DIGITAL_CHEST || kind == Kind.LEGACY_CHEST) {
            ItemIO io = Capabilities.getCapability(tile, ItemIO.class, side);
            if (io != null) {
                return new CapabilityInventoryAdapter(tile, inventory, side, io, kind == Kind.DRAWERS);
            }
            if (kind == Kind.LEGACY_CHEST) return new BulkInventoryAdapter(tile, side, storage, false);
            // The buffered IInventory views do not expose the real stored quantity.
            return null;
        }
        if (kind == Kind.PROJECTRED_BARREL) return new BulkInventoryAdapter(tile, side, tile, true);
        if (kind == Kind.FILING) return filingInventory(tile, inventory, side);
        return new StorageInventoryAdapter(
            tile,
            inventory,
            side,
            identity,
            accessible,
            kind == Kind.WEAPON_CASE ? 1 : Integer.MAX_VALUE);
    }

    public static Object getStorageIdentity(IInventory inventory) {
        Kind kind = kind(inventory);
        if (kind == Kind.ENDER || kind == Kind.BARREL) return StorageCompatibility.call(inventory, "getStorage");
        if (kind == Kind.PRESENT) return StorageCompatibility.call(inventory, "getParent");
        if (kind == Kind.MAGIC) return StorageCompatibility.callPrivate(inventory, "getInventory");
        if (kind == Kind.BOUND) {
            int id = (Integer) StorageCompatibility.field(inventory, "id");
            return id > 0 ? Arrays.asList(((TileEntity) inventory).getWorldObj(), id) : inventory;
        }
        if (kind == Kind.KITCHEN || kind == Kind.FRIDGE) {
            return StorageCompatibility.call(inventory, "getInternalInventory");
        }
        return inventory;
    }

    private static boolean isBinnieCompartment(TileEntity tile) {
        Object machine = StorageCompatibility.call(tile, "getMachine");
        if (machine == null) return false;
        for (Object component : (Iterable<?>) StorageCompatibility.call(machine, "getComponents")) {
            if (kind(component) == Kind.BINNIE_COMPARTMENT) return true;
        }
        return false;
    }

    private static IStorageInventory kitchenInventory(TileEntity tile, ForgeDirection side) {
        IInventory internal = (IInventory) StorageCompatibility.call(tile, "getInternalInventory");
        return new StorageInventoryAdapter(tile, internal, side, internal, () -> true, Integer.MAX_VALUE);
    }

    private static IStorageInventory filingInventory(TileEntity tile, IInventory inventory, ForgeDirection side) {
        return new StorageInventoryAdapter(tile, inventory, side, tile, () -> true, Integer.MAX_VALUE) {

            private int storedSlots() {
                return ((List<?>) StorageCompatibility.field(tile, "itemSlots")).size();
            }

            @Override
            public void forEachStack(Consumer<ItemStack> consumer) {
                int stored = storedSlots();
                for (int slot = 0; slot < stored; slot++) {
                    consumer.accept(super.getStackInSlot(slot));
                }
            }

            @Override
            public long packSlotStats() {
                int total = getSlots();
                int stored = Math.min(total, storedSlots());
                int free = total - stored;
                for (int slot = 0; slot < stored; slot++) {
                    ItemStack stack = super.getStackInSlot(slot);
                    if (stack == null || stack.stackSize <= 0) free++;
                }
                return ((long) total << 32) | (free & 0xFFFFFFFFL);
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return slot < storedSlots() ? super.getStackInSlot(slot) : null;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                ItemStack stack = getStackInSlot(slot);
                return stack == null ? null
                    : super.extractItem(slot, Math.min(amount, stack.getMaxStackSize()), simulate);
            }
        };
    }
}
