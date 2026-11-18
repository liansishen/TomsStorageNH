package com.hepdd.toms_storage.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.hepdd.toms_storage.Config;
import com.hepdd.toms_storage.ModNetwork;
import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.inventory.IStorageInventory;
import com.hepdd.toms_storage.network.PacketConnectorRangePreview;
import com.hepdd.toms_storage.tile.TileEntityInventoryConnector;

public class BlockInventoryConnector extends BlockContainer {

    public BlockInventoryConnector() {
        super(Material.wood);
        setBlockName("tomsstorage.inventory_connector");
        setBlockTextureName("tomsstorage:inventory_connector");
        setHardness(3.0F);
        setResistance(5.0F);
        setCreativeTab(ModRegistry.CREATIVE_TAB);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityInventoryConnector();
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileEntityInventoryConnector) {
                ((TileEntityInventoryConnector) tile).markTopologyDirty();
            }
            TileEntityInventoryConnector.notifyBlockChanged(world, x, y, z);
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int metadata) {
        super.breakBlock(world, x, y, z, block, metadata);
        if (!world.isRemote) {
            TileEntityInventoryConnector.notifyBlockChanged(world, x, y, z);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() == Item.getItemFromBlock(ModRegistry.inventoryCable)) {
            return false;
        }
        if (!world.isRemote) {
            if (player.isSneaking() && player.getHeldItem() == null && player instanceof EntityPlayerMP) {
                ModNetwork.channel.sendTo(
                    new PacketConnectorRangePreview(
                        world.provider.dimensionId,
                        x,
                        y,
                        z,
                        Config.inventoryConnectorRange,
                        200),
                    (EntityPlayerMP) player);
                return true;
            }
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileEntityInventoryConnector) {
                IStorageInventory inventory = ((TileEntityInventoryConnector) tile).getStorageInventory();
                long stats = inventory.packSlotStats();
                int total = (int) (stats >>> 32);
                int free = (int) stats;
                player.addChatMessage(
                    new ChatComponentTranslation("message.tomsstorage.inventory_connector.slots", free, total));
                if (((TileEntityInventoryConnector) tile).isCableScanLimitReached()) {
                    player.addChatMessage(
                        new ChatComponentTranslation(
                            "message.tomsstorage.inventory_connector.cable_limit",
                            Config.maxInventoryCableNodes));
                }
                if (((TileEntityInventoryConnector) tile).hasCableNetworkConflict()) {
                    player.addChatMessage(
                        new ChatComponentTranslation("message.tomsstorage.inventory_connector.cable_conflict"));
                }
            }
        }
        return true;
    }
}
