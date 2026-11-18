package mcp.mobius.betterbarrels.common.blocks;

import net.minecraft.item.ItemStack;

import com.hepdd.toms_storage.inventory.StorageTestInventory;

public class TileEntityBarrel extends StorageTestInventory {

    public Object storage = this;

    public Object getStorage() {
        return storage;
    }

    public TileEntityBarrel() {
        super(2);
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == 0 && input;
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot == 1 && output;
    }
}
