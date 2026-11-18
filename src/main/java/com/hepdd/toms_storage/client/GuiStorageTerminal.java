package com.hepdd.toms_storage.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

import com.hepdd.toms_storage.Config;
import com.hepdd.toms_storage.ModNetwork;
import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.StoredItemStack.IStoredItemStackComparator;
import com.hepdd.toms_storage.StoredItemStack.SortingTypes;
import com.hepdd.toms_storage.client.SearchMatcher.SearchQuery;
import com.hepdd.toms_storage.client.SearchMatcher.SearchTarget;
import com.hepdd.toms_storage.gui.ContainerStorageTerminal;
import com.hepdd.toms_storage.gui.SlotAction;
import com.hepdd.toms_storage.network.PacketTerminalAction;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

import codechicken.nei.LayoutManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier;

public class GuiStorageTerminal extends GuiContainer {

    private static final long TOOLTIP_FRAME_BUDGET_NS = 4_000_000L;
    private static final int[] SEARCH_PRESETS = { 0, Config.SEARCH_AUTO_FOCUS,
        Config.SEARCH_AUTO_FOCUS | Config.SEARCH_KEEP_TEXT,
        Config.SEARCH_AUTO_FOCUS | Config.SEARCH_KEEP_TEXT | Config.SEARCH_SYNC_NEI };
    private static final ResourceLocation STORAGE_GUI = new ResourceLocation(
        "tomsstorage",
        "textures/gui/storage_terminal.png");
    private static final ResourceLocation CREATIVE_TABS = new ResourceLocation(
        "textures/gui/container/creative_inventory/tabs.png");

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
    private final Map<StoredItemStack, SearchDocument> searchCache = new HashMap<>();
    private final List<StoredItemStack> pendingTooltipStacks = new ArrayList<>();
    private int pendingTooltipCursor;
    private SearchQuery activeSearchQuery = SearchQuery.EMPTY;
    private int lastClientDataRevision = -1;
    private int lastClientKindsRevision = -1;
    private int searchMode;
    private boolean searchMigrated;
    private String originalNeiSearch;
    private String lastTerminalSearch = "";
    private String lastNeiSearch = "";
    private String searchCacheContext = "";

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
        int localSorting = Config.normalizeSorting(Config.terminalSorting);
        sortType = localSorting & 1;
        sortReversed = (localSorting & 2) != 0;
        searchMode = Config.normalizeSearchMode(Config.terminalSearchMode);
        searchMigrated = true;
        searchField = new GuiTextField(
            fontRendererObj,
            guiLeft + getStorageGridX() + 74,
            guiTop + 6,
            getSearchFieldWidth(),
            fontRendererObj.FONT_HEIGHT);
        searchField.setMaxStringLength(100);
        String initialSearch = Config.hasSearchOption(Config.SEARCH_KEEP_TEXT) ? Config.terminalLastSearch : "";
        searchField.setText(initialSearch);
        searchField.setEnableBackgroundDrawing(false);
        searchField.setTextColor(0xFFFFFF);
        searchField.setFocused(hasSearchOption(Config.SEARCH_AUTO_FOCUS));
        buttonList.clear();
        buttonList.add(new TerminalButton(0, guiLeft - 18, guiTop + 5, 0));
        buttonList.add(new TerminalButton(1, guiLeft - 18, guiTop + 23, 1));
        buttonList.add(new SearchModeButton(2, guiLeft - 18, guiTop + 41));
        beginNeiSyncIfEnabled();
        searchCacheContext = getSearchCacheContext();
        lastClientDataRevision = container.getClientDataRevision();
        lastClientKindsRevision = container.getClientKindsRevision();
        updateSearch();
    }

    @Override
    public void onGuiClosed() {
        saveSearchSettings();
        restoreNeiSearch();
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        searchField.updateCursorCounter();
        invalidateSearchCacheWhenNeeded();
        synchronizeNeiSearch();
        updateForChangedTerminalData();
    }

    public void refreshFromTerminalData() {
        if (searchField != null) {
            updateForChangedTerminalData();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            sortType = (sortType + 1) % SortingTypes.VALUES.length;
        } else if (button.id == 1) {
            sortReversed = !sortReversed;
        } else if (button.id == 2) {
            boolean wasSyncing = hasSearchOption(Config.SEARCH_SYNC_NEI);
            searchMode = nextSearchPreset(searchMode);
            if (!wasSyncing && hasSearchOption(Config.SEARCH_SYNC_NEI)) beginNeiSyncIfEnabled();
            if (wasSyncing && !hasSearchOption(Config.SEARCH_SYNC_NEI)) restoreNeiSearch();
            searchField.setFocused(hasSearchOption(Config.SEARCH_AUTO_FOCUS));
            saveSearchSettings();
            return;
        } else {
            return;
        }
        Config.saveTerminalSorting(packSorting());
        updateSearch();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.thePlayer.closeScreen();
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
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            onSearchChanged();
            return;
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
        updateForChangedTerminalData();
        processPendingTooltips();
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
                    - getStorageRows());
            scrollRow -= wheel > 0 ? 1 : -1;
            if (scrollRow < 0) scrollRow = 0;
            if (scrollRow > maxRows) scrollRow = maxRows;
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        for (int i = 0; i < getStorageRows() * ContainerStorageTerminal.COLUMNS; i++) {
            int index = scrollRow * ContainerStorageTerminal.COLUMNS + i;
            if (index >= sortedStacks.size()) break;
            int x = getStorageGridX() + (i % ContainerStorageTerminal.COLUMNS) * 18;
            int y = 18 + (i / ContainerStorageTerminal.COLUMNS) * 18;
            drawStoredStack(sortedStacks.get(index), x, y);
        }
        int slot = getVirtualSlotAt(mouseX, mouseY);
        if (slot >= 0) {
            int x = getStorageGridX() + (slot % ContainerStorageTerminal.COLUMNS) * 18;
            int y = 18 + (slot / ContainerStorageTerminal.COLUMNS) * 18;
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColorMask(true, true, true, false);
            drawGradientRect(x, y, x + 16, y + 16, 0x80FFFFFF, 0x80FFFFFF);
            GL11.glColorMask(true, true, true, true);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawDefaultBackground();
        mc.getTextureManager()
            .bindTexture(getGuiTexture());
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        drawTerminalBackground();
        drawScrollBar();
    }

    protected void drawTerminalBackground() {
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);
    }

    protected int getStorageRows() {
        return ContainerStorageTerminal.ROWS;
    }

    protected int getStorageGridX() {
        return 8;
    }

    protected int getSearchFieldWidth() {
        return 89;
    }

    protected ResourceLocation getGuiTexture() {
        return STORAGE_GUI;
    }

    private void drawScrollBar() {
        int maxRows = Math.max(
            0,
            (sortedStacks.size() + ContainerStorageTerminal.COLUMNS - 1) / ContainerStorageTerminal.COLUMNS
                - getStorageRows());
        int scrollOffset = maxRows == 0 ? 0 : (int) (((getStorageRows() * 18 - 18.0F) * scrollRow) / maxRows);
        float previousZLevel = zLevel;
        float previousItemZLevel = itemRender.zLevel;
        mc.getTextureManager()
            .bindTexture(CREATIVE_TABS);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        zLevel = 0.0F;
        itemRender.zLevel = 0.0F;
        drawTexturedModalRect(
            guiLeft + getStorageGridX() + 166,
            guiTop + 18 + scrollOffset,
            maxRows > 0 ? 232 : 244,
            0,
            12,
            15);
        zLevel = previousZLevel;
        itemRender.zLevel = previousItemZLevel;
    }

    private void drawStoredStack(StoredItemStack storedStack, int x, int y) {
        ItemStack stack = storedStack.getStack();
        if (stack == null) return;
        RenderHelper.enableGUIStandardItemLighting();
        itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y);
        itemRender.renderItemOverlayIntoGUI(fontRendererObj, mc.getTextureManager(), stack, x, y, null);
        if (stack.stackSize != 1) stack.stackSize = 1;
        drawStackSize(storedStack.getFormattedCount(SearchMatcher::formatNumber), x, y);
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
            if (!button.visible) continue;
            if (mouseX < button.xPosition || mouseY < button.yPosition
                || mouseX >= button.xPosition + button.width
                || mouseY >= button.yPosition + button.height) continue;
            List<String> tooltip = getButtonTooltip(button);
            if (!tooltip.isEmpty()) drawHoveringText(tooltip, mouseX, mouseY, fontRendererObj);
        }
    }

    protected List<String> getButtonTooltip(GuiButton button) {
        List<String> tooltip = new ArrayList<>();
        if (button.id == 0) {
            tooltip.add(I18n.format("tooltip.tomsstorage.sorting." + sortType));
        } else if (button.id == 1) {
            tooltip.add(I18n.format("tooltip.tomsstorage.direction." + (sortReversed ? 1 : 0)));
        } else if (button.id == 2) {
            tooltip.add(I18n.format("tooltip.tomsstorage.search_mode." + searchMode));
        }
        return tooltip;
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
        pendingTooltipStacks.clear();
        pendingTooltipCursor = 0;

        String rawText = searchField == null ? "" : searchField.getText();
        activeSearchQuery = SearchMatcher.parseSearchQuery(rawText);
        if (activeSearchQuery.isEmpty()) {
            sortedStacks.addAll(container.clientStacks);
            sortAndClampScroll();
            return;
        }

        long deadlineNs = System.nanoTime() + TOOLTIP_FRAME_BUDGET_NS;
        boolean budgetExhausted = false;
        for (StoredItemStack stored : container.clientStacks) {
            if (stored == null || stored.getStack() == null) continue;
            SearchDocument document = getSearchDocument(stored);
            if (document == null) continue;

            if (activeSearchQuery.target == SearchTarget.MOD_ONLY) {
                ensureModTexts(document, stored.getStack());
                if (SearchMatcher.matchesMod(document.modTexts, activeSearchQuery.pattern)) {
                    sortedStacks.add(stored);
                }
                continue;
            }

            if (activeSearchQuery.target == SearchTarget.NAME_AND_TOOLTIP && SearchMatcher
                .matchesName(document.displayName, activeSearchQuery.queryText, activeSearchQuery.pattern)) {
                sortedStacks.add(stored);
                continue;
            }

            if (document.tooltip != null) {
                if (SearchMatcher.matchesTooltipLines(document.tooltip, activeSearchQuery)) {
                    sortedStacks.add(stored);
                }
            } else if (!budgetExhausted) {
                loadTooltip(document, stored.getStack());
                if (System.nanoTime() >= deadlineNs) {
                    budgetExhausted = true;
                }
                if (SearchMatcher.matchesTooltipLines(document.tooltip, activeSearchQuery)) {
                    sortedStacks.add(stored);
                }
            } else {
                pendingTooltipStacks.add(stored);
            }
        }
        sortAndClampScroll();
    }

    private void processPendingTooltips() {
        if (pendingTooltipCursor >= pendingTooltipStacks.size()) return;
        if (activeSearchQuery.isEmpty() || activeSearchQuery.target == SearchTarget.MOD_ONLY) {
            pendingTooltipStacks.clear();
            pendingTooltipCursor = 0;
            return;
        }

        long deadlineNs = System.nanoTime() + TOOLTIP_FRAME_BUDGET_NS;
        boolean addedAny = false;
        while (pendingTooltipCursor < pendingTooltipStacks.size()) {
            StoredItemStack stored = pendingTooltipStacks.get(pendingTooltipCursor++);
            if (stored == null || stored.getStack() == null) continue;
            SearchDocument document = getSearchDocument(stored);
            if (document == null) continue;
            if (document.tooltip == null) {
                loadTooltip(document, stored.getStack());
            }
            if (SearchMatcher.matchesTooltipLines(document.tooltip, activeSearchQuery)) {
                sortedStacks.add(stored);
                addedAny = true;
            }
            if (System.nanoTime() >= deadlineNs) break;
        }

        if (pendingTooltipCursor >= pendingTooltipStacks.size()) {
            pendingTooltipStacks.clear();
            pendingTooltipCursor = 0;
        }
        if (addedAny) {
            sortAndClampScroll();
        }
    }

    private void sortAndClampScroll() {
        IStoredItemStackComparator comparator = SortingTypes.VALUES[sortType].create(sortReversed);
        Collections.sort(sortedStacks, comparator);
        int maxRows = Math.max(
            0,
            (sortedStacks.size() + ContainerStorageTerminal.COLUMNS - 1) / ContainerStorageTerminal.COLUMNS
                - getStorageRows());
        if (scrollRow > maxRows) scrollRow = maxRows;
    }

    private void onSearchChanged() {
        scrollRow = 0;
        lastTerminalSearch = searchField.getText();
        updateSearch();
    }

    private SearchDocument getSearchDocument(StoredItemStack stored) {
        if (stored == null || stored.getStack() == null) return null;
        SearchDocument document = searchCache.get(stored);
        if (document == null) {
            String displayName;
            try {
                displayName = stored.getDisplayName();
            } catch (RuntimeException exception) {
                displayName = "";
            }
            document = new SearchDocument(displayName);
            searchCache.put(stored, document);
        }
        return document;
    }

    private void ensureModTexts(SearchDocument document, ItemStack stack) {
        if (document.modTexts != null) return;
        List<String> modTexts = new ArrayList<>(3);
        try {
            UniqueIdentifier identifier = GameRegistry.findUniqueIdentifierFor(stack.getItem());
            if (identifier != null) {
                modTexts.add(identifier.modId);
                ModContainer mod = Loader.instance()
                    .getIndexedModList()
                    .get(identifier.modId);
                if (mod != null) modTexts.add(mod.getName());
            }
            modTexts.add(
                stack.getItem()
                    .getUnlocalizedName());
        } catch (RuntimeException ignored) {}
        document.modTexts = modTexts;
    }

    @SuppressWarnings("unchecked")
    private void loadTooltip(SearchDocument document, ItemStack stack) {
        if (document.tooltip != null) return;
        try {
            document.tooltip = new ArrayList<String>(
                stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips));
        } catch (RuntimeException exception) {
            document.tooltip = Collections.emptyList();
        }
    }

    public static SearchQuery parseSearchQuery(String rawText) {
        return SearchMatcher.parseSearchQuery(rawText);
    }

    public static Pattern compilePattern(String text) {
        return SearchMatcher.compilePattern(text);
    }

    public static String formatNumber(long number) {
        return SearchMatcher.formatNumber(number);
    }

    private int getVirtualSlotAt(int mouseX, int mouseY) {
        int relX = mouseX - guiLeft - getStorageGridX();
        int relY = mouseY - guiTop - 18;
        if (relX < 0 || relY < 0) return -1;
        int col = relX / 18;
        int row = relY / 18;
        if (col < 0 || col >= ContainerStorageTerminal.COLUMNS || row < 0 || row >= getStorageRows()) return -1;
        return row * ContainerStorageTerminal.COLUMNS + col;
    }

    private int getHoveredStack(int mouseX, int mouseY) {
        int slot = getVirtualSlotAt(mouseX, mouseY);
        if (slot < 0) return -1;
        int index = scrollRow * ContainerStorageTerminal.COLUMNS + slot;
        return index >= 0 && index < sortedStacks.size() ? index : -1;
    }

    private boolean isMouseInItemGrid(int mouseX, int mouseY) {
        int relX = mouseX - guiLeft - getStorageGridX();
        int relY = mouseY - guiTop - 18;
        return relX >= 0 && relY >= 0 && relX < ContainerStorageTerminal.COLUMNS * 18 && relY < getStorageRows() * 18;
    }

    private boolean isMouseInScrollArea(int mouseX, int mouseY) {
        if (isMouseInItemGrid(mouseX, mouseY)) return true;
        int relX = mouseX - guiLeft - getStorageGridX() - 166;
        int relY = mouseY - guiTop - 18;
        return relX >= 0 && relY >= 0 && relX < 12 && relY < getStorageRows() * 18;
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

    private boolean hasSearchOption(int option) {
        return (searchMode & option) != 0;
    }

    private int nextSearchPreset(int current) {
        for (int i = 0; i < SEARCH_PRESETS.length; i++) {
            if (SEARCH_PRESETS[i] == current) return SEARCH_PRESETS[(i + 1) % SEARCH_PRESETS.length];
        }
        return SEARCH_PRESETS[0];
    }

    private void saveSearchSettings() {
        String retained = hasSearchOption(Config.SEARCH_KEEP_TEXT) && searchField != null ? searchField.getText() : "";
        Config.saveTerminalSearchSettings(searchMode, retained, searchMigrated);
    }

    private void updateForChangedTerminalData() {
        if (searchField == null) return;
        int revision = container.getClientDataRevision();
        if (revision == lastClientDataRevision) return;
        lastClientDataRevision = revision;
        int kindsRevision = container.getClientKindsRevision();
        if (kindsRevision != lastClientKindsRevision) {
            lastClientKindsRevision = kindsRevision;
            if (searchCache.size() > container.clientStacks.size() * 2 + 64) {
                searchCache.keySet()
                    .retainAll(new HashSet<>(container.clientStacks));
            }
            updateSearch();
        } else if (sortType == SortingTypes.AMOUNT.ordinal()) {
            sortAndClampScroll();
        }
    }

    private void invalidateSearchCacheWhenNeeded() {
        String context = getSearchCacheContext();
        if (context.equals(searchCacheContext)) return;
        searchCacheContext = context;
        StoredItemStack.invalidateLanguageCache();
        searchCache.clear();
        updateSearch();
    }

    private String getSearchCacheContext() {
        try {
            return mc.getLanguageManager()
                .getCurrentLanguage()
                .getLanguageCode() + ":"
                + mc.gameSettings.advancedItemTooltips
                + ":"
                + mc.getResourcePackRepository()
                    .getRepositoryEntries()
                    .hashCode();
        } catch (RuntimeException ignored) {
            return Boolean.toString(mc.gameSettings.advancedItemTooltips);
        }
    }

    private void beginNeiSyncIfEnabled() {
        if (!hasSearchOption(Config.SEARCH_SYNC_NEI)) return;
        String neiSearch = getNeiSearch();
        if (neiSearch == null) return;
        if (originalNeiSearch == null) originalNeiSearch = neiSearch;
        String terminalSearch = searchField.getText();
        if (terminalSearch.isEmpty() && !neiSearch.isEmpty()) {
            searchField.setText(neiSearch);
            onSearchChanged();
            terminalSearch = neiSearch;
        } else if (!terminalSearch.equals(neiSearch)) {
            setNeiSearch(terminalSearch);
        }
        lastTerminalSearch = terminalSearch;
        lastNeiSearch = terminalSearch;
    }

    private void synchronizeNeiSearch() {
        if (!hasSearchOption(Config.SEARCH_SYNC_NEI)) return;
        if (originalNeiSearch == null) {
            beginNeiSyncIfEnabled();
            return;
        }
        String neiSearch = getNeiSearch();
        if (neiSearch == null) return;
        String terminalSearch = searchField.getText();
        if (!terminalSearch.equals(lastTerminalSearch)) {
            setNeiSearch(terminalSearch);
            lastTerminalSearch = terminalSearch;
            lastNeiSearch = terminalSearch;
        } else if (!neiSearch.equals(lastNeiSearch)) {
            searchField.setText(neiSearch);
            onSearchChanged();
            lastTerminalSearch = neiSearch;
            lastNeiSearch = neiSearch;
        }
    }

    private String getNeiSearch() {
        try {
            return LayoutManager.searchField == null ? null : LayoutManager.searchField.text();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private void setNeiSearch(String text) {
        try {
            if (LayoutManager.searchField != null) LayoutManager.searchField.setText(text == null ? "" : text);
        } catch (RuntimeException | LinkageError ignored) {}
    }

    private void restoreNeiSearch() {
        if (originalNeiSearch == null) return;
        setNeiSearch(originalNeiSearch);
        originalNeiSearch = null;
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

    private class SearchModeButton extends GuiButton {

        private SearchModeButton(int id, int x, int y) {
            super(id, x, y, 16, 16, "");
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft minecraft, int mouseX, int mouseY) {
            if (!visible) return;
            minecraft.getTextureManager()
                .bindTexture(getGuiTexture());
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            drawTexturedModalRect(xPosition, yPosition, 194, 62, width, height);
            if (searchMode != 0) {
                func_152125_a(xPosition + 2, yPosition + 2, 196, 64, 1, 1, 12, 10, 256, 256);
                int iconX = (searchMode & Config.SEARCH_SYNC_NEI) != 0 ? 240
                    : (searchMode & Config.SEARCH_KEEP_TEXT) != 0 ? 226 : 211;
                int iconY = (searchMode & Config.SEARCH_SYNC_NEI) != 0 ? 60 : 65;
                GL11.glColorMask(true, true, true, false);
                drawTexturedModalRect(xPosition + 2, yPosition + 3, iconX, iconY, 12, 9);
                GL11.glColorMask(true, true, true, true);
            }
        }
    }

    private static final class SearchDocument {

        private final String displayName;
        private List<String> tooltip;
        private List<String> modTexts;

        private SearchDocument(String displayName) {
            this.displayName = displayName;
        }
    }
}
