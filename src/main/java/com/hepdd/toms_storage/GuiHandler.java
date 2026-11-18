package com.hepdd.toms_storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.hepdd.toms_storage.client.GuiCraftingTerminal;
import com.hepdd.toms_storage.client.GuiStorageTerminal;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.gui.ContainerStorageTerminal;
import com.hepdd.toms_storage.inventory.StorageInventoryUtils;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

import cpw.mods.fml.common.network.IGuiHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class GuiHandler implements IGuiHandler {

    public static final int STORAGE_TERMINAL = 0;
    public static final int CRAFTING_TERMINAL = 1;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (!StorageInventoryUtils.isChunkLoaded(world, x, z)) return null;
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
        return ClientGuiFactory.createGui(id, player, world, x, y, z);
    }

    public static Container createClientContainer(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileEntityStorageTerminal terminal = resolveClientTerminal(id, world, x, y, z);
        if (id == STORAGE_TERMINAL && terminal != null) {
            return new ContainerStorageTerminal(player.inventory, terminal);
        }
        if (id == CRAFTING_TERMINAL && terminal instanceof TileEntityCraftingTerminal) {
            return new ContainerCraftingTerminal(player.inventory, (TileEntityCraftingTerminal) terminal);
        }
        return null;
    }

    private static TileEntityStorageTerminal resolveClientTerminal(int id, World world, int x, int y, int z) {
        TileEntity tile = StorageInventoryUtils.isChunkLoaded(world, x, z) ? world.getTileEntity(x, y, z) : null;
        if (id == STORAGE_TERMINAL) {
            return tile instanceof TileEntityStorageTerminal ? (TileEntityStorageTerminal) tile
                : createClientTerminal(new TileEntityStorageTerminal(), world, x, y, z);
        }
        if (id == CRAFTING_TERMINAL) {
            return tile instanceof TileEntityCraftingTerminal ? (TileEntityCraftingTerminal) tile
                : createClientTerminal(new TileEntityCraftingTerminal(), world, x, y, z);
        }
        return null;
    }

    private static <T extends TileEntityStorageTerminal> T createClientTerminal(T terminal, World world, int x, int y,
        int z) {
        terminal.setWorldObj(world);
        terminal.xCoord = x;
        terminal.yCoord = y;
        terminal.zCoord = z;
        return terminal;
    }

    @SideOnly(Side.CLIENT)
    private static final class ClientGuiFactory {

        private static Object createGui(int id, EntityPlayer player, World world, int x, int y, int z) {
            TileEntityStorageTerminal terminal = resolveClientTerminal(id, world, x, y, z);
            if (id == STORAGE_TERMINAL && terminal != null) {
                return new GuiStorageTerminal(player.inventory, terminal);
            }
            if (id == CRAFTING_TERMINAL && terminal instanceof TileEntityCraftingTerminal) {
                return new GuiCraftingTerminal(player.inventory, (TileEntityCraftingTerminal) terminal);
            }
            return null;
        }
    }
}
