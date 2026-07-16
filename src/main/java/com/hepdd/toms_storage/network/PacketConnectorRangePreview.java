package com.hepdd.toms_storage.network;

import com.hepdd.toms_storage.TomsStorageMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketConnectorRangePreview implements IMessage {

    private int dimension;
    private int x;
    private int y;
    private int z;
    private int radius;
    private int durationTicks;

    public PacketConnectorRangePreview() {}

    public PacketConnectorRangePreview(int dimension, int x, int y, int z, int radius, int durationTicks) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.durationTicks = durationTicks;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        dimension = buf.readInt();
        x = buf.readInt();
        y = buf.readInt();
        z = buf.readInt();
        radius = buf.readInt();
        durationTicks = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(dimension);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(radius);
        buf.writeInt(durationTicks);
    }

    public int getDimension() {
        return dimension;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getRadius() {
        return radius;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public static class Handler implements IMessageHandler<PacketConnectorRangePreview, IMessage> {

        @Override
        public IMessage onMessage(PacketConnectorRangePreview message, MessageContext ctx) {
            TomsStorageMod.proxy.handleConnectorRangePreview(message);
            return null;
        }
    }
}
