package com.hepdd.toms_storage.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.tile.TileEntityInventoryHopper;

public class BlockInventoryHopper extends BlockContainer {

    public BlockInventoryHopper() {
        super(Material.wood);
        setBlockName("tomsstorage.inventory_hopper");
        setBlockTextureName("tomsstorage:inventory_hopper_basic");
        setHardness(2.5F);
        setResistance(4.0F);
        setCreativeTab(ModRegistry.CREATIVE_TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityInventoryHopper();
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ,
        int metadata) {
        ForgeDirection direction = ForgeDirection.getOrientation(side);
        return direction == ForgeDirection.UNKNOWN ? ForgeDirection.DOWN.ordinal()
            : direction.getOpposite()
                .ordinal();
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
        return ModRegistry.hopperRenderId;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        setBoundsForFacing(ForgeDirection.getOrientation(world.getBlockMetadata(x, y, z)));
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBoundsForFacing(ForgeDirection.DOWN);
    }

    private void setBoundsForFacing(ForgeDirection facing) {
        if (facing == ForgeDirection.EAST || facing == ForgeDirection.WEST) {
            setBlockBounds(0.0F, 3.0F / 16.0F, 3.0F / 16.0F, 1.0F, 13.0F / 16.0F, 13.0F / 16.0F);
        } else if (facing == ForgeDirection.UP || facing == ForgeDirection.DOWN) {
            setBlockBounds(3.0F / 16.0F, 0.0F, 3.0F / 16.0F, 13.0F / 16.0F, 1.0F, 13.0F / 16.0F);
        } else {
            setBlockBounds(3.0F / 16.0F, 3.0F / 16.0F, 0.0F, 13.0F / 16.0F, 13.0F / 16.0F, 1.0F);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityInventoryHopper)) return true;
        TileEntityInventoryHopper hopper = (TileEntityInventoryHopper) tile;
        ItemStack held = player.getHeldItem();
        if (held != null) {
            hopper.setFilter(held);
            player.addChatMessage(new ChatComponentTranslation("message.tomsstorage.inventory_hopper.filter_set"));
        } else if (player.isSneaking()) {
            hopper.setFilter(null);
            player.addChatMessage(new ChatComponentTranslation("message.tomsstorage.inventory_hopper.filter_cleared"));
        } else {
            player.addChatMessage(
                new ChatComponentTranslation(
                    "message.tomsstorage.inventory_hopper.status",
                    new ChatComponentTranslation(hopper.getModeTranslationKey()),
                    new ChatComponentTranslation(hopper.getStatusTranslationKey())));
            ItemStack filter = hopper.getFilter();
            player.addChatMessage(
                filter == null ? new ChatComponentTranslation("message.tomsstorage.inventory_hopper.filter_none")
                    : new ChatComponentTranslation(
                        "message.tomsstorage.inventory_hopper.filter",
                        filter.getDisplayName()));
        }
        return true;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, net.minecraft.block.Block neighbor) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityInventoryHopper) ((TileEntityInventoryHopper) tile).markNetworkDirty();
    }
}
