package com.hepdd.toms_storage.client;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import com.hepdd.toms_storage.ModNetwork;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.StoredItemStack.IStoredItemStackComparator;
import com.hepdd.toms_storage.StoredItemStack.SortingTypes;
import com.hepdd.toms_storage.gui.ContainerStorageTerminal;
import com.hepdd.toms_storage.gui.SlotAction;
import com.hepdd.toms_storage.network.PacketTerminalAction;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public class GuiStorageTerminal extends GuiContainer {

    private static final int DIVISION_BASE = 1000;
    private static final char[] POSTFIXES = "KMGTPE".toCharArray();
    private static final DecimalFormat FORMAT;
    private static final ResourceLocation STORAGE_GUI = new ResourceLocation(
        "tomsstorage",
        "textures/gui/storage_terminal.png");
    private static final ResourceLocation CREATIVE_TABS = new ResourceLocation(
        "textures/gui/container/creative_inventory/tabs.png");

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setDecimalSeparator('.');
        FORMAT = new DecimalFormat(".#;0.#", symbols);
    }

    protected final ContainerStorageTerminal container;
    private GuiTextField searchField;
    private List<StoredItemStack> sortedStacks = new ArrayList<>();
    private int scrollRow;
    private int sortType;
    private boolean sortReversed;
    private int hoveredStack = -1;
    private int lastShiftClickSlot = -1;
    private int lastShiftClickButton = -1;
    private long lastShiftClickTime;
    private ItemStack lastShiftClickStack;

    public GuiStorageTerminal(InventoryPlayer playerInventory, TileEntityStorageTerminal terminal) {
        this(new ContainerStorageTerminal(playerInventory, terminal));
    }

    protected GuiStorageTerminal(ContainerStorageTerminal container) {
        super(container);
        this.container = container;
        xSize = 194;
        ySize = 202;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        searchField = new GuiTextField(fontRendererObj, guiLeft + 82, guiTop + 6, 89, fontRendererObj.FONT_HEIGHT);
        searchField.setMaxStringLength(100);
        searchField.setText(container.search);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setTextColor(0xFFFFFF);
        buttonList.clear();
        buttonList.add(new TerminalButton(0, guiLeft - 18, guiTop + 5, 0));
        buttonList.add(new TerminalButton(1, guiLeft - 18, guiTop + 23, 1));
        updateSearch();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        searchField.updateCursorCounter();
        boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (shiftDown) {
            updateQuantitiesWithoutSorting();
        } else {
            updateSearch();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            sortType = (sortType + 1) % SortingTypes.VALUES.length;
        } else if (button.id == 1) {
            sortReversed = !sortReversed;
        }
        ModNetwork.channel.sendToServer(PacketTerminalAction.sorting(packSorting()));
        updateSearch();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.thePlayer.closeScreen();
            return;
        }
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            onSearchChanged();
            return;
        }
        if (hoveredStack >= 0 && hoveredStack < sortedStacks.size()) {
            ItemStack stack = sortedStacks.get(hoveredStack)
                .getStack();
            if (keyCode == Keyboard.KEY_R) {
                GuiCraftingRecipe.openRecipeGui("item", stack);
                return;
            }
            if (keyCode == Keyboard.KEY_U) {
                GuiUsageRecipe.openRecipeGui("item", stack);
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 1 && isMouseOverSearchField(mouseX, mouseY)) {
            searchField.setFocused(true);
            if (!searchField.getText()
                .isEmpty()) {
                searchField.setText("");
                onSearchChanged();
            }
            return;
        }
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        int slot = getVirtualSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            int index = scrollRow * ContainerStorageTerminal.COLUMNS + slot;
            ItemStack stack = index >= 0 && index < sortedStacks.size() ? sortedStacks.get(index)
                .getStack() : null;
            if (index >= 0 && index < sortedStacks.size()) {
                SlotAction action = mouseButton == 1 ? SlotAction.GET_HALF : SlotAction.PULL_OR_PUSH_STACK;
                if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                    action = mouseButton == 1 ? SlotAction.PULL_ONE : SlotAction.SHIFT_PULL;
                }
                ModNetwork.channel.sendToServer(new PacketTerminalAction(action, stack));
            } else if (mc.thePlayer.inventory.getItemStack() != null) {
                ModNetwork.channel.sendToServer(new PacketTerminalAction(SlotAction.PULL_OR_PUSH_STACK, null));
            }
            return;
        }
        Slot playerSlot = getPlayerSlotAt(mouseX, mouseY);
        if (playerSlot != null) {
            ItemStack clickedStack = playerSlot.getStack();
            if (Keyboard.isKeyDown(Keyboard.KEY_SPACE) && clickedStack != null) {
                ModNetwork.channel
                    .sendToServer(new PacketTerminalAction(SlotAction.PUSH_MATCHING_FROM_PLAYER, clickedStack));
                return;
            }

            boolean shiftDown = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
            ItemStack held = mc.thePlayer.inventory.getItemStack();
            if (shiftDown && held != null) {
                long time = net.minecraft.client.Minecraft.getSystemTime();
                boolean doubleClick = lastShiftClickSlot == playerSlot.slotNumber && lastShiftClickButton == mouseButton
                    && time - lastShiftClickTime < 250L;
                ItemStack template = clickedStack != null ? clickedStack : lastShiftClickStack;
                if (doubleClick && template != null) {
                    ModNetwork.channel
                        .sendToServer(new PacketTerminalAction(SlotAction.PUSH_MATCHING_FROM_PLAYER, template));
                    clearLastShiftClick();
                    return;
                }
                lastShiftClickSlot = playerSlot.slotNumber;
                lastShiftClickButton = mouseButton;
                lastShiftClickTime = time;
                lastShiftClickStack = clickedStack == null ? null : clickedStack.copy();
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void handleMouseClick(Slot slot, int slotId, int clickedButton, int clickType) {
        if (slot != null && slot.inventory == mc.thePlayer.inventory
            && Keyboard.isKeyDown(Keyboard.KEY_SPACE)
            && slot.getHasStack()) {
            ModNetwork.channel
                .sendToServer(new PacketTerminalAction(SlotAction.PUSH_MATCHING_FROM_PLAYER, slot.getStack()));
            return;
        }
        super.handleMouseClick(slot, slotId, clickedButton, clickType);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hoveredStack = getHoveredStack(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        searchField.drawTextBox();
        drawButtonTooltip(mouseX, mouseY);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (wheel != 0 && isMouseInScrollArea(mouseX, mouseY)) {
            int maxRows = Math.max(
                0,
                (sortedStacks.size() + ContainerStorageTerminal.COLUMNS - 1) / ContainerStorageTerminal.COLUMNS
                    - ContainerStorageTerminal.ROWS);
            scrollRow -= wheel > 0 ? 1 : -1;
            if (scrollRow < 0) scrollRow = 0;
            if (scrollRow > maxRows) scrollRow = maxRows;
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        for (int i = 0; i < ContainerStorageTerminal.VISIBLE_STACKS; i++) {
            int index = scrollRow * ContainerStorageTerminal.COLUMNS + i;
            if (index >= sortedStacks.size()) break;
            int x = 8 + (i % ContainerStorageTerminal.COLUMNS) * 18;
            int y = 18 + (i / ContainerStorageTerminal.COLUMNS) * 18;
            if (hoveredStack == index) drawRect(x, y, x + 16, y + 16, 0x80FFFFFF);
            drawStoredStack(sortedStacks.get(index), x, y);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawDefaultBackground();
        mc.getTextureManager()
            .bindTexture(getGuiTexture());
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
        drawScrollBar();
    }

    protected ResourceLocation getGuiTexture() {
        return STORAGE_GUI;
    }

    private void drawScrollBar() {
        int maxRows = Math.max(
            0,
            (sortedStacks.size() + ContainerStorageTerminal.COLUMNS - 1) / ContainerStorageTerminal.COLUMNS
                - ContainerStorageTerminal.ROWS);
        int scrollOffset = maxRows == 0 ? 0 : (int) ((72.0F * scrollRow) / maxRows);
        float previousZLevel = zLevel;
        float previousItemZLevel = itemRender.zLevel;
        mc.getTextureManager()
            .bindTexture(CREATIVE_TABS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        zLevel = 0.0F;
        itemRender.zLevel = 0.0F;
        drawTexturedModalRect(guiLeft + 174, guiTop + 18 + scrollOffset, maxRows > 0 ? 232 : 244, 0, 12, 15);
        zLevel = previousZLevel;
        itemRender.zLevel = previousItemZLevel;
    }

    private void drawStoredStack(StoredItemStack storedStack, int x, int y) {
        ItemStack stack = storedStack.getStack()
            .copy();
        stack.stackSize = 1;
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        itemRender.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y, null);
        drawStackSize(formatNumber(storedStack.getQuantity()), x, y);
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    public StoredItemStack getVirtualStackUnderMouse(int mouseX, int mouseY) {
        int index = getHoveredStack(mouseX, mouseY);
        return index >= 0 && index < sortedStacks.size() ? sortedStacks.get(index) : null;
    }

    private void drawButtonTooltip(int mouseX, int mouseY) {
        for (Object object : buttonList) {
            if (!(object instanceof GuiButton)) continue;
            GuiButton button = (GuiButton) object;
            if (mouseX < button.xPosition || mouseY < button.yPosition
                || mouseX >= button.xPosition + button.width
                || mouseY >= button.yPosition + button.height) continue;
            List<String> tooltip = new ArrayList<>();
            if (button.id == 0) {
                tooltip.add(I18n.format("tooltip.tomsstorage.sorting." + sortType));
            } else if (button.id == 1) {
                tooltip.add(I18n.format("tooltip.tomsstorage.direction." + (sortReversed ? 1 : 0)));
            }
            if (!tooltip.isEmpty()) drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }

    private void drawStackSize(String stackSize, int x, int y) {
        float scale = 0.6F;
        float inverseScale = 1.0F / scale;
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPushMatrix();
        GL11.glScalef(scale, scale, scale);
        GL11.glTranslatef(0.0F, 0.0F, 450.0F);
        int textX = (int) ((x + 16.0F - fontRendererObj.getStringWidth(stackSize) * scale) * inverseScale);
        int textY = (int) ((y + 16.0F - 7.0F * scale) * inverseScale);
        fontRendererObj.drawStringWithShadow(stackSize, textX, textY, 0xFFFFFF);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    private void updateSearch() {
        sortedStacks = new ArrayList<>();
        String text = searchField == null ? "" : searchField.getText();
        Pattern pattern = compilePattern(text.startsWith("@") ? text.substring(1) : text);
        boolean modSearch = text.startsWith("@");
        for (StoredItemStack stack : container.clientStacks) {
            if (matches(stack.getStack(), pattern, modSearch)) sortedStacks.add(stack);
        }
        IStoredItemStackComparator comparator = SortingTypes.VALUES[sortType].create(sortReversed);
        Collections.sort(sortedStacks, comparator);
    }

    private void onSearchChanged() {
        scrollRow = 0;
        ModNetwork.channel.sendToServer(PacketTerminalAction.search(searchField.getText()));
        updateSearch();
    }

    private void updateQuantitiesWithoutSorting() {
        List<StoredItemStack> updated = new ArrayList<>();
        for (StoredItemStack displayed : sortedStacks) {
            StoredItemStack current = findCurrentStack(displayed);
            updated.add(new StoredItemStack(displayed.getStack(), current == null ? 0 : current.getQuantity()));
        }
        sortedStacks = updated;
    }

    private StoredItemStack findCurrentStack(StoredItemStack displayed) {
        for (StoredItemStack current : container.clientStacks) {
            if (displayed.equals(current)) return current;
        }
        return null;
    }

    private boolean matches(ItemStack stack, Pattern pattern, boolean modSearch) {
        if (stack == null) return false;
        if (pattern == null) return true;
        if (modSearch) return matchesMod(stack, pattern);
        String searchText = searchField == null ? "" : searchField.getText();
        if (SearchMatcher.matchesName(stack.getDisplayName(), searchText, pattern)) return true;
        @SuppressWarnings("unchecked")
        List<String> tooltip = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        for (String line : tooltip) {
            if (SearchMatcher.matchesTooltip(line, searchText, pattern)) return true;
        }
        return false;
    }

    private boolean matchesMod(ItemStack stack, Pattern pattern) {
        UniqueIdentifier identifier = GameRegistry.findUniqueIdentifierFor(stack.getItem());
        if (identifier != null) {
            if (pattern.matcher(identifier.modId.toLowerCase(Locale.ROOT))
                .find()) return true;
            ModContainer mod = Loader.instance()
                .getIndexedModList()
                .get(identifier.modId);
            if (mod != null && pattern.matcher(
                mod.getName()
                    .toLowerCase(Locale.ROOT))
                .find()) return true;
        }
        return pattern.matcher(
            stack.getItem()
                .getUnlocalizedName()
                .toLowerCase(Locale.ROOT))
            .find();
    }

    private Pattern compilePattern(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            return Pattern.compile(text.toLowerCase(Locale.ROOT), Pattern.CASE_INSENSITIVE);
        } catch (RuntimeException ignored) {
            return Pattern.compile(Pattern.quote(text.toLowerCase(Locale.ROOT)), Pattern.CASE_INSENSITIVE);
        }
    }

    private int getVirtualSlotAt(int mouseX, int mouseY) {
        int relX = mouseX - guiLeft - 8;
        int relY = mouseY - guiTop - 18;
        if (relX < 0 || relY < 0) return -1;
        int col = relX / 18;
        int row = relY / 18;
        if (col < 0 || col >= ContainerStorageTerminal.COLUMNS || row < 0 || row >= ContainerStorageTerminal.ROWS)
            return -1;
        return row * ContainerStorageTerminal.COLUMNS + col;
    }

    private int getHoveredStack(int mouseX, int mouseY) {
        int slot = getVirtualSlotAt(mouseX, mouseY);
        if (slot < 0) return -1;
        int index = scrollRow * ContainerStorageTerminal.COLUMNS + slot;
        return index >= 0 && index < sortedStacks.size() ? index : -1;
    }

    private boolean isMouseInItemGrid(int mouseX, int mouseY) {
        int relX = mouseX - guiLeft - 8;
        int relY = mouseY - guiTop - 18;
        return relX >= 0 && relY >= 0
            && relX < ContainerStorageTerminal.COLUMNS * 18
            && relY < ContainerStorageTerminal.ROWS * 18;
    }

    private boolean isMouseInScrollArea(int mouseX, int mouseY) {
        if (isMouseInItemGrid(mouseX, mouseY)) return true;
        int relX = mouseX - guiLeft - 174;
        int relY = mouseY - guiTop - 18;
        return relX >= 0 && relY >= 0 && relX < 12 && relY < ContainerStorageTerminal.ROWS * 18;
    }

    private Slot getPlayerSlotAt(int mouseX, int mouseY) {
        for (Object object : inventorySlots.inventorySlots) {
            Slot slot = (Slot) object;
            if (slot.inventory == mc.thePlayer.inventory && isMouseOverSlot(slot, mouseX, mouseY)) return slot;
        }
        return null;
    }

    private boolean isMouseOverSlot(Slot slot, int mouseX, int mouseY) {
        int x = guiLeft + slot.xDisplayPosition;
        int y = guiTop + slot.yDisplayPosition;
        return mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16;
    }

    private boolean isMouseOverSearchField(int mouseX, int mouseY) {
        return searchField != null && mouseX >= searchField.xPosition
            && mouseX < searchField.xPosition + searchField.width
            && mouseY >= searchField.yPosition
            && mouseY < searchField.yPosition + searchField.height;
    }

    private void clearLastShiftClick() {
        lastShiftClickSlot = -1;
        lastShiftClickButton = -1;
        lastShiftClickTime = 0L;
        lastShiftClickStack = null;
    }

    private int packSorting() {
        return sortType | (sortReversed ? 2 : 0);
    }

    private String formatNumber(long number) {
        if (number <= 9999) return Long.toString(number);
        long base = number;
        double last = base * 1000.0D;
        int exponent = -1;
        while (Long.toString(base)
            .length() + 1 > 4 && exponent + 1 < POSTFIXES.length) {
            last = base;
            base /= DIVISION_BASE;
            exponent++;
        }
        String withPrecision = FORMAT.format(last / DIVISION_BASE) + POSTFIXES[exponent];
        String withoutPrecision = base + String.valueOf(POSTFIXES[exponent]);
        return withPrecision.length() <= 4 ? withPrecision : withoutPrecision;
    }

    private class TerminalButton extends GuiButton {

        private final int tile;

        private TerminalButton(int id, int x, int y, int tile) {
            super(id, x, y, 16, 16, "");
            this.tile = tile;
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            int state = id == 0 ? sortType : sortReversed ? 1 : 0;
            minecraft.getTextureManager()
                .bindTexture(getGuiTexture());
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            drawTexturedModalRect(xPosition, yPosition, 194 + state * 16, 30 + tile * 16, width, height);
        }
    }
}
