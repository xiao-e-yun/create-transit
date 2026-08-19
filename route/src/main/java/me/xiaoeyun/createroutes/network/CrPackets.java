package me.xiaoeyun.createroutes.network;

import me.xiaoeyun.createroutes.CreateRoutes;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class CrPackets {

    private static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(CreateRoutes.asResource("main"),
        () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(RouteListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(RouteListPacket::encode)
            .decoder(RouteListPacket::new)
            .consumerMainThread(RouteListPacket::handle)
            .add();
        CHANNEL.messageBuilder(RouteEditPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RouteEditPacket::encode)
            .decoder(RouteEditPacket::new)
            .consumerMainThread(RouteEditPacket::handle)
            .add();
        CHANNEL.messageBuilder(RouteManagePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RouteManagePacket::encode)
            .decoder(RouteManagePacket::new)
            .consumerMainThread(RouteManagePacket::handle)
            .add();
        CHANNEL.messageBuilder(RouteSavePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RouteSavePacket::encode)
            .decoder(RouteSavePacket::new)
            .consumerMainThread(RouteSavePacket::handle)
            .add();
        // Appended, not reordered: an id is a position in this list, so moving one renames every message after it.
        CHANNEL.messageBuilder(ScheduleReopenPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(ScheduleReopenPacket::encode)
            .decoder(ScheduleReopenPacket::new)
            .consumerMainThread(ScheduleReopenPacket::handle)
            .add();
    }

}
