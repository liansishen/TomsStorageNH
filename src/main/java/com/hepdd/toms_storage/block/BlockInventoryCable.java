package com.hepdd.toms_storage.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.inventory.IStorageNetworkEndpoint;

public class BlockInventoryCable extends Block {

    private static final float MIN = 6.0F / 16.0F;
    private static final float MAX = 10.0F / 16.0F;

    public BlockInventoryCable() {
        super(Material.wood);
        setBlockName("tomsstorage.inventory_cable");
        setBlockTextureName("tomsstorage:inventory_cable");
        setHardness(1.5F);
        setResistance(3.0F);
        setCreativeTab(ModRegistry.CREATIVE_TAB);
        setBlockBounds(MIN, MIN, MIN, MAX, MAX, MAX);
    }

    public boolean connectsTo(IBlockAccess world, int x, int y, int z, ForgeDirection direction) {
        int nextX = x + direction.offsetX;
        int nextY = y + direction.offsetY;
        int nextZ = z + direction.offsetZ;
        Block block = world.getBlock(nextX, nextY, nextZ);
        if (block == ModRegistry.inventoryCable || block == ModRegistry.inventoryConnector) return true;
        TileEntity tile = world.getTileEntity(nextX, nextY, nextZ);
        if (tile instanceof IStorageNetworkEndpoint) {
            return ((IStorageNetworkEndpoint) tile).canConnectNetworkFrom(direction.getOpposite());
        }
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return ModRegistry.cableRenderId;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        setBlockBoundsBasedOnState(world, x, y, z);
        return super.getCollisionBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        float minX = connectsTo(world, x, y, z, ForgeDirection.WEST) ? 0.0F : MIN;
        float minY = connectsTo(world, x, y, z, ForgeDirection.DOWN) ? 0.0F : MIN;
        float minZ = connectsTo(world, x, y, z, ForgeDirection.NORTH) ? 0.0F : MIN;
        float maxX = connectsTo(world, x, y, z, ForgeDirection.EAST) ? 1.0F : MAX;
        float maxY = connectsTo(world, x, y, z, ForgeDirection.UP) ? 1.0F : MAX;
        float maxZ = connectsTo(world, x, y, z, ForgeDirection.SOUTH) ? 1.0F : MAX;
        setBlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBlockBounds(MIN, MIN, MIN, MAX, MAX, MAX);
    }
}
