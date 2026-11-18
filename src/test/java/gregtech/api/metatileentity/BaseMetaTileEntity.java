package gregtech.api.metatileentity;

import com.hepdd.toms_storage.inventory.StorageTestInventory;

public class BaseMetaTileEntity extends StorageTestInventory {

    private final Object metaTileEntity;

    public BaseMetaTileEntity(Object metaTileEntity) {
        this.metaTileEntity = metaTileEntity;
    }

    public Object getMetaTileEntity() {
        return metaTileEntity;
    }
}
