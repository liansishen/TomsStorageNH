package com.hepdd.toms_storage.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.hepdd.toms_storage.ModNetwork;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.gui.SlotAction;
import com.hepdd.toms_storage.network.PacketTerminalAction;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;

public class GuiCraftingTerminal extends GuiStorageTerminal {

    private static final ResourceLocation CRAFTING_GUI = new ResourceLocation(
        "tomsstorage",
        "textures/gui/crafting_terminal.png");

    public GuiCraftingTerminal(InventoryPlayer playerInventory, TileEntityCraftingTerminal terminal) {
        super(new ContainerCraftingTerminal(playerInventory, terminal));
        ySize = 256;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.add(new ClearButton(10, guiLeft + 80, guiTop + 110));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 10) {
            ModNetwork.channel.sendToServer(new PacketTerminalAction(SlotAction.CLEAR_GRID, null));
            return;
        }
        super.actionPerformed(button);
    }

    @Override
    protected ResourceLocation getGuiTexture() {
        return CRAFTING_GUI;
    }

    private class ClearButton extends GuiButton {

        private ClearButton(int id, int x, int y) {
            super(id, x, y, 11, 11, "");
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            minecraft.getTextureManager()
                .bindTexture(getGuiTexture());
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            int hover = mouseX >= xPosition && mouseY >= yPosition
                && mouseX < xPosition + width
                && mouseY < yPosition + height ? 2 : 1;
            drawTexturedModalRect(xPosition, yPosition, 194 + hover * 11, 10, width, height);
        }
    }
}
