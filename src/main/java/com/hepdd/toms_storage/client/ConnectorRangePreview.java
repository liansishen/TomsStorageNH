package com.hepdd.toms_storage.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.network.PacketConnectorRangePreview;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ConnectorRangePreview {

    public static final ConnectorRangePreview INSTANCE = new ConnectorRangePreview();
    private static final int MAX_LATITUDE_SEGMENTS = 128;
    private static final int MAX_LONGITUDE_SEGMENTS = 256;

    private int dimension;
    private int x;
    private int y;
    private int z;
    private int remainingTicks;
    private float[] surfaceVertices;
    private float[] gridVertices;
    private World lastWorld;

    private ConnectorRangePreview() {}

    public void showOrToggle(PacketConnectorRangePreview message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = minecraft.theWorld;
        lastWorld = world;
        if (gridVertices != null && dimension == message.getDimension()
            && x == message.getX()
            && y == message.getY()
            && z == message.getZ()) {
            clear();
            if (minecraft.thePlayer != null) {
                minecraft.thePlayer.addChatMessage(
                    new ChatComponentTranslation("message.tomsstorage.inventory_connector.range_preview_closed"));
            }
            return;
        }
        dimension = message.getDimension();
        x = message.getX();
        y = message.getY();
        z = message.getZ();
        remainingTicks = Math.max(1, message.getDurationTicks());
        SphereMesh mesh = createSphereMesh(Math.max(1, message.getRadius()));
        surfaceVertices = mesh.surfaceVertices;
        gridVertices = mesh.gridVertices;
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(
                new ChatComponentTranslation(
                    "message.tomsstorage.inventory_connector.range_preview_opened",
                    message.getRadius()));
        }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        World world = Minecraft.getMinecraft().theWorld;
        if (world != lastWorld) clear();
        lastWorld = world;
        if (gridVertices != null && --remainingTicks <= 0) clear();
    }

    @SubscribeEvent
    public void renderWorldLast(RenderWorldLastEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = minecraft.theWorld;
        EntityClientPlayerMP player = minecraft.thePlayer;
        if (gridVertices == null || world == null || player == null) return;
        if (world.provider.dimensionId != dimension || !world.blockExists(x, y, z)
            || world.getBlock(x, y, z) != ModRegistry.inventoryConnector) {
            clear();
            return;
        }

        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x + 0.5D - playerX, y + 0.5D - playerY, z + 0.5D - playerZ);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawing(GL11.GL_TRIANGLES);
            tessellator.setColorRGBA_F(0.12F, 0.65F, 1.0F, 0.16F);
            addVertices(tessellator, surfaceVertices);
            tessellator.draw();

            GL11.glLineWidth(1.0F);
            tessellator.startDrawing(GL11.GL_LINES);
            tessellator.setColorRGBA_F(0.2F, 0.85F, 1.0F, 0.72F);
            addVertices(tessellator, gridVertices);
            tessellator.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private void clear() {
        surfaceVertices = null;
        gridVertices = null;
        remainingTicks = 0;
    }

    private void addVertices(Tessellator tessellator, float[] vertices) {
        for (int i = 0; i < vertices.length; i += 3) {
            tessellator.addVertex(vertices[i], vertices[i + 1], vertices[i + 2]);
        }
    }

    private SphereMesh createSphereMesh(float radius) {
        int latitudeSegments = Math.min(MAX_LATITUDE_SEGMENTS, Math.max(8, (int) Math.ceil(Math.PI * radius)));
        int longitudeSegments = Math
            .min(MAX_LONGITUDE_SEGMENTS, Math.max(16, (int) Math.ceil(Math.PI * 2.0D * radius)));
        List<Float> surface = new ArrayList<>();
        List<Float> grid = new ArrayList<>();

        for (int latitude = 0; latitude < latitudeSegments; latitude++) {
            double lower = -Math.PI / 2.0D + Math.PI * latitude / latitudeSegments;
            double upper = -Math.PI / 2.0D + Math.PI * (latitude + 1) / latitudeSegments;
            for (int longitude = 0; longitude < longitudeSegments; longitude++) {
                double first = Math.PI * 2.0D * longitude / longitudeSegments;
                double second = Math.PI * 2.0D * (longitude + 1) / longitudeSegments;
                addTriangle(surface, radius, lower, first, upper, first, upper, second);
                addTriangle(surface, radius, lower, first, upper, second, lower, second);
                addSphereLine(grid, radius, lower, first, lower, second);
                addSphereLine(grid, radius, lower, first, upper, first);
            }
        }
        return new SphereMesh(toArray(surface), toArray(grid));
    }

    private void addTriangle(List<Float> mesh, double radius, double latitude1, double longitude1, double latitude2,
        double longitude2, double latitude3, double longitude3) {
        addSpherePoint(mesh, radius, latitude1, longitude1);
        addSpherePoint(mesh, radius, latitude2, longitude2);
        addSpherePoint(mesh, radius, latitude3, longitude3);
    }

    private void addSphereLine(List<Float> mesh, double radius, double latitude1, double longitude1, double latitude2,
        double longitude2) {
        addSpherePoint(mesh, radius, latitude1, longitude1);
        addSpherePoint(mesh, radius, latitude2, longitude2);
    }

    private void addSpherePoint(List<Float> mesh, double radius, double latitude, double longitude) {
        double ringRadius = Math.cos(latitude) * radius;
        mesh.add((float) (Math.cos(longitude) * ringRadius));
        mesh.add((float) (Math.sin(latitude) * radius));
        mesh.add((float) (Math.sin(longitude) * ringRadius));
    }

    private float[] toArray(List<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private static final class SphereMesh {

        private final float[] surfaceVertices;
        private final float[] gridVertices;

        private SphereMesh(float[] surfaceVertices, float[] gridVertices) {
            this.surfaceVertices = surfaceVertices;
            this.gridVertices = gridVertices;
        }
    }
}
