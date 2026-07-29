package me.xiaoeyun.createtransit.network;

import me.xiaoeyun.createtransit.CreateTransit;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class CtPackets {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(CreateTransit.asResource("main"),
        () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(TransitLinkLabelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(TransitLinkLabelPacket::encode)
            .decoder(TransitLinkLabelPacket::new)
            .consumerMainThread(TransitLinkLabelPacket::handle)
            .add();
    }

}
