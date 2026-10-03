package com.hepdd.toms_storage.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.gtnhlib.util.ServerThreadUtil;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.gui.ContainerStorageTerminal;
import com.hepdd.toms_storage.gui.SlotAction;
import com.hepdd.toms_storage.nei.RecipeNbtSerializer;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketTerminalAction implements IMessage {

    private SlotAction action;
    private ItemStack stack;
    private String search;
    private int sorting = -1;
    private NBTTagCompound recipe;
    private int windowId;
    private Boolean autoRefill;
    private int craftingButton;
    private int craftingMode = -1;

    public PacketTerminalAction() {}

    public PacketTerminalAction(SlotAction action, ItemStack stack) {
        this.action = action;
        this.stack = stack == null ? null : stack.copy();
        if (this.stack != null) this.stack.stackSize = 1;
    }

    public static PacketTerminalAction search(String search) {
        PacketTerminalAction packet = new PacketTerminalAction();
        packet.search = search == null ? "" : search;
        return packet;
    }

    public static PacketTerminalAction sorting(int sorting) {
        PacketTerminalAction packet = new PacketTerminalAction();
        packet.sorting = sorting;
        return packet;
    }

    public static PacketTerminalAction fillRecipe(int windowId, NBTTagCompound recipe) {
        PacketTerminalAction packet = new PacketTerminalAction();
        packet.windowId = windowId;
        packet.recipe = (NBTTagCompound) recipe.copy();
        return packet;
    }

    public static PacketTerminalAction autoRefill(int windowId, boolean enabled) {
        PacketTerminalAction packet = new PacketTerminalAction();
        packet.windowId = windowId;
        packet.autoRefill = enabled;
        return packet;
    }

    public static PacketTerminalAction craftResult(int windowId, int button, int mode) {
        PacketTerminalAction packet = new PacketTerminalAction();
        packet.windowId = windowId;
        packet.craftingButton = button;
        packet.craftingMode = mode;
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        if (tag.hasKey("action")) action = SlotAction.VALUES[tag.getInteger("action") % SlotAction.VALUES.length];
        if (tag.hasKey("stack")) stack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("stack"));
        if (tag.hasKey("search")) search = tag.getString("search");
        if (tag.hasKey("sorting")) sorting = tag.getInteger("sorting");
        if (tag.hasKey("recipe")) recipe = tag.getCompoundTag("recipe");
        if (tag.hasKey("autoRefill")) autoRefill = tag.getBoolean("autoRefill");
        if (tag.hasKey("craftingMode")) {
            craftingMode = tag.getInteger("craftingMode");
            craftingButton = tag.getInteger("craftingButton");
        }
        windowId = tag.getInteger("windowId");
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        if (action != null) tag.setInteger("action", action.ordinal());
        if (stack != null) {
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            tag.setTag("stack", stackTag);
        }
        if (search != null) tag.setString("search", search);
        if (sorting >= 0) tag.setInteger("sorting", sorting);
        if (recipe != null) tag.setTag("recipe", recipe);
        if (autoRefill != null) tag.setBoolean("autoRefill", autoRefill);
        if (craftingMode >= 0) {
            tag.setInteger("craftingMode", craftingMode);
            tag.setInteger("craftingButton", craftingButton);
        }
        if (recipe != null || autoRefill != null || craftingMode >= 0) tag.setInteger("windowId", windowId);
        ByteBufUtils.writeTag(buf, tag);
    }

    public static class Handler implements IMessageHandler<PacketTerminalAction, IMessage> {

        @Override
        public IMessage onMessage(PacketTerminalAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            ServerThreadUtil.addScheduledTask(() -> handleAction(message, player));
            return null;
        }

        private static void handleAction(PacketTerminalAction message, EntityPlayerMP player) {
            if (message.craftingMode >= 0) {
                handleCraftingClick(message, player);
                return;
            }
            if (!(player.openContainer instanceof ContainerStorageTerminal)) return;

            ContainerStorageTerminal container = (ContainerStorageTerminal) player.openContainer;
            TileEntityStorageTerminal terminal = container.getTerminal();
            if (message.search != null) terminal.setLastSearch(message.search);
            if (message.sorting >= 0) terminal.setSorting(message.sorting);
            if (message.autoRefill != null && container instanceof ContainerCraftingTerminal
                && container.windowId == message.windowId
                && container.canInteractWith(player)) {
                ((ContainerCraftingTerminal) container).setAutoRefillEnabled(message.autoRefill);
                container.detectAndSendChanges();
            }
            if (message.recipe != null && container instanceof ContainerCraftingTerminal
                && container.windowId == message.windowId
                && container.canInteractWith(player)) {
                ((ContainerCraftingTerminal) container).fillRecipe(
                    player,
                    RecipeNbtSerializer.readIngredients(message.recipe),
                    message.recipe.getInteger("count"));
                player.inventory.markDirty();
                container.detectAndSendChanges();
                player.sendContainerToPlayer(container);
            }
            if (message.action == SlotAction.CLEAR_GRID && container instanceof ContainerCraftingTerminal) {
                ((ContainerCraftingTerminal) container).clearGrid();
                return;
            }
            if (message.action != null) container.handleTerminalAction(player, message.action, message.stack);
        }

        private static void handleCraftingClick(PacketTerminalAction message, EntityPlayerMP player) {
            if (!(player.openContainer instanceof ContainerCraftingTerminal)) return;
            ContainerCraftingTerminal container = (ContainerCraftingTerminal) player.openContainer;
            if (container.windowId != message.windowId) return;
            int mode = message.craftingMode;
            int button = message.craftingButton;
            boolean valid = ((mode == 0 || mode == 1 || mode == 4) && (button == 0 || button == 1))
                || (mode == 2 && button >= 0 && button < 9)
                || (mode == 3 && button == 2 && player.capabilities.isCreativeMode);
            if (valid && container.canInteractWith(player) && container.isPlayerNotUsingContainer(player)) {
                container.slotClick(ContainerCraftingTerminal.GRID_SLOT_START - 1, button, mode, player);
            }
            player.inventory.markDirty();
            container.detectAndSendChanges();
            player.sendContainerToPlayer(container);
            // Release client prediction only after inventory and cursor updates have been sent.
            player.sendProgressBarUpdate(container, 1, 0);
        }
    }
}
