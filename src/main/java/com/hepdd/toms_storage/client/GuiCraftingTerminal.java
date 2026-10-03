package com.hepdd.toms_storage.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hepdd.toms_storage.ModNetwork;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.gui.SlotAction;
import com.hepdd.toms_storage.nei.TerminalOverlayHandler;
import com.hepdd.toms_storage.network.PacketTerminalAction;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;

import codechicken.nei.api.API;

public class GuiCraftingTerminal extends GuiStorageTerminal {

    private static final ResourceLocation CRAFTING_GUI = new ResourceLocation(
        "tomsstorage",
        "textures/gui/crafting_terminal.png");
    private static final ResourceLocation CLEAR_BUTTON = new ResourceLocation("tomsstorage", "dynamic/clear_button");
    private static final ResourceLocation CLEAR_BUTTON_HOVER = new ResourceLocation(
        "tomsstorage",
        "dynamic/clear_button_hover");
    private static final int[] CLEAR_ICON_PIXELS = { 0b11000011, 0b11100111, 0b01111110, 0b00111100, 0b00111100,
        0b01111110, 0b11100111, 0b11000011, 0 };
    private static final int[] CLEAR_ICON_SHADOW = { 0, 0, 0b10000001, 0b01000010, 0, 0, 0b00011000, 0b00100100,
        0b11000011 };
    private int storageRows = ContainerCraftingTerminal.MAX_STORAGE_ROWS;

    public GuiCraftingTerminal(InventoryPlayer playerInventory, TileEntityCraftingTerminal terminal) {
        super(new ContainerCraftingTerminal(playerInventory, terminal));
        xSize = ContainerCraftingTerminal.GUI_WIDTH;
        ySize = ContainerCraftingTerminal.GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        storageRows = Math.max(
            3,
            Math.min(
                ContainerCraftingTerminal.MAX_STORAGE_ROWS,
                (height - ContainerCraftingTerminal.BASE_HEIGHT - 42) / 18));
        ySize = ContainerCraftingTerminal.BASE_HEIGHT + storageRows * 18;
        ((ContainerCraftingTerminal) container).setGuiHeight(ySize);
        super.initGui();
        buttonList.add(new ClearButton(10, guiLeft + 92, guiTop + ySize - 156));
        buttonList.add(new AutoRefillButton(11, guiLeft + 135, guiTop + ySize - 154));
        API.registerGuiOverlay(GuiCraftingTerminal.class, "crafting", ContainerCraftingTerminal.GRID_X, ySize - 155);
        API.setGuiOffset(GuiCraftingTerminal.class, ContainerCraftingTerminal.GRID_X, ySize - 155);
        API.registerGuiOverlayHandler(
            GuiCraftingTerminal.class,
            new TerminalOverlayHandler(ContainerCraftingTerminal.GRID_X - 25, ySize - 161),
            "crafting");
    }

    @Override
    protected int getStorageRows() {
        return storageRows;
    }

    @Override
    protected int getStorageGridX() {
        return 9;
    }

    @Override
    protected int getSearchFieldWidth() {
        return 87;
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotId, int clickedButton, int clickType) {
        ContainerCraftingTerminal crafting = (ContainerCraftingTerminal) container;
        if (slot != null && slot.slotNumber == ContainerCraftingTerminal.GRID_SLOT_START - 1) {
            if (clickType != 6) {
                crafting.beginCraftingClick();
                ModNetwork.channel
                    .sendToServer(PacketTerminalAction.craftResult(container.windowId, clickedButton, clickType));
            }
            return;
        }
        // Vanilla prediction needs the confirmed cursor from outstanding result clicks.
        if (crafting.hasPendingCraftingClick()) return;
        super.handleMouseClick(slot, slotId, clickedButton, clickType);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 10) {
            ModNetwork.channel.sendToServer(new PacketTerminalAction(SlotAction.CLEAR_GRID, null));
            return;
        }
        if (button.id == 11) {
            ContainerCraftingTerminal crafting = (ContainerCraftingTerminal) container;
            boolean enabled = !crafting.isAutoRefillEnabled();
            crafting.setAutoRefillEnabled(enabled);
            ModNetwork.channel.sendToServer(PacketTerminalAction.autoRefill(crafting.windowId, enabled));
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    protected List<String> getButtonTooltip(GuiButton button) {
        if (button.id == 10) return Collections.singletonList(I18n.format("tooltip.tomsstorage.clear_grid"));
        if (button.id == 11) {
            boolean enabled = ((ContainerCraftingTerminal) container).isAutoRefillEnabled();
            return Arrays.asList(
                I18n.format("tooltip.tomsstorage.auto_refill." + (enabled ? 1 : 0)),
                I18n.format("tooltip.tomsstorage.auto_refill.description"));
        }
        return super.getButtonTooltip(button);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
        fontRendererObj.drawString(I18n.format("gui.tomsstorage.terminal"), 8, 6, 0x404040);
        fontRendererObj.drawString(I18n.format("container.inventory"), 8, ySize - 93, 0x404040);
    }

    @Override
    protected void drawTerminalBackground() {
        // Resource packs share the existing 256x256 atlas across GUI heights.
        drawStorageStrip(0, 0, 17);
        for (int row = 0; row < storageRows; row++) {
            drawStorageStrip(17 + row * 18, row == 0 ? 17 : 35, 18);
        }
        int storageBottom = 17 + storageRows * 18;
        drawStorageStrip(storageBottom - 1, 106, 1);
        for (int row = 0; row < 3; row++) drawStorageStrip(storageBottom + row, 107, 1);
        func_152125_a(guiLeft, guiTop + storageBottom + 3, 0, 170, 176, 1, 176, 84, 256, 256);
        drawTexturedModalRect(guiLeft + 174, guiTop + storageBottom + 3, 174, 109, 19, 4);
        drawTexturedModalRect(guiLeft + 193, guiTop + storageBottom + 3, 192, 109, 2, 4);
        func_152125_a(guiLeft + 174, guiTop + 18, 173, 36, 14, 1, 14, storageRows * 18 - 2, 256, 256);
        drawTexturedModalRect(guiLeft + 174, guiTop + storageBottom - 1, 173, 105, 14, 1);

        drawTexturedModalRect(guiLeft + 8, guiTop + ySize - 163, 7, 108, 162, 1);
        func_152125_a(guiLeft + 8, guiTop + ySize - 162, 7, 109, 162, 1, 162, 2, 256, 256);
        func_152125_a(guiLeft + 8, guiTop + ySize - 160, 7, 165, 162, 1, 162, 64, 256, 256);
        drawTexturedModalRect(guiLeft + 8, guiTop + ySize - 96, 7, 169, 162, 1);
        drawTexturedModalRect(guiLeft + 36, guiTop + ySize - 156, 25, 110, 54, 54);
        drawTexturedModalRect(guiLeft + 96, guiTop + ySize - 137, 85, 129, 23, 16);
        drawTexturedModalRect(guiLeft + 126, guiTop + ySize - 142, 115, 124, 26, 26);
        drawTexturedModalRect(guiLeft, guiTop + ySize - 84, 0, 173, 176, 83);
    }

    private void drawStorageStrip(int y, int textureY, int height) {
        drawTexturedModalRect(guiLeft, guiTop + y, 0, textureY, 7, height);
        drawTexturedModalRect(guiLeft + 7, guiTop + y, 6, textureY, 188, height);
    }

    @Override
    protected ResourceLocation getGuiTexture() {
        return CRAFTING_GUI;
    }

    private class AutoRefillButton extends ClearButton {

        private AutoRefillButton(int id, int x, int y) {
            super(id, x, y);
        }

        @Override
        protected void drawIcon() {
            if (!((ContainerCraftingTerminal) container).isAutoRefillEnabled()) return;
            drawRect(4, 6, 6, 10, 0xFFFFFFFF);
            drawRect(6, 10, 8, 12, 0xFFFFFFFF);
            drawRect(8, 8, 10, 10, 0xFFFFFFFF);
            drawRect(10, 6, 12, 8, 0xFFFFFFFF);
            drawRect(12, 4, 14, 6, 0xFFFFFFFF);
        }
    }

    private class ClearButton extends GuiButton {

        private ClearButton(int id, int x, int y) {
            super(id, x, y, 8, 8, "");
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            int hover = enabled && mouseX >= xPosition
                && mouseY >= yPosition
                && mouseX < xPosition + width
                && mouseY < yPosition + height ? 2 : 1;
            field_146123_n = hover == 2;
            TextureManager textures = minecraft.getTextureManager();
            ResourceLocation texture = hover == 2 ? CLEAR_BUTTON_HOVER : CLEAR_BUTTON;
            if (textures.getTexture(texture) == null) textures.loadTexture(texture, new ClearButtonTexture(hover == 2));
            textures.bindTexture(texture);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            // AE renders a 16x16 button at half size.
            GL11.glPushMatrix();
            GL11.glTranslatef(xPosition, yPosition, 0.0F);
            GL11.glScalef(0.5F, 0.5F, 1.0F);
            drawTexturedModalRect(0, 0, 240, 240, 16, 16);
            drawIcon();
            GL11.glPopMatrix();
            if (hover == 2) {
                GL11.glColorMask(true, true, true, false);
                drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 0x40FFFFFF);
                GL11.glColorMask(true, true, true, true);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        protected void drawIcon() {
            drawTexturedModalRect(0, 0, 96, 0, 16, 16);
        }
    }

    private static BufferedImage createClearButtonTexture(BufferedImage atlas, boolean hover) {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        int textureX = hover ? 217 : 206;
        int outline = atlas.getRGB(textureX + 1, 20);
        int highlight = atlas.getRGB(textureX, 11);
        int background = atlas.getRGB(textureX + (hover ? 1 : 2), 12);
        int bevel = atlas.getRGB(textureX + 2, 18);
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                int color = outline;
                if (row >= 1 && row < 13 && col >= 1 && col < 15) color = highlight;
                if (row >= 2 && row < 12 && col >= 2 && col < 14) color = background;
                if (row >= 13 && row < 15 && col >= 1 && col < 15) color = bevel;
                image.setRGB(240 + col, 240 + row, color);
            }
        }
        for (int row = 0; row < CLEAR_ICON_PIXELS.length; row++) {
            for (int col = 0; col < 8; col++) {
                int pixel = 1 << (7 - col);
                if ((CLEAR_ICON_PIXELS[row] & pixel) != 0) {
                    image.setRGB(100 + col, 3 + row, atlas.getRGB(8, 106));
                } else if ((CLEAR_ICON_SHADOW[row] & pixel) != 0) {
                    image.setRGB(100 + col, 3 + row, atlas.getRGB(196, 14));
                }
            }
        }
        return image;
    }

    private static class ClearButtonTexture extends SimpleTexture {

        private final boolean hover;

        private ClearButtonTexture(boolean hover) {
            super(CRAFTING_GUI);
            this.hover = hover;
        }

        @Override
        public void loadTexture(IResourceManager resourceManager) throws IOException {
            deleteGlTexture();
            try (InputStream stream = resourceManager.getResource(CRAFTING_GUI)
                .getInputStream()) {
                BufferedImage image = createClearButtonTexture(ImageIO.read(stream), hover);
                TextureUtil.uploadTextureImageAllocate(getGlTextureId(), image, false, false);
            }
        }
    }
}
