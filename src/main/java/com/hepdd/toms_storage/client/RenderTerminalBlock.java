package com.hepdd.toms_storage.client;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;

import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.block.BlockStorageTerminal;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;

public class RenderTerminalBlock implements ISimpleBlockRenderingHandler {

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelId, RenderBlocks renderer) {
        Tessellator tessellator = Tessellator.instance;
        GL11.glTranslatef(-0.7F, -0.7F, -0.5F);
        tessellator.startDrawingQuads();
        renderTerminal(null, block, 0, 0, 0, ForgeDirection.NORTH, renderer, tessellator, true);
        tessellator.draw();
        GL11.glTranslatef(0.7F, 0.7F, 0.5F);
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        ForgeDirection facing = BlockStorageTerminal.getFacing(world.getBlockMetadata(x, y, z));
        renderTerminal(world, block, x, y, z, facing, renderer, Tessellator.instance, false);
        return true;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return ModRegistry.terminalRenderId;
    }

    private void renderTerminal(IBlockAccess world, Block block, int x, int y, int z, ForgeDirection facing,
        RenderBlocks renderer, Tessellator tessellator, boolean inventory) {
        IIcon front = block.getIcon(facing.ordinal(), facing.ordinal());
        IIcon back = block.getIcon(
            facing.getOpposite()
                .ordinal(),
            facing.ordinal());
        ForgeDirection sideDirection = facing.offsetX == 0 ? ForgeDirection.EAST : ForgeDirection.NORTH;
        IIcon side = block.getIcon(sideDirection.ordinal(), facing.ordinal());
        IIcon top = block.getIcon(ForgeDirection.UP.ordinal(), facing.ordinal());

        if (world != null) tessellator.setBrightness(block.getMixedBrightnessForBlock(world, x, y, z));
        tessellator.setColorOpaque_F(1.0F, 1.0F, 1.0F);

        renderBox(x, y, z, facing, 0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 6.0D, back, front, side, top, tessellator);
        renderBox(x, y, z, facing, 0.0D, 15.0D, 0.0D, 16.0D, 16.0D, 6.0D, back, front, side, top, tessellator);
        renderBox(x, y, z, facing, 0.0D, 1.0D, 0.0D, 1.0D, 15.0D, 6.0D, back, front, side, top, tessellator);
        renderBox(x, y, z, facing, 15.0D, 1.0D, 0.0D, 16.0D, 15.0D, 6.0D, back, front, side, top, tessellator);
        renderBox(x, y, z, facing, 1.0D, 1.0D, 0.0D, 15.0D, 15.0D, 5.0D, back, front, side, top, tessellator);
    }

    private void renderBox(int x, int y, int z, ForgeDirection facing, double minX, double minY, double minZ,
        double maxX, double maxY, double maxZ, IIcon back, IIcon front, IIcon side, IIcon top,
        Tessellator tessellator) {
        drawFace(
            x,
            y,
            z,
            facing,
            back,
            new double[][] { { minX, maxY, minZ }, { maxX, maxY, minZ }, { maxX, minY, minZ }, { minX, minY, minZ } },
            new double[][] { { minX, minY }, { maxX, minY }, { maxX, maxY }, { minX, maxY } },
            tessellator);
        drawFace(
            x,
            y,
            z,
            facing,
            front,
            new double[][] { { minX, minY, maxZ }, { maxX, minY, maxZ }, { maxX, maxY, maxZ }, { minX, maxY, maxZ } },
            new double[][] { { minX, maxY }, { maxX, maxY }, { maxX, minY }, { minX, minY } },
            tessellator);
        drawFace(
            x,
            y,
            z,
            facing,
            side,
            new double[][] { { minX, minY, minZ }, { minX, minY, maxZ }, { minX, maxY, maxZ }, { minX, maxY, minZ } },
            new double[][] { { minZ, maxY }, { maxZ, maxY }, { maxZ, minY }, { minZ, minY } },
            tessellator);
        drawFace(
            x,
            y,
            z,
            facing,
            side,
            new double[][] { { maxX, maxY, minZ }, { maxX, maxY, maxZ }, { maxX, minY, maxZ }, { maxX, minY, minZ } },
            new double[][] { { minZ, minY }, { maxZ, minY }, { maxZ, maxY }, { minZ, maxY } },
            tessellator);
        drawFace(
            x,
            y,
            z,
            facing,
            top,
            new double[][] { { minX, minY, minZ }, { maxX, minY, minZ }, { maxX, minY, maxZ }, { minX, minY, maxZ } },
            new double[][] { { minX, minZ }, { maxX, minZ }, { maxX, maxZ }, { minX, maxZ } },
            tessellator);
        drawFace(
            x,
            y,
            z,
            facing,
            top,
            new double[][] { { minX, maxY, maxZ }, { maxX, maxY, maxZ }, { maxX, maxY, minZ }, { minX, maxY, minZ } },
            new double[][] { { minX, maxZ }, { maxX, maxZ }, { maxX, minZ }, { minX, minZ } },
            tessellator);
    }

    private void drawFace(int x, int y, int z, ForgeDirection facing, IIcon icon, double[][] vertices, double[][] uv,
        Tessellator tessellator) {
        double[] normal = calculateNormal(vertices);
        tessellator.setNormal((float) normal[0], (float) normal[1], (float) normal[2]);
        for (int i = 0; i < 4; i++) {
            double[] vertex = rotatePoint(
                facing,
                vertices[i][0] / 16.0D,
                vertices[i][1] / 16.0D,
                vertices[i][2] / 16.0D);
            tessellator.addVertexWithUV(
                x + vertex[0],
                y + vertex[1],
                z + vertex[2],
                icon.getInterpolatedU(uv[i][0]),
                icon.getInterpolatedV(uv[i][1]));
        }
    }

    private double[] calculateNormal(double[][] vertices) {
        double[] edgeA = new double[] { vertices[1][0] - vertices[0][0], vertices[1][1] - vertices[0][1],
            vertices[1][2] - vertices[0][2] };
        double[] edgeB = new double[] { vertices[2][0] - vertices[1][0], vertices[2][1] - vertices[1][1],
            vertices[2][2] - vertices[1][2] };
        double normalX = edgeA[1] * edgeB[2] - edgeA[2] * edgeB[1];
        double normalY = edgeA[2] * edgeB[0] - edgeA[0] * edgeB[2];
        double normalZ = edgeA[0] * edgeB[1] - edgeA[1] * edgeB[0];
        double length = Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length <= 0.0D) return new double[] { 0.0D, 1.0D, 0.0D };
        return new double[] { normalX / length, normalY / length, normalZ / length };
    }

    private double[] rotatePoint(ForgeDirection facing, double localX, double localY, double localZ) {
        switch (facing) {
            case SOUTH:
                return new double[] { 1.0D - localX, localY, 1.0D - localZ };
            case EAST:
                return new double[] { 1.0D - localZ, localY, localX };
            case WEST:
                return new double[] { localZ, localY, 1.0D - localX };
            case NORTH:
            default:
                return new double[] { localX, localY, localZ };
        }
    }
}
