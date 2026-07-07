package com.hepdd.toms_storage.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

import com.hepdd.toms_storage.ModRegistry;

public class BlockTrim extends Block {

    public BlockTrim() {
        super(Material.wood);
        setBlockName("tomsstorage.trim");
        setBlockTextureName("tomsstorage:trim");
        setHardness(3.0F);
        setResistance(5.0F);
        setCreativeTab(ModRegistry.CREATIVE_TAB);
    }
}
