package com.hepdd.toms_storage.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import com.hepdd.toms_storage.Config;
import com.hepdd.toms_storage.GuiHandler;
import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.TomsStorageMod;
import com.hepdd.toms_storage.client.TooltipHelper;
import com.hepdd.toms_storage.tile.TileEntityCraftingTerminal;
import com.hepdd.toms_storage.tile.TileEntityStorageTerminal;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ItemWirelessTerminal extends Item {

    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";

    public ItemWirelessTerminal() {
        setUnlocalizedName("tomsstorage.wireless_terminal");
        setTextureName("tomsstorage:wireless_terminal");
        setCreativeTab(ModRegistry.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List tooltip, boolean advanced) {
        TooltipHelper.addLines(tooltip, "tooltip.tomsstorage.wireless_terminal");
        tooltip.add(
            net.minecraft.client.resources.I18n
                .format("tooltip.tomsstorage.wireless_terminal.range", Config.wirelessReach));
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(TAG_DIMENSION)) {
            tooltip.add(
                net.minecraft.client.resources.I18n.format(
                    "tooltip.tomsstorage.wireless_terminal.bound",
                    tag.getInteger(TAG_X),
                    tag.getInteger(TAG_Y),
                    tag.getInteger(TAG_Z)));
            tooltip.add(
                net.minecraft.client.resources.I18n
                    .format("tooltip.tomsstorage.wireless_terminal.dimension", tag.getInteger(TAG_DIMENSION)));
        } else {
            tooltip.add(net.minecraft.client.resources.I18n.format("tooltip.tomsstorage.wireless_terminal.unbound"));
        }
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!isTerminal(tile)) return false;

        if (!world.isRemote) {
            if (player.isSneaking()) {
                bind(stack, world, x, y, z, tile);
                player.addChatMessage(
                    new ChatComponentTranslation("message.tomsstorage.wireless_terminal.bound", x, y, z));
            } else {
                openTarget(world, player, x, y, z, tile);
            }
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return stack;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(TAG_DIMENSION)) {
            openBound(stack, world, player);
            return stack;
        }

        MovingObjectPosition hit = getMovingObjectPositionFromPlayer(world, player, true);
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            TileEntity tile = world.getTileEntity(hit.blockX, hit.blockY, hit.blockZ);
            if (isTerminal(tile)) openTarget(world, player, hit.blockX, hit.blockY, hit.blockZ, tile);
        } else {
            player.addChatMessage(new ChatComponentTranslation("message.tomsstorage.wireless_terminal.unbound"));
        }
        return stack;
    }

    public static boolean isPlayerHolding(EntityPlayer player) {
        return player != null && isWireless(player.getHeldItem());
    }

    private static boolean isWireless(ItemStack stack) {
        return stack != null && stack.getItem() == ModRegistry.wirelessTerminal;
    }

    private static boolean isTerminal(TileEntity tile) {
        return tile instanceof TileEntityStorageTerminal;
    }

    private void bind(ItemStack stack, World world, int x, int y, int z, TileEntity tile) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setInteger(TAG_DIMENSION, world.provider.dimensionId);
        tag.setInteger(TAG_X, x);
        tag.setInteger(TAG_Y, y);
        tag.setInteger(TAG_Z, z);
    }

    private void openBound(ItemStack stack, World world, EntityPlayer player) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_DIMENSION)) {
            player.addChatMessage(new ChatComponentTranslation("message.tomsstorage.wireless_terminal.unbound"));
            return;
        }
        if (tag.getInteger(TAG_DIMENSION) != world.provider.dimensionId) {
            player
                .addChatMessage(new ChatComponentTranslation("message.tomsstorage.wireless_terminal.wrong_dimension"));
            return;
        }

        int x = tag.getInteger(TAG_X);
        int y = tag.getInteger(TAG_Y);
        int z = tag.getInteger(TAG_Z);
        if (player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > Config.wirelessReach * Config.wirelessReach) {
            player.addChatMessage(new ChatComponentTranslation("message.tomsstorage.wireless_terminal.too_far"));
            return;
        }

        TileEntity tile = world.getTileEntity(x, y, z);
        if (!isTerminal(tile)) {
            player.addChatMessage(new ChatComponentTranslation("message.tomsstorage.wireless_terminal.missing"));
            return;
        }
        player.openGui(TomsStorageMod.instance, getGuiId(tile), world, x, y, z);
    }

    private void openTarget(World world, EntityPlayer player, int x, int y, int z, TileEntity tile) {
        if (player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > Config.wirelessReach * Config.wirelessReach) {
            player.addChatMessage(new ChatComponentTranslation("message.tomsstorage.wireless_terminal.too_far"));
            return;
        }
        player.openGui(TomsStorageMod.instance, getGuiId(tile), world, x, y, z);
    }

    private int getGuiId(TileEntity tile) {
        return tile instanceof TileEntityCraftingTerminal ? GuiHandler.CRAFTING_TERMINAL : GuiHandler.STORAGE_TERMINAL;
    }
}
