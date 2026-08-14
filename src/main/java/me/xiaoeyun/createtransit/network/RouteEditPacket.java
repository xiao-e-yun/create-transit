package me.xiaoeyun.createtransit.network;

import java.util.UUID;
import java.util.function.Supplier;

import me.xiaoeyun.createtransit.content.route.Route;
import me.xiaoeyun.createtransit.content.route.RouteEditSession;
import me.xiaoeyun.createtransit.content.route.RouteStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Asks to edit a route; opening happens on the server since what opens is a menu, and the route's stops ride in on that menu's stack. */
public class RouteEditPacket {

    private final UUID route;

    public RouteEditPacket(UUID route) {
        this.route = route;
    }

    public RouteEditPacket(FriendlyByteBuf buffer) {
        route = buffer.readUUID();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(route);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get()
            .getSender();
        if (sender == null)
            return;

        Route found = RouteStore.get(sender.server)
            .get(route);
        if (found == null) {
            sender.displayClientMessage(Component.translatable("create_transit.route.save.missing")
                .withStyle(ChatFormatting.RED), false);
            return;
        }
        RouteEditSession.open(sender, found);
    }

}
