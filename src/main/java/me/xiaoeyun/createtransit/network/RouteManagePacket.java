package me.xiaoeyun.createtransit.network;

import java.util.UUID;
import java.util.function.Supplier;

import me.xiaoeyun.createtransit.content.route.Route;
import me.xiaoeyun.createtransit.content.route.RouteStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Which routes exist, changed from the route list.
 *
 * <p>One message for the three, because they are one thing: the set of routes.
 * Each of them ends in the same resync, and splitting them would be three
 * packets that differ only in a word.
 *
 * <p>No permission gate. Routes are shared world data in the same way a
 * station's name is, and Create lets any player who can reach a station rename
 * it; a route reached from a schedule a player is already holding is no
 * different. Gating it on op would lock the feature out of an ordinary
 * singleplayer world, where nobody is one.
 */
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
                if (wanted.isEmpty() || wanted.length() > Route.MAX_NAME_LENGTH)
                    return;
                if (store.byName(wanted) != null) {
                    taken(sender, wanted);
                    return;
                }
                store.put(new Route(wanted));
                store.syncNames();
            }
            // Refused rather than quietly kept, because the name is still how a
            // reference is authored: two routes called the same thing would make
            // typing one ambiguous.
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
        player.displayClientMessage(Component.translatable("create_transit.route.rename.taken", name)
            .withStyle(ChatFormatting.RED), false);
    }

}
