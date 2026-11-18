package mrtjp.projectred.exploration;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.inventory.StorageTestInventory;

public class TileBarrel extends StorageTestInventory {

    public int stored;

    public TileBarrel() {
        super(2);
    }

    public int storage() {
        return stored;
    }

    public void storage_$eq(int count) {
        stored = count;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return slot == 0 && side == ForgeDirection.UP.ordinal();
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return slot == 1 && side == ForgeDirection.DOWN.ordinal();
    }
}
