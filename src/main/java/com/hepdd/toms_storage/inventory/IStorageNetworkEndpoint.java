package com.hepdd.toms_storage.inventory;

import net.minecraftforge.common.util.ForgeDirection;

import com.hepdd.toms_storage.tile.TileEntityInventoryConnector;

public interface IStorageNetworkEndpoint {

    boolean canConnectNetworkFrom(ForgeDirection side);

    void attachNetwork(TileEntityInventoryConnector connector, ForgeDirection side);

    void markNetworkConflict();

    default void markNetworkConflict(TileEntityInventoryConnector connector, ForgeDirection side) {
        markNetworkConflict();
    }

    default void detachNetwork(TileEntityInventoryConnector connector) {}
}
