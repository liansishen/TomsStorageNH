package jds.bibliocraft.tileentities;

import com.hepdd.toms_storage.inventory.StorageTestInventory;

public class TileEntityFramedChest extends StorageTestInventory {

    public boolean locked;

    public boolean isLocked() {
        return locked;
    }
}
