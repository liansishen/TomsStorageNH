package team.chisel.block.tileentity;

import com.hepdd.toms_storage.inventory.StorageTestInventory;

public class TileEntityPresent extends StorageTestInventory {

    public TileEntityPresent parent;

    public TileEntityPresent() {
        super(54);
        parent = this;
    }

    public TileEntityPresent getParent() {
        return parent;
    }
}
