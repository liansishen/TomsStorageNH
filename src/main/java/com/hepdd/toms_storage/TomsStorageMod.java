package com.hepdd.toms_storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = TomsStorageMod.MODID,
    version = Tags.VERSION,
    name = TomsStorageMod.NAME,
    acceptedMinecraftVersions = "[1.7.10]")
public class TomsStorageMod {

    public static final String MODID = "tomsstorage";
    public static final String NAME = "Tom's Simple Storage";
    public static final Logger LOG = LogManager.getLogger(MODID);
    public static final String NETWORK_CHANNEL = "TomsStorage";

    @SidedProxy(clientSide = "com.hepdd.toms_storage.ClientProxy", serverSide = "com.hepdd.toms_storage.CommonProxy")
    public static CommonProxy proxy;

    @Mod.Instance(MODID)
    public static TomsStorageMod instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }
}
