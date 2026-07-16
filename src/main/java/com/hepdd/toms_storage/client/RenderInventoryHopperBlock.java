package com.hepdd.toms_storage.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.hepdd.toms_storage.ModRegistry;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

/** Renders the wide input body and narrow output nozzle from the 1.17 hopper model. */
public class RenderInventoryHopperBlock implements ISimpleBlockRenderingHandler {

    private static final double BODY_MIN = 3.0D / 16.0D;
    private static final double BODY_MAX = 13.0D / 16.0D;
    private static final double TIP_MIN = 5.0D / 16.0D;
    private static final double TIP_MAX = 11.0D / 16.0D;
    private static final double SEAM_LOW = 6.0D / 16.0D;
    private static final double SEAM_HIGH = 10.0D / 16.0D;

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        ForgeDirection facing = getFacing(metadata);
        IIcon icon = block.getIcon(0, metadata);
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        renderInventoryCuboid(icon, getBodyBounds(facing), null, 6.0D, 0.0D, 16.0D, 10.0D);
        renderInventoryCuboid(icon, getTipBounds(facing), facing.getOpposite(), 0.0D, 0.0D, 6.0D, 6.0D);
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        ForgeDirection facing = getFacing(world.getBlockMetadata(x, y, z));
        IIcon icon = block.getIcon(world, x, y, z, facing.ordinal());
        Tessellator tessellator = Tessellator.instance;
        tessellator.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
        renderCuboid(tessellator, icon, getBodyBounds(facing), null, x, y, z, 6.0D, 0.0D, 16.0D, 10.0D, false);
        renderCuboid(
            tessellator,
            icon,
            getTipBounds(facing),
            facing.getOpposite(),
            x,
            y,
            z,
            0.0D,
            0.0D,
            6.0D,
            6.0D,
            false);
        return true;
    }

    private void renderInventoryCuboid(IIcon icon, Bounds bounds, ForgeDirection omitted, double minU, double minV,
        double maxU, double maxV) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        renderCuboid(tessellator, icon, bounds, omitted, 0.0D, 0.0D, 0.0D, minU, minV, maxU, maxV, true);
        tessellator.draw();
    }

    private void renderCuboid(Tessellator tessellator, IIcon icon, Bounds bounds, ForgeDirection omitted,
        double originX, double originY, double originZ, double minUPixel, double minVPixel, double maxUPixel,
        double maxVPixel, boolean inventory) {
        double minX = originX + bounds.minX;
        double minY = originY + bounds.minY;
        double minZ = originZ + bounds.minZ;
        double maxX = originX + bounds.maxX;
        double maxY = originY + bounds.maxY;
        double maxZ = originZ + bounds.maxZ;
        double minU = icon.getInterpolatedU(minUPixel);
        double minV = icon.getInterpolatedV(minVPixel);
        double maxU = icon.getInterpolatedU(maxUPixel);
        double maxV = icon.getInterpolatedV(maxVPixel);

        if (omitted != ForgeDirection.DOWN) {
            prepareFace(tessellator, inventory, 0.0F, -1.0F, 0.0F, 0.5F);
            addQuad(
                tessellator,
                minX,
                minY,
                maxZ,
                minX,
                minY,
                minZ,
                maxX,
                minY,
                minZ,
                maxX,
                minY,
                maxZ,
                minU,
                minV,
                maxU,
                maxV);
        }
        if (omitted != ForgeDirection.UP) {
            prepareFace(tessellator, inventory, 0.0F, 1.0F, 0.0F, 1.0F);
            addQuad(
                tessellator,
                minX,
                maxY,
                minZ,
                minX,
                maxY,
                maxZ,
                maxX,
                maxY,
                maxZ,
                maxX,
                maxY,
                minZ,
                minU,
                minV,
                maxU,
                maxV);
        }
        if (omitted != ForgeDirection.NORTH) {
            prepareFace(tessellator, inventory, 0.0F, 0.0F, -1.0F, 0.8F);
            addQuad(
                tessellator,
                minX,
                minY,
                minZ,
                minX,
                maxY,
                minZ,
                maxX,
                maxY,
                minZ,
                maxX,
                minY,
                minZ,
                minU,
                minV,
                maxU,
                maxV);
        }
        if (omitted != ForgeDirection.SOUTH) {
            prepareFace(tessellator, inventory, 0.0F, 0.0F, 1.0F, 0.8F);
            addQuad(
                tessellator,
                minX,
                maxY,
                maxZ,
                minX,
                minY,
                maxZ,
                maxX,
                minY,
                maxZ,
                maxX,
                maxY,
                maxZ,
                minU,
                minV,
                maxU,
                maxV);
        }
        if (omitted != ForgeDirection.WEST) {
            prepareFace(tessellator, inventory, -1.0F, 0.0F, 0.0F, 0.6F);
            addQuad(
                tessellator,
                minX,
                minY,
                maxZ,
                minX,
                maxY,
                maxZ,
                minX,
                maxY,
                minZ,
                minX,
                minY,
                minZ,
                minU,
                minV,
                maxU,
                maxV);
        }
        if (omitted != ForgeDirection.EAST) {
            prepareFace(tessellator, inventory, 1.0F, 0.0F, 0.0F, 0.6F);
            addQuad(
                tessellator,
                maxX,
                minY,
                minZ,
                maxX,
                maxY,
                minZ,
                maxX,
                maxY,
                maxZ,
                maxX,
                minY,
                maxZ,
                minU,
                minV,
                maxU,
                maxV);
        }
    }

    private void prepareFace(Tessellator tessellator, boolean inventory, float normalX, float normalY, float normalZ,
        float shade) {
        if (inventory) {
            tessellator.setNormal(normalX, normalY, normalZ);
            tessellator.setColorOpaque_F(1.0F, 1.0F, 1.0F);
        } else {
            tessellator.setColorOpaque_F(shade, shade, shade);
        }
    }

    private void addQuad(Tessellator tessellator, double x1, double y1, double z1, double x2, double y2, double z2,
        double x3, double y3, double z3, double x4, double y4, double z4, double minU, double minV, double maxU,
        double maxV) {
        tessellator.addVertexWithUV(x1, y1, z1, minU, maxV);
        tessellator.addVertexWithUV(x2, y2, z2, minU, minV);
        tessellator.addVertexWithUV(x3, y3, z3, maxU, minV);
        tessellator.addVertexWithUV(x4, y4, z4, maxU, maxV);
    }

    private Bounds getBodyBounds(ForgeDirection facing) {
        switch (facing) {
            case DOWN:
                return new Bounds(BODY_MIN, SEAM_LOW, BODY_MIN, BODY_MAX, 1.0D, BODY_MAX);
            case UP:
                return new Bounds(BODY_MIN, 0.0D, BODY_MIN, BODY_MAX, SEAM_HIGH, BODY_MAX);
            case NORTH:
                return new Bounds(BODY_MIN, BODY_MIN, SEAM_LOW, BODY_MAX, BODY_MAX, 1.0D);
            case SOUTH:
                return new Bounds(BODY_MIN, BODY_MIN, 0.0D, BODY_MAX, BODY_MAX, SEAM_HIGH);
            case WEST:
                return new Bounds(SEAM_LOW, BODY_MIN, BODY_MIN, 1.0D, BODY_MAX, BODY_MAX);
            case EAST:
            default:
                return new Bounds(0.0D, BODY_MIN, BODY_MIN, SEAM_HIGH, BODY_MAX, BODY_MAX);
        }
    }

    private Bounds getTipBounds(ForgeDirection facing) {
        switch (facing) {
            case DOWN:
                return new Bounds(TIP_MIN, 0.0D, TIP_MIN, TIP_MAX, SEAM_LOW, TIP_MAX);
            case UP:
                return new Bounds(TIP_MIN, SEAM_HIGH, TIP_MIN, TIP_MAX, 1.0D, TIP_MAX);
            case NORTH:
                return new Bounds(TIP_MIN, TIP_MIN, 0.0D, TIP_MAX, TIP_MAX, SEAM_LOW);
            case SOUTH:
                return new Bounds(TIP_MIN, TIP_MIN, SEAM_HIGH, TIP_MAX, TIP_MAX, 1.0D);
            case WEST:
                return new Bounds(0.0D, TIP_MIN, TIP_MIN, SEAM_LOW, TIP_MAX, TIP_MAX);
            case EAST:
            default:
                return new Bounds(SEAM_HIGH, TIP_MIN, TIP_MIN, 1.0D, TIP_MAX, TIP_MAX);
        }
    }

    private ForgeDirection getFacing(int metadata) {
        ForgeDirection facing = ForgeDirection.getOrientation(metadata);
        return facing == ForgeDirection.UNKNOWN ? ForgeDirection.DOWN : facing;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return ModRegistry.hopperRenderId;
    }

    private static final class Bounds {

        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;

        private Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }
}
