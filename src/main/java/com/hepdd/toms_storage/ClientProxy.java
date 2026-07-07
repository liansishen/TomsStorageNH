package com.hepdd.toms_storage;

import com.hepdd.toms_storage.client.RenderTerminalBlock;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ModRegistry.terminalRenderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new RenderTerminalBlock());
        super.preInit(event);
    }
}
