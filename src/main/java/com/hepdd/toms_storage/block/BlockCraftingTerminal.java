package com.hepdd.toms_storage.block;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;

public class BlockCraftingTerminal extends BlockStorageTerminal {

    public BlockCraftingTerminal() {
        setBlockName("tomsstorage.crafting_terminal");
    }

    @Override
    protected String getFrontTextureName() {
        return "tomsstorage:crafting_terminal";
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityCraftingTerminal();
    }
}
