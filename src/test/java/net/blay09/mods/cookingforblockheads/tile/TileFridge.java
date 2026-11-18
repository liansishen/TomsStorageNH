package net.blay09.mods.cookingforblockheads.tile;

import net.minecraft.inventory.IInventory;

import com.hepdd.toms_storage.inventory.StorageTestInventory;

public class TileFridge extends StorageTestInventory {

    public final StorageTestInventory internal = new StorageTestInventory(27);
    public TileFridge neighbour;

    public IInventory getInternalInventory() {
        return internal;
    }

    public TileFridge findNeighbourFridge() {
        return neighbour;
    }
}
