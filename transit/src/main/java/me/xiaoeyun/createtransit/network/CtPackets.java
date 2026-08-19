package me.xiaoeyun.createtransit.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class CtPackets {

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(TransitLinkLabelPacket.TYPE, TransitLinkLabelPacket.STREAM_CODEC,
            TransitLinkLabelPacket::handle);
    }

}
