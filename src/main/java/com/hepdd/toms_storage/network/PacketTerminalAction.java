package com.hepdd.toms_storage.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.gui.ContainerStorageTerminal;
import com.hepdd.toms_storage.gui.SlotAction;
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
    private Boolean neiTransfer;

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

    public static PacketTerminalAction neiTransfer(boolean begin) {
        PacketTerminalAction packet = new PacketTerminalAction();
        packet.neiTransfer = begin;
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        if (tag.hasKey("action")) action = SlotAction.VALUES[tag.getInteger("action") % SlotAction.VALUES.length];
        if (tag.hasKey("stack")) stack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("stack"));
        if (tag.hasKey("search")) search = tag.getString("search");
        if (tag.hasKey("sorting")) sorting = tag.getInteger("sorting");
        if (tag.hasKey("neiTransfer")) neiTransfer = tag.getBoolean("neiTransfer");
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
        if (neiTransfer != null) tag.setBoolean("neiTransfer", neiTransfer);
        ByteBufUtils.writeTag(buf, tag);
    }

    public static class Handler implements IMessageHandler<PacketTerminalAction, IMessage> {

        @Override
        public IMessage onMessage(PacketTerminalAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (!(player.openContainer instanceof ContainerStorageTerminal)) return null;

            ContainerStorageTerminal container = (ContainerStorageTerminal) player.openContainer;
            TileEntityStorageTerminal terminal = container.getTerminal();
            if (message.search != null) terminal.setLastSearch(message.search);
            if (message.sorting >= 0) terminal.setSorting(message.sorting);
            if (message.neiTransfer != null && container instanceof ContainerCraftingTerminal) {
                if (message.neiTransfer) {
                    ((ContainerCraftingTerminal) container).beginNeiTransfer();
                } else {
                    ((ContainerCraftingTerminal) container).endNeiTransfer(player);
                }
            }
            if (message.action == SlotAction.CLEAR_GRID && container instanceof ContainerCraftingTerminal) {
                ((ContainerCraftingTerminal) container).clearGrid();
                return null;
            }
            if (message.action != null) container.handleTerminalAction(player, message.action, message.stack);
            return null;
        }
    }
}
