package com.hepdd.toms_storage.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.toms_storage.StoredItemStack;
import com.hepdd.toms_storage.TomsStorageMod;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketTerminalData implements IMessage {

    public static final byte FLAG_RESET = 0x01;
    public static final byte FLAG_COMMIT = 0x02;
    public static final byte FLAG_DELTA = 0x04;

    public static final int MAX_CHUNK_BYTES = 262_144;
    private static final int MAX_SINGLE_NBT_COMPRESSED = 1_800_000;
    private static final long MAX_SINGLE_NBT_UNCOMPRESSED = 2_097_152L;
    private static final byte[] EMPTY_BYTES = new byte[0];

    private int windowId;
    private byte flags;
    private List<StoredItemStack> entries = new ArrayList<>();
    private int encodedEntryCount;
    private byte[] preEncodedBody;

    public PacketTerminalData() {}

    private PacketTerminalData(int windowId, byte flags, List<StoredItemStack> entries, int encodedEntryCount,
        byte[] preEncodedBody) {
        this.windowId = windowId & 0xFF;
        this.flags = flags;
        this.entries = entries;
        this.encodedEntryCount = encodedEntryCount;
        this.preEncodedBody = preEncodedBody;
    }

    public static List<PacketTerminalData> createFullSnapshots(int windowId, List<StoredItemStack> stacks) {
        return createFullSnapshots(windowId, stacks, MAX_CHUNK_BYTES);
    }

    public static List<PacketTerminalData> createFullSnapshots(int windowId, List<StoredItemStack> stacks,
        int maxChunkBytes) {
        return createChunks(windowId, stacks, false, maxChunkBytes);
    }

    public static List<PacketTerminalData> createDeltas(int windowId, List<StoredItemStack> changes) {
        return createDeltas(windowId, changes, MAX_CHUNK_BYTES);
    }

    public static List<PacketTerminalData> createDeltas(int windowId, List<StoredItemStack> changes,
        int maxChunkBytes) {
        return createChunks(windowId, changes, true, maxChunkBytes);
    }

    private static List<PacketTerminalData> createChunks(int windowId, List<StoredItemStack> source, boolean delta,
        int maxChunkBytes) {
        int limit = Math.max(16, maxChunkBytes);
        List<RawChunk> rawChunks = new ArrayList<>();
        ByteArrayOutputStream entryScratch = new ByteArrayOutputStream(256);
        ByteArrayOutputStream chunkBody = new ByteArrayOutputStream(Math.min(limit, 4096));
        List<StoredItemStack> chunkEntries = new ArrayList<>();

        if (source != null) {
            for (int i = 0, size = source.size(); i < size; i++) {
                StoredItemStack s = source.get(i);
                if (s == null || s.getStack() == null) continue;
                if (!delta && s.getQuantity() <= 0) continue;

                entryScratch.reset();
                encodeEntry(s, entryScratch);

                if (!chunkEntries.isEmpty() && chunkBody.size() + entryScratch.size() > limit) {
                    rawChunks.add(new RawChunk(chunkEntries, chunkBody.toByteArray()));
                    chunkEntries = new ArrayList<>();
                    chunkBody.reset();
                }

                try {
                    entryScratch.writeTo(chunkBody);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to write terminal entry chunk", e);
                }
                chunkEntries.add(s);
            }
        }

        if (!chunkEntries.isEmpty()) {
            rawChunks.add(new RawChunk(chunkEntries, chunkBody.toByteArray()));
        }

        if (rawChunks.isEmpty()) {
            if (delta) {
                return Collections.emptyList();
            }
            return Collections.singletonList(
                new PacketTerminalData(
                    windowId,
                    (byte) (FLAG_RESET | FLAG_COMMIT),
                    Collections.emptyList(),
                    0,
                    EMPTY_BYTES));
        }

        int total = rawChunks.size();
        List<PacketTerminalData> result = new ArrayList<>(total);
        for (int idx = 0; idx < total; idx++) {
            RawChunk chunk = rawChunks.get(idx);
            byte flags = delta ? FLAG_DELTA : 0;
            if (!delta && idx == 0) {
                flags |= FLAG_RESET;
            }
            if (idx == total - 1) {
                flags |= FLAG_COMMIT;
            }
            result.add(new PacketTerminalData(windowId, flags, chunk.entries, chunk.entries.size(), chunk.body));
        }
        return result;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(windowId & 0xFF);
        buf.writeByte(flags);
        if (preEncodedBody != null) {
            writeVarInt32(buf, encodedEntryCount);
            buf.writeBytes(preEncodedBody);
            return;
        }

        int validCount = 0;
        for (int i = 0, size = entries.size(); i < size; i++) {
            StoredItemStack s = entries.get(i);
            if (s != null && s.getStack() != null) validCount++;
        }
        writeVarInt32(buf, validCount);
        if (validCount == 0) return;

        ByteArrayOutputStream scratch = new ByteArrayOutputStream(256);
        for (int i = 0, size = entries.size(); i < size; i++) {
            StoredItemStack s = entries.get(i);
            if (s == null || s.getStack() == null) continue;
            scratch.reset();
            encodeEntry(s, scratch);
            buf.writeBytes(scratch.toByteArray());
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.windowId = buf.readUnsignedByte();
        this.flags = buf.readByte();
        int count = readVarInt32(buf);
        if (count < 0) {
            throw new IllegalArgumentException("Negative entry count in PacketTerminalData: " + count);
        }
        this.encodedEntryCount = count;
        this.preEncodedBody = null;
        this.entries = count == 0 ? new ArrayList<>() : new ArrayList<>(Math.min(count, 4096));
        for (int i = 0; i < count; i++) {
            StoredItemStack decoded = decodeEntry(buf);
            if (decoded != null) {
                this.entries.add(decoded);
            }
        }
    }

    private static void encodeEntry(StoredItemStack stored, ByteArrayOutputStream out) {
        ItemStack stack = stored.getStack();
        int itemId = Item.getIdFromItem(stack.getItem());
        int damage = stack.getItemDamage();
        long quantity = Math.max(0L, stored.getQuantity());
        try {
            writeVarInt32(out, itemId);
            writeVarInt32(out, damage);
            writeVarLong64(out, quantity);
            if (!stack.hasTagCompound()) {
                writeVarInt32(out, 0);
            } else {
                byte[] compressed = CompressedStreamTools.compress(stack.getTagCompound());
                if (compressed.length > MAX_SINGLE_NBT_COMPRESSED) {
                    TomsStorageMod.LOG.warn(
                        "Skipping oversized item NBT ({} compressed bytes) in PacketTerminalData for item id {}",
                        compressed.length,
                        itemId);
                    writeVarInt32(out, 0);
                } else {
                    writeVarInt32(out, compressed.length);
                    out.write(compressed);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize StoredItemStack in PacketTerminalData", e);
        }
    }

    private static StoredItemStack decodeEntry(ByteBuf buf) {
        int itemId = readVarInt32(buf);
        int damage = readVarInt32(buf);
        long quantity = readVarLong64(buf);
        int nbtLen = readVarInt32(buf);
        if (nbtLen < 0 || nbtLen > MAX_SINGLE_NBT_COMPRESSED) {
            throw new IllegalArgumentException("Invalid compressed NBT length in PacketTerminalData: " + nbtLen);
        }
        NBTTagCompound tag = null;
        if (nbtLen > 0) {
            byte[] compressed = new byte[nbtLen];
            buf.readBytes(compressed);
            try {
                tag = CompressedStreamTools.func_152457_a(compressed, new NBTSizeTracker(MAX_SINGLE_NBT_UNCOMPRESSED));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to decompress item NBT in PacketTerminalData", e);
            }
        }
        Item item = itemId < 0 ? null : Item.getItemById(itemId);
        if (item == null) {
            return null;
        }
        ItemStack stack = new ItemStack(item, 1, damage);
        if (tag != null) {
            stack.setTagCompound(tag);
        }
        return StoredItemStack.wrapOwned(stack, quantity);
    }

    private static void writeVarInt32(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    private static void writeVarInt32(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarInt32(ByteBuf buf) {
        int result = 0;
        int shift = 0;
        while (true) {
            if (shift >= 35) {
                throw new IllegalArgumentException("VarInt32 too big");
            }
            byte b = buf.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
    }

    private static void writeVarLong64(OutputStream out, long value) throws IOException {
        while ((value & ~0x7FL) != 0L) {
            out.write(((int) value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write((int) value);
    }

    private static long readVarLong64(ByteBuf buf) {
        long result = 0L;
        int shift = 0;
        while (true) {
            if (shift >= 70) {
                throw new IllegalArgumentException("VarLong64 too big");
            }
            byte b = buf.readByte();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
    }

    public int getWindowId() {
        return windowId;
    }

    public byte getFlags() {
        return flags;
    }

    public boolean isReset() {
        return (flags & FLAG_RESET) != 0;
    }

    public boolean isCommit() {
        return (flags & FLAG_COMMIT) != 0;
    }

    public boolean isDelta() {
        return (flags & FLAG_DELTA) != 0;
    }

    public List<StoredItemStack> getEntries() {
        return entries;
    }

    public List<StoredItemStack> getStacks() {
        return entries;
    }

    private static final class RawChunk {

        private final List<StoredItemStack> entries;
        private final byte[] body;

        private RawChunk(List<StoredItemStack> entries, byte[] body) {
            this.entries = entries;
            this.body = body;
        }
    }

    public static class Handler implements IMessageHandler<PacketTerminalData, IMessage> {

        @Override
        public IMessage onMessage(PacketTerminalData message, MessageContext ctx) {
            TomsStorageMod.proxy.handleTerminalData(message);
            return null;
        }
    }
}
