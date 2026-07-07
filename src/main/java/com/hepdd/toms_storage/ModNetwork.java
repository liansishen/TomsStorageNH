package com.hepdd.toms_storage;

import com.hepdd.toms_storage.network.PacketAutoCraftRequest;
import com.hepdd.toms_storage.network.PacketAutoCraftResult;
import com.hepdd.toms_storage.network.PacketTerminalAction;
import com.hepdd.toms_storage.network.PacketTerminalData;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class ModNetwork {

    public static SimpleNetworkWrapper channel;

    private ModNetwork() {}

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel(TomsStorageMod.NETWORK_CHANNEL);
        channel.registerMessage(PacketTerminalAction.Handler.class, PacketTerminalAction.class, 0, Side.SERVER);
        channel.registerMessage(PacketTerminalData.Handler.class, PacketTerminalData.class, 1, Side.CLIENT);
        channel.registerMessage(PacketAutoCraftRequest.Handler.class, PacketAutoCraftRequest.class, 2, Side.SERVER);
        channel.registerMessage(PacketAutoCraftResult.Handler.class, PacketAutoCraftResult.class, 3, Side.CLIENT);
    }
}
