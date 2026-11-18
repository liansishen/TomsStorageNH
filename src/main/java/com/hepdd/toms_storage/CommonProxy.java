package com.hepdd.toms_storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;

import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.network.PacketAutoCraftResult;
import com.hepdd.toms_storage.network.PacketConnectorRangePreview;
import com.hepdd.toms_storage.network.PacketTerminalData;
import com.hepdd.toms_storage.tile.TileEntityInventoryConnector;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.NetworkRegistry;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        ModNetwork.init();
        ModRegistry.preInit();

        TomsStorageMod.LOG.info("Starting " + TomsStorageMod.NAME + " " + Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(TomsStorageMod.instance, new GuiHandler());
        ModRegistry.init();
        MinecraftForge.EVENT_BUS.register(new StorageTopologyEventHandler());
        NBTTagCompound craftingTweaks = new NBTTagCompound();
        craftingTweaks.setString("ContainerClass", ContainerCraftingTerminal.class.getName());
        craftingTweaks.setInteger("GridSlotNumber", ContainerCraftingTerminal.GRID_SLOT_START);
        craftingTweaks.setInteger("GridSize", 9);
        craftingTweaks.setString("AlignToGrid", "west");
        FMLInterModComms.sendMessage("craftingtweaks", "RegisterProvider", craftingTweaks);
    }

    public void postInit(FMLPostInitializationEvent event) {
        ModRegistry.postInit();
    }

    public void handleTerminalData(PacketTerminalData message) {}

    public void handleAutoCraftResult(PacketAutoCraftResult message) {}

    public void handleConnectorRangePreview(PacketConnectorRangePreview message) {}

    public void serverStarting(FMLServerStartingEvent event) {}

    public static final class StorageTopologyEventHandler {

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onBlockPlaced(BlockEvent.PlaceEvent event) {
            if (event.world == null || event.world.isRemote) return;
            if (event instanceof BlockEvent.MultiPlaceEvent) {
                for (BlockSnapshot snapshot : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) {
                    TileEntityInventoryConnector.notifyBlockChanged(event.world, snapshot.x, snapshot.y, snapshot.z);
                }
            } else {
                TileEntityInventoryConnector.notifyBlockChanged(event.world, event.x, event.y, event.z);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void onBlockBroken(BlockEvent.BreakEvent event) {
            if (event.world == null || event.world.isRemote) return;
            TileEntityInventoryConnector.notifyBlockChanged(event.world, event.x, event.y, event.z);
        }
    }
}
