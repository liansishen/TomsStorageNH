package com.hepdd.toms_storage.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.TomsStorageMod;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketTerminalData implements IMessage {

    private List<StoredItemStack> stacks = new ArrayList<>();
    private int sorting;
    private String search = "";

    public PacketTerminalData() {}

    public PacketTerminalData(List<StoredItemStack> stacks, int sorting, String search) {
        this.stacks = stacks;
        this.sorting = sorting;
        this.search = search == null ? "" : search;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        sorting = tag.getInteger("sorting");
        search = tag.getString("search");
        NBTTagList list = tag.getTagList("stacks", 10);
        stacks = new ArrayList<>();
        for (int i = 0; i < list.tagCount(); i++) {
            StoredItemStack stack = StoredItemStack.readFromNBT(list.getCompoundTagAt(i));
            if (stack != null) stacks.add(stack);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("sorting", sorting);
        tag.setString("search", search);
        NBTTagList list = new NBTTagList();
        for (StoredItemStack stack : stacks) {
            NBTTagCompound stackTag = new NBTTagCompound();
            stack.writeToNBT(stackTag);
            list.appendTag(stackTag);
        }
        tag.setTag("stacks", list);
        ByteBufUtils.writeTag(buf, tag);
    }

    public List<StoredItemStack> getStacks() {
        return stacks;
    }

    public int getSorting() {
        return sorting;
    }

    public String getSearch() {
        return search;
    }

    public static class Handler implements IMessageHandler<PacketTerminalData, IMessage> {

        @Override
        public IMessage onMessage(PacketTerminalData message, MessageContext ctx) {
            TomsStorageMod.proxy.handleTerminalData(message);
            return null;
        }
    }
}
