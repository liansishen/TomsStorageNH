package com.hepdd.toms_storage;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;

import com.hepdd.toms_storage.client.RenderTerminalBlock;
import com.hepdd.toms_storage.gui.ContainerStorageTerminal;
import com.hepdd.toms_storage.nei.NEIDebug;
import com.hepdd.toms_storage.network.PacketAutoCraftResult;
import com.hepdd.toms_storage.network.PacketTerminalData;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ModRegistry.terminalRenderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new RenderTerminalBlock());
        super.preInit(event);
    }

    @Override
    public void handleTerminalData(PacketTerminalData message) {
        Container container = Minecraft.getMinecraft().thePlayer.openContainer;
        if (container instanceof ContainerStorageTerminal) {
            ContainerStorageTerminal terminal = (ContainerStorageTerminal) container;
            terminal.clientStacks = message.getStacks();
            terminal.getAutoCraftPreview()
                .clear();
            terminal.sorting = message.getSorting();
            terminal.search = message.getSearch();
        }
    }

    @Override
    public void handleAutoCraftResult(PacketAutoCraftResult message) {
        Container container = Minecraft.getMinecraft().thePlayer.openContainer;
        if (container instanceof ContainerStorageTerminal) {
            if (!message.isSuccess()) NEIDebug
                .log("autocraft result failed request=" + message.getRequestId() + " reason=" + message.getReason());
            ((ContainerStorageTerminal) container).getAutoCraftPreview()
                .clearIfMatches(message.getRequestId());
        }
    }
}
