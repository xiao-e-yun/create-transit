package me.xiaoeyun.createroutes.network;

import java.util.UUID;

import me.xiaoeyun.createroutes.CreateRoutes;
import me.xiaoeyun.createroutes.content.route.Route;
import me.xiaoeyun.createroutes.content.route.RouteStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Creates, renames, or deletes a route. */
public record RouteManagePacket(Action action, UUID route, String name) implements CustomPacketPayload {

    public enum Action {
        NEW, RENAME, DELETE
    }

    /** Carried by the one action that names no route, so the shape stays fixed. */
    private static final UUID NONE = new UUID(0, 0);

    public static final Type<RouteManagePacket> TYPE = new Type<>(CreateRoutes.asResource("route_manage"));

    // Written out rather than composed: readEnum and the name's length cap have
    // no ByteBufCodecs spelling as compact as the calls themselves.
    public static final StreamCodec<FriendlyByteBuf, RouteManagePacket> STREAM_CODEC =
        StreamCodec.of(RouteManagePacket::write, RouteManagePacket::read);

    public static RouteManagePacket create(String name) {
        return new RouteManagePacket(Action.NEW, NONE, name);
    }

    public static RouteManagePacket rename(UUID route, String name) {
        return new RouteManagePacket(Action.RENAME, route, name);
    }

    public static RouteManagePacket delete(UUID route) {
        return new RouteManagePacket(Action.DELETE, route, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, RouteManagePacket packet) {
        buffer.writeEnum(packet.action);
        buffer.writeUUID(packet.route);
        buffer.writeUtf(packet.name, Route.MAX_NAME_LENGTH);
    }

    private static RouteManagePacket read(FriendlyByteBuf buffer) {
        return new RouteManagePacket(buffer.readEnum(Action.class), buffer.readUUID(),
            buffer.readUtf(Route.MAX_NAME_LENGTH));
    }

    public static void handle(RouteManagePacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender))
            return;
        RouteStore store = RouteStore.get(sender.server);

        switch (packet.action) {
            case NEW -> {
                String wanted = packet.name.trim();
                if (wanted.isEmpty())
                    return;
                if (store.byName(wanted) != null) {
                    taken(sender, wanted);
                    return;
                }
                store.put(new Route(wanted));
                store.syncNames();
            }
            case RENAME -> {
                if (!store.rename(packet.route, packet.name.trim()))
                    taken(sender, packet.name.trim());
            }
            case DELETE -> {
                if (store.remove(packet.route))
                    store.syncNames();
            }
        }
    }

    private static void taken(ServerPlayer player, String name) {
        player.displayClientMessage(Component.translatable("create_routes.route.rename.taken", name)
            .withStyle(ChatFormatting.RED), false);
    }

}
