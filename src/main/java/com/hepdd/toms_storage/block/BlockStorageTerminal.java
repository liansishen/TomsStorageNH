package com.hepdd.toms_storage.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.GuiHandler;
import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.TomsStorageMod;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class BlockStorageTerminal extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon frontIcon;

    @SideOnly(Side.CLIENT)
    private IIcon sideIcon;

    @SideOnly(Side.CLIENT)
    private IIcon topIcon;

    public BlockStorageTerminal() {
        super(Material.wood);
        setBlockName("tomsstorage.storage_terminal");
        setBlockTextureName("tomsstorage:inventory_connector");
        setHardness(3.0F);
        setResistance(5.0F);
        setCreativeTab(ModRegistry.CREATIVE_TAB);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister register) {
        blockIcon = register.registerIcon(getTextureName());
        frontIcon = register.registerIcon(getFrontTextureName());
        sideIcon = register.registerIcon("tomsstorage:terminal_side");
        topIcon = register.registerIcon("tomsstorage:terminal_top");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int metadata) {
        ForgeDirection facing = getFacing(metadata);
        ForgeDirection sideDirection = ForgeDirection.getOrientation(side);
        if (sideDirection == facing) return frontIcon;
        if (sideDirection == facing.getOpposite()) return blockIcon;
        if (sideDirection == ForgeDirection.UP || sideDirection == ForgeDirection.DOWN) return topIcon;
        return sideIcon;
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
        return ModRegistry.terminalRenderId;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        setBlockBoundsBasedOnState(world, x, y, z);
        return super.getCollisionBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        setBoundsForFacing(getFacing(world.getBlockMetadata(x, y, z)));
    }

    @Override
    public void setBlockBoundsForItemRender() {
        setBoundsForFacing(ForgeDirection.NORTH);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityStorageTerminal();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            int guiId = tile instanceof TileEntityCraftingTerminal ? GuiHandler.CRAFTING_TERMINAL
                : GuiHandler.STORAGE_TERMINAL;
            player.openGui(TomsStorageMod.instance, guiId, world, x, y, z);
        }
        return true;
    }

    @Override
    public int onBlockPlaced(World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ,
        int metadata) {
        ForgeDirection clickedSide = ForgeDirection.getOrientation(side);
        if (clickedSide.offsetY == 0 && clickedSide != ForgeDirection.UNKNOWN) {
            return clickedSide.getOpposite()
                .ordinal();
        }
        return metadata;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack stack) {
        ForgeDirection facing = getFacing(world.getBlockMetadata(x, y, z));
        if (facing.offsetY != 0) {
            facing = ForgeDirection.getOrientation(determineFacing(entity));
            world.setBlockMetadataWithNotify(x, y, z, facing.ordinal(), 2);
        }
    }

    public static ForgeDirection getFacing(int metadata) {
        ForgeDirection direction = ForgeDirection.getOrientation(metadata);
        return direction == ForgeDirection.UNKNOWN ? ForgeDirection.NORTH : direction;
    }

    private void setBoundsForFacing(ForgeDirection facing) {
        switch (facing) {
            case SOUTH:
                setBlockBounds(0.0F, 0.0F, 0.625F, 1.0F, 1.0F, 1.0F);
                break;
            case EAST:
                setBlockBounds(0.625F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
                break;
            case WEST:
                setBlockBounds(0.0F, 0.0F, 0.0F, 0.375F, 1.0F, 1.0F);
                break;
            case NORTH:
            default:
                setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.375F);
                break;
        }
    }

    protected String getFrontTextureName() {
        return "tomsstorage:storage_terminal";
    }

    private int determineFacing(EntityLivingBase entity) {
        int direction = (int) Math.floor(entity.rotationYaw * 4.0F / 360.0F + 0.5D) & 3;
        switch (direction) {
            case 0:
                return ForgeDirection.NORTH.ordinal();
            case 1:
                return ForgeDirection.EAST.ordinal();
            case 2:
                return ForgeDirection.SOUTH.ordinal();
            case 3:
                return ForgeDirection.WEST.ordinal();
            default:
                return ForgeDirection.NORTH.ordinal();
        }
    }
}
