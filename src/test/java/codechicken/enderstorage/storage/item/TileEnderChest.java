package codechicken.enderstorage.storage.item;

import net.minecraft.inventory.IInventory;

import com.hepdd.toms_storage.inventory.StorageTestInventory;

public class TileEnderChest extends StorageTestInventory {

    private final IInventory storage;

    public TileEnderChest(StorageTestInventory shared) {
        storage = shared;
        stacks = shared.stacks;
    }

    public IInventory getStorage() {
        return storage;
    }
}
