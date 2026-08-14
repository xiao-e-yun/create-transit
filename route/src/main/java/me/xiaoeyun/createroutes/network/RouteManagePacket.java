package me.xiaoeyun.createroutes.network;

import java.util.UUID;
import java.util.function.Supplier;

import me.xiaoeyun.createroutes.content.route.Route;
import me.xiaoeyun.createroutes.content.route.RouteStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Creates, renames, or deletes a route; no permission gate, on purpose — the same trust Create gives a station rename. */
public class RouteManagePacket {

    public enum Action {
        NEW, RENAME, DELETE
    }

    /** Carried by the one action that names no route, so the shape stays fixed. */
    private static final UUID NONE = new UUID(0, 0);

    private final Action action;

    /** Which route, for the two actions that act on one. */
    private final UUID route;

    /** What to call it, for the two that name one. */
    private final String name;

    private RouteManagePacket(Action action, UUID route, String name) {
        this.action = action;
        this.route = route;
        this.name = name;
    }

    public static RouteManagePacket create(String name) {
        return new RouteManagePacket(Action.NEW, NONE, name);
    }

    public static RouteManagePacket rename(UUID route, String name) {
        return new RouteManagePacket(Action.RENAME, route, name);
    }

    public static RouteManagePacket delete(UUID route) {
        return new RouteManagePacket(Action.DELETE, route, "");
    }

    public RouteManagePacket(FriendlyByteBuf buffer) {
        action = buffer.readEnum(Action.class);
        route = buffer.readUUID();
        name = buffer.readUtf(Route.MAX_NAME_LENGTH);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(action);
        buffer.writeUUID(route);
        buffer.writeUtf(name, Route.MAX_NAME_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get()
            .getSender();
        if (sender == null)
            return;
        RouteStore store = RouteStore.get(sender.server);

        switch (action) {
            case NEW -> {
                String wanted = name.trim();
                if (wanted.isEmpty())
                    return;
                if (store.byName(wanted) != null) {
                    taken(sender, wanted);
                    return;
                }
                store.put(new Route(wanted));
                store.syncNames();
            }
            // Refused rather than quietly kept: two routes with the same name would make typing one ambiguous.
            case RENAME -> {
                if (!store.rename(route, name.trim()))
                    taken(sender, name.trim());
            }
            case DELETE -> {
                if (store.remove(route))
                    store.syncNames();
            }
        }
    }

    private static void taken(ServerPlayer player, String name) {
        player.displayClientMessage(Component.translatable("create_routes.route.rename.taken", name)
            .withStyle(ChatFormatting.RED), false);
    }

}
