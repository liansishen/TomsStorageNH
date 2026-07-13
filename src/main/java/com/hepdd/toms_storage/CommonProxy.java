package com.hepdd.toms_storage;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;

import com.hepdd.toms_storage.network.PacketAutoCraftResult;
import com.hepdd.toms_storage.network.PacketTerminalData;

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
    }

    public void postInit(FMLPostInitializationEvent event) {
        ModRegistry.postInit();
    }

    public void handleTerminalData(PacketTerminalData message) {}

    public void handleAutoCraftResult(PacketAutoCraftResult message) {}

    public void serverStarting(FMLServerStartingEvent event) {}
}
