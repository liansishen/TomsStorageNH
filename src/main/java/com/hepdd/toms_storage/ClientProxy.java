package com.hepdd.toms_storage;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraftforge.common.MinecraftForge;

import com.hepdd.toms_storage.client.ConnectorRangePreview;
import com.hepdd.toms_storage.client.GuiStorageTerminal;
import com.hepdd.toms_storage.client.RenderInventoryCableBlock;
import com.hepdd.toms_storage.client.RenderInventoryHopperBlock;
import com.hepdd.toms_storage.client.RenderTerminalBlock;
import com.hepdd.toms_storage.gui.ContainerStorageTerminal;
import com.hepdd.toms_storage.nei.AutoCraftPreviewInventory;
import com.hepdd.toms_storage.nei.NEIDebug;
import com.hepdd.toms_storage.network.PacketAutoCraftResult;
import com.hepdd.toms_storage.network.PacketConnectorRangePreview;
import com.hepdd.toms_storage.network.PacketTerminalData;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ModRegistry.terminalRenderId = RenderingRegistry.getNextAvailableRenderId();
        ModRegistry.cableRenderId = RenderingRegistry.getNextAvailableRenderId();
        ModRegistry.hopperRenderId = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new RenderTerminalBlock());
        RenderingRegistry.registerBlockHandler(new RenderInventoryCableBlock());
        RenderingRegistry.registerBlockHandler(new RenderInventoryHopperBlock());
        MinecraftForge.EVENT_BUS.register(ConnectorRangePreview.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(ConnectorRangePreview.INSTANCE);
        super.preInit(event);
    }

    @Override
    public void handleTerminalData(PacketTerminalData message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> applyTerminalData(message));
    }

    @Override
    public void handleAutoCraftResult(PacketAutoCraftResult message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> applyAutoCraftResult(message));
    }

    private void applyTerminalData(PacketTerminalData message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        Container container = mc.thePlayer.openContainer;
        if (!(container instanceof ContainerStorageTerminal)) return;
        ContainerStorageTerminal terminal = (ContainerStorageTerminal) container;
        if (terminal.applyTerminalDataPacket(message) && mc.currentScreen instanceof GuiStorageTerminal) {
            ((GuiStorageTerminal) mc.currentScreen).refreshFromTerminalData();
        }
    }

    private void applyAutoCraftResult(PacketAutoCraftResult message) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        Container container = mc.thePlayer.openContainer;
        if (container instanceof ContainerStorageTerminal) {
            AutoCraftPreviewInventory preview = ((ContainerStorageTerminal) container).getAutoCraftPreview();
            if (message.isSuccess()) {
                preview.clearIfMatches(message.getRequestId());
            } else {
                NEIDebug.log(
                    "autocraft result failed request=" + message.getRequestId() + " reason=" + message.getReason());
                preview.clearOnFailure(message.getRequestId());
            }
            if (mc.currentScreen instanceof GuiStorageTerminal) {
                ((GuiStorageTerminal) mc.currentScreen).refreshFromTerminalData();
            }
        }
    }

    @Override
    public void handleConnectorRangePreview(PacketConnectorRangePreview message) {
        Minecraft.getMinecraft()
            .func_152344_a(() -> ConnectorRangePreview.INSTANCE.showOrToggle(message));
    }
}
