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
        CHANNEL.messageBuilder(RouteListPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(RouteListPacket::encode)
            .decoder(RouteListPacket::new)
            .consumerMainThread(RouteListPacket::handle)
            .add();
        // A route's stops never travel on their own: they ride in the stack the
        // editor's menu is opened on, so asking to edit one is the only message
        // it takes.
        CHANNEL.messageBuilder(RouteEditPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RouteEditPacket::encode)
            .decoder(RouteEditPacket::new)
            .consumerMainThread(RouteEditPacket::handle)
            .add();
        CHANNEL.messageBuilder(RouteOpenedPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RouteOpenedPacket::encode)
            .decoder(RouteOpenedPacket::new)
            .consumerMainThread(RouteOpenedPacket::handle)
            .add();
        // Which routes exist, changed from the route list.
        CHANNEL.messageBuilder(RouteManagePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RouteManagePacket::encode)
            .decoder(RouteManagePacket::new)
            .consumerMainThread(RouteManagePacket::handle)
            .add();
        // The other half of that close: what the editor holds that a schedule
        // cannot carry, sent right after Create's own so it lands second.
        CHANNEL.messageBuilder(RouteEnvelopePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(RouteEnvelopePacket::encode)
            .decoder(RouteEnvelopePacket::new)
            .consumerMainThread(RouteEnvelopePacket::handle)
            .add();
        // The last step out of a trip that began at a held schedule. Added at
        // the end rather than beside the other route messages, because an id is
        // a position in this list and moving one renames every message after it.
        CHANNEL.messageBuilder(ScheduleReopenPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
            .encoder(ScheduleReopenPacket::encode)
            .decoder(ScheduleReopenPacket::new)
            .consumerMainThread(ScheduleReopenPacket::handle)
            .add();
    }

}
