package com.hepdd.toms_storage.network;

import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.toms_storage.TomsStorageMod;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketAutoCraftResult implements IMessage {

    private int requestId;
    private boolean success;
    private String reason = "";

    public PacketAutoCraftResult() {}

    public PacketAutoCraftResult(int requestId, boolean success, String reason) {
        this.requestId = requestId;
        this.success = success;
        this.reason = reason == null ? "" : reason;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        requestId = tag.getInteger("requestId");
        success = tag.getBoolean("success");
        reason = tag.getString("reason");
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("requestId", requestId);
        tag.setBoolean("success", success);
        tag.setString("reason", reason);
        ByteBufUtils.writeTag(buf, tag);
    }

    public int getRequestId() {
        return requestId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReason() {
        return reason;
    }

    public static class Handler implements IMessageHandler<PacketAutoCraftResult, IMessage> {

        @Override
        public IMessage onMessage(PacketAutoCraftResult message, MessageContext ctx) {
            TomsStorageMod.proxy.handleAutoCraftResult(message);
            return null;
        }
    }
}
