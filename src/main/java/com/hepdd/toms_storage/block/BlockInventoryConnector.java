package com.hepdd.toms_storage.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

import com.hepdd.toms_storage.ModRegistry;
import com.hepdd.toms_storage.inventory.IStorageInventory;
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
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileEntityInventoryConnector) {
                IStorageInventory inventory = ((TileEntityInventoryConnector) tile).getStorageInventory();
                int total = inventory.getSlots();
                int free = 0;
                for (int i = 0; i < total; i++) {
                    ItemStack stack = inventory.getStackInSlot(i);
                    if (stack == null || stack.stackSize <= 0) free++;
                }
                player.addChatMessage(
                    new ChatComponentTranslation("message.tomsstorage.inventory_connector.slots", free, total));
            }
        }
        return true;
    }
}
