package com.hepdd.toms_storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.hepdd.toms_storage.client.GuiCraftingTerminal;
import com.hepdd.toms_storage.client.GuiStorageTerminal;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.gui.ContainerStorageTerminal;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

import cpw.mods.fml.common.network.IGuiHandler;

public class GuiHandler implements IGuiHandler {

    public static final int STORAGE_TERMINAL = 0;
    public static final int CRAFTING_TERMINAL = 1;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (id == STORAGE_TERMINAL && tile instanceof TileEntityStorageTerminal) {
            return new ContainerStorageTerminal(player.inventory, (TileEntityStorageTerminal) tile);
        }
        if (id == CRAFTING_TERMINAL && tile instanceof TileEntityCraftingTerminal) {
            return new ContainerCraftingTerminal(player.inventory, (TileEntityCraftingTerminal) tile);
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (id == STORAGE_TERMINAL && tile instanceof TileEntityStorageTerminal) {
            return new GuiStorageTerminal(player.inventory, (TileEntityStorageTerminal) tile);
        }
        if (id == CRAFTING_TERMINAL && tile instanceof TileEntityCraftingTerminal) {
            return new GuiCraftingTerminal(player.inventory, (TileEntityCraftingTerminal) tile);
        }
        return null;
    }
}
