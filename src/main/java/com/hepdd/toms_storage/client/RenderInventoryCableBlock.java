package com.hepdd.toms_storage.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.block.BlockInventoryCable;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/** Recreates the 1.17 multipart cable model with locked UVs on 1.7.10. */
public class RenderInventoryCableBlock implements ISimpleBlockRenderingHandler {

    private static final double MIN = 6.0D / 16.0D;
    private static final double MAX = 10.0D / 16.0D;

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        IIcon icon = block.getIcon(0, metadata);
        Tessellator tessellator = Tessellator.instance;
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        tessellator.startDrawingQuads();
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            renderClosedCap(tessellator, icon, 0.0D, 0.0D, 0.0D, direction, true);
        }
        tessellator.draw();
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        BlockInventoryCable cable = (BlockInventoryCable) block;
        IIcon icon = block.getIcon(world, x, y, z, 0);
        Tessellator tessellator = Tessellator.instance;
        tessellator.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (cable.connectsTo(world, x, y, z, direction)) {
                renderOpenArm(tessellator, icon, x, y, z, direction, false);
            } else {
                renderClosedCap(tessellator, icon, x, y, z, direction, false);
            }
        }
        return true;
    }

    private void renderOpenArm(Tessellator tessellator, IIcon icon, double originX, double originY, double originZ,
        ForgeDirection direction, boolean inventory) {
        renderQuad(
            tessellator,
            icon,
            originX,
            originY,
            originZ,
            direction,
            ForgeDirection.EAST,
            inventory,
            0.0D,
            6.0D,
            6.0D,
            10.0D,
            MAX,
            MIN,
            0.0D,
            MAX,
            MAX,
            0.0D,
            MAX,
            MAX,
            MIN,
            MAX,
            MIN,
            MIN);
        renderQuad(
            tessellator,
            icon,
            originX,
            originY,
            originZ,
            direction,
            ForgeDirection.WEST,
            inventory,
            10.0D,
            6.0D,
            16.0D,
            10.0D,
            MIN,
            MIN,
            MIN,
            MIN,
            MAX,
            MIN,
            MIN,
            MAX,
            0.0D,
            MIN,
            MIN,
            0.0D);
        renderQuad(
            tessellator,
            icon,
            originX,
            originY,
            originZ,
            direction,
            ForgeDirection.UP,
            inventory,
            6.0D,
            0.0D,
            10.0D,
            6.0D,
            MIN,
            MAX,
            0.0D,
            MIN,
            MAX,
            MIN,
            MAX,
            MAX,
            MIN,
            MAX,
            MAX,
            0.0D);
        renderQuad(
            tessellator,
            icon,
            originX,
            originY,
            originZ,
            direction,
            ForgeDirection.DOWN,
            inventory,
            6.0D,
            10.0D,
            10.0D,
            16.0D,
            MIN,
            MIN,
            MIN,
            MIN,
            MIN,
            0.0D,
            MAX,
            MIN,
            0.0D,
            MAX,
            MIN,
            MIN);
    }

    private void renderClosedCap(Tessellator tessellator, IIcon icon, double originX, double originY, double originZ,
        ForgeDirection direction, boolean inventory) {
        renderQuad(
            tessellator,
            icon,
            originX,
            originY,
            originZ,
            direction,
            ForgeDirection.NORTH,
            inventory,
            6.0D,
            6.0D,
            10.0D,
            10.0D,
            MIN,
            MIN,
            MIN,
            MIN,
            MAX,
            MIN,
            MAX,
            MAX,
            MIN,
            MAX,
            MIN,
            MIN);
    }

    private void renderQuad(Tessellator tessellator, IIcon icon, double originX, double originY, double originZ,
        ForgeDirection rotation, ForgeDirection canonicalNormal, boolean inventory, double minUPixel, double minVPixel,
        double maxUPixel, double maxVPixel, double x1, double y1, double z1, double x2, double y2, double z2, double x3,
        double y3, double z3, double x4, double y4, double z4) {
        ForgeDirection normal = rotateDirection(canonicalNormal, rotation);
        if (inventory) {
            tessellator.setNormal(normal.offsetX, normal.offsetY, normal.offsetZ);
            tessellator.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        } else {
            float shade = getShade(normal);
            tessellator.setColorOpaque_F(shade, shade, shade);
        }
        double[] first = rotatePoint(x1, y1, z1, rotation);
        double[] second = rotatePoint(x2, y2, z2, rotation);
        double[] third = rotatePoint(x3, y3, z3, rotation);
        double[] fourth = rotatePoint(x4, y4, z4, rotation);
        double minU = icon.getInterpolatedU(minUPixel);
        double minV = icon.getInterpolatedV(minVPixel);
        double maxU = icon.getInterpolatedU(maxUPixel);
        double maxV = icon.getInterpolatedV(maxVPixel);
        tessellator.addVertexWithUV(originX + first[0], originY + first[1], originZ + first[2], minU, maxV);
        tessellator.addVertexWithUV(originX + second[0], originY + second[1], originZ + second[2], minU, minV);
        tessellator.addVertexWithUV(originX + third[0], originY + third[1], originZ + third[2], maxU, minV);
        tessellator.addVertexWithUV(originX + fourth[0], originY + fourth[1], originZ + fourth[2], maxU, maxV);
    }

    private double[] rotatePoint(double x, double y, double z, ForgeDirection direction) {
        switch (direction) {
            case SOUTH:
                return new double[] { 1.0D - x, y, 1.0D - z };
            case EAST:
                return new double[] { 1.0D - z, y, x };
            case WEST:
                return new double[] { z, y, 1.0D - x };
            case UP:
                return new double[] { x, 1.0D - z, y };
            case DOWN:
                return new double[] { x, z, 1.0D - y };
            case NORTH:
            default:
                return new double[] { x, y, z };
        }
    }

    private ForgeDirection rotateDirection(ForgeDirection normal, ForgeDirection rotation) {
        int x = normal.offsetX;
        int y = normal.offsetY;
        int z = normal.offsetZ;
        switch (rotation) {
            case SOUTH:
                return ForgeDirection.getOrientation(vectorToSide(-x, y, -z));
            case EAST:
                return ForgeDirection.getOrientation(vectorToSide(-z, y, x));
            case WEST:
                return ForgeDirection.getOrientation(vectorToSide(z, y, -x));
            case UP:
                return ForgeDirection.getOrientation(vectorToSide(x, -z, y));
            case DOWN:
                return ForgeDirection.getOrientation(vectorToSide(x, z, -y));
            case NORTH:
            default:
                return normal;
        }
    }

    private int vectorToSide(int x, int y, int z) {
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (direction.offsetX == x && direction.offsetY == y && direction.offsetZ == z) {
                return direction.ordinal();
            }
        }
        return ForgeDirection.UNKNOWN.ordinal();
    }

    private float getShade(ForgeDirection normal) {
        if (normal == ForgeDirection.DOWN) return 0.5F;
        if (normal == ForgeDirection.UP) return 1.0F;
        if (normal == ForgeDirection.NORTH || normal == ForgeDirection.SOUTH) return 0.8F;
        return 0.6F;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return ModRegistry.cableRenderId;
    }
}
