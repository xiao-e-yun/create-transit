package me.xiaoeyun.createroutes.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class CrPackets {

    // No integer ids any more: a payload is identified by its own Type, so
    // order here is nothing but reading order.
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(RouteListPacket.TYPE, RouteListPacket.STREAM_CODEC, RouteListPacket::handle);
        registrar.playToServer(RouteEditPacket.TYPE, RouteEditPacket.STREAM_CODEC, RouteEditPacket::handle);
        registrar.playToServer(RouteManagePacket.TYPE, RouteManagePacket.STREAM_CODEC, RouteManagePacket::handle);
        registrar.playToServer(RouteSavePacket.TYPE, RouteSavePacket.STREAM_CODEC, RouteSavePacket::handle);
        registrar.playToServer(ScheduleReopenPacket.TYPE, ScheduleReopenPacket.STREAM_CODEC,
            ScheduleReopenPacket::handle);
    }

}
