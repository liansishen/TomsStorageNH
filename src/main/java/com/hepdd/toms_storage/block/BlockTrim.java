package com.hepdd.toms_storage.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.world.World;

import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.tile.TileEntityInventoryConnector;

public class BlockTrim extends Block {

    public BlockTrim() {
        super(Material.wood);
        setBlockName("tomsstorage.trim");
        setBlockTextureName("tomsstorage:trim");
        setHardness(3.0F);
        setResistance(5.0F);
        setCreativeTab(ModRegistry.CREATIVE_TAB);
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        if (!world.isRemote) {
            TileEntityInventoryConnector.notifyBlockChanged(world, x, y, z);
        }
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (!world.isRemote) {
            TileEntityInventoryConnector.notifyBlockChanged(world, x, y, z);
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        super.breakBlock(world, x, y, z, block, metadata);
        if (!world.isRemote) {
            TileEntityInventoryConnector.notifyBlockChanged(world, x, y, z);
        }
    }
}
