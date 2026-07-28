package me.xiaoeyun.createnestnetwork.network;

import me.xiaoeyun.createnestnetwork.CreateNestNetwork;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class CnnPackets {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(CreateNestNetwork.asResource("main"),
        () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(CustomsLinkLabelPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(CustomsLinkLabelPacket::encode)
            .decoder(CustomsLinkLabelPacket::new)
            .consumerMainThread(CustomsLinkLabelPacket::handle)
            .add();
    }

}
