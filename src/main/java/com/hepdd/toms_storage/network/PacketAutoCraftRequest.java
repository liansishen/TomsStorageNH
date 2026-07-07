package com.hepdd.toms_storage.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.hepdd.toms_storage.crafting.SingleAutoCraftExecutor;
import com.hepdd.toms_storage.gui.ContainerCraftingTerminal;
import com.hepdd.toms_storage.nei.RecipeNbtSerializer;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketAutoCraftRequest implements IMessage {

    private int requestId;
    private NBTTagCompound recipeTag = new NBTTagCompound();
    private boolean allowSubCrafting;
    private boolean usePlayerInventory = true;

    public PacketAutoCraftRequest() {}

    public PacketAutoCraftRequest(int requestId, NBTTagCompound recipeTag, boolean allowSubCrafting,
        boolean usePlayerInventory) {
        this.requestId = requestId;
        this.recipeTag = recipeTag == null ? new NBTTagCompound() : (NBTTagCompound) recipeTag.copy();
        this.allowSubCrafting = allowSubCrafting;
        this.usePlayerInventory = usePlayerInventory;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        NBTTagCompound tag = ByteBufUtils.readTag(buf);
        requestId = tag.getInteger("requestId");
        recipeTag = tag.getCompoundTag("recipe");
        allowSubCrafting = tag.getBoolean("allowSubCrafting");
        usePlayerInventory = !tag.hasKey("usePlayerInventory") || tag.getBoolean("usePlayerInventory");
    }

    @Override
    public void toBytes(ByteBuf buf) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("requestId", requestId);
        tag.setTag("recipe", recipeTag);
        tag.setBoolean("allowSubCrafting", allowSubCrafting);
        tag.setBoolean("usePlayerInventory", usePlayerInventory);
        ByteBufUtils.writeTag(buf, tag);
    }

    public int getRequestId() {
        return requestId;
    }

    public NBTTagCompound getRecipeTag() {
        return recipeTag;
    }

    public ItemStack getOutput() {
        return RecipeNbtSerializer.readOutput(recipeTag);
    }

    public int getCount() {
        return Math.max(1, recipeTag.getInteger("count"));
    }

    public boolean isAllowSubCrafting() {
        return allowSubCrafting;
    }

    public boolean isUsePlayerInventory() {
        return usePlayerInventory;
    }

    public static class Handler implements IMessageHandler<PacketAutoCraftRequest, IMessage> {

        @Override
        public IMessage onMessage(PacketAutoCraftRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            Container container = player.openContainer;
            if (!(container instanceof ContainerCraftingTerminal)) {
                return new PacketAutoCraftResult(message.requestId, false, "not_crafting_terminal");
            }

            ItemStack output = message.getOutput();
            if (output == null || output.stackSize <= 0) {
                return new PacketAutoCraftResult(message.requestId, false, "missing_output");
            }

            synchronized (container) {
                SingleAutoCraftExecutor.CraftResult result = SingleAutoCraftExecutor
                    .execute(message, player, (ContainerCraftingTerminal) container);
                ((ContainerCraftingTerminal) container).detectAndSendChanges();
                player.inventory.markDirty();
                player.sendContainerToPlayer(container);
                return new PacketAutoCraftResult(message.requestId, result.isSuccess(), result.getReason());
            }
        }
    }
}
