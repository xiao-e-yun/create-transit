package me.xiaoeyun.createroutes.network;

import java.util.UUID;

import me.xiaoeyun.createroutes.CreateRoutes;
import me.xiaoeyun.createroutes.content.route.Route;
import me.xiaoeyun.createroutes.content.route.RouteEditSession;
import me.xiaoeyun.createroutes.content.route.RouteStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Asks to edit a route; opening happens on the server since what opens is a menu, and the route's stops ride in on that menu's stack. */
public record RouteEditPacket(UUID route) implements CustomPacketPayload {

    public static final Type<RouteEditPacket> TYPE = new Type<>(CreateRoutes.asResource("route_edit"));

    public static final StreamCodec<FriendlyByteBuf, RouteEditPacket> STREAM_CODEC =
        StreamCodec.composite(UUIDUtil.STREAM_CODEC, RouteEditPacket::route, RouteEditPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RouteEditPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender))
            return;

        Route found = RouteStore.get(sender.server)
            .get(packet.route);
        if (found == null) {
            sender.displayClientMessage(Component.translatable("create_routes.route.save.missing")
                .withStyle(ChatFormatting.RED), false);
            return;
        }
        RouteEditSession.open(sender, found);
    }

}
