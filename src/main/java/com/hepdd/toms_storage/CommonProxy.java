package com.hepdd.toms_storage;

import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.network.PacketAutoCraftResult;
import com.hepdd.toms_storage.network.PacketConnectorRangePreview;
import com.hepdd.toms_storage.network.PacketTerminalData;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
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
}
