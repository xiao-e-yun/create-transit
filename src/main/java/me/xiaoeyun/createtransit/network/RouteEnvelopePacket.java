package me.xiaoeyun.createtransit.network;

import java.util.UUID;
import java.util.function.Supplier;

import me.xiaoeyun.createtransit.content.route.Route;
import me.xiaoeyun.createtransit.content.route.RouteEditSession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * The part of a route that is not a schedule.
 *
 * <p>Stops travel inside Create's own closing packet, because a route's stops
 * really are a schedule's entries. Its name and default conditions are not — a
 * {@code Schedule} has no field either fits in — so they need a message, and
 * this is it.
 *
 * <p>Sent from {@code removed()} straight after Create's, so it is handled
 * second and the stops are already in. It names its route rather than relying on
 * the session, so nothing about it depends on arriving in that order.
 */
public class RouteEnvelopePacket {

    /** One key, because a buffer writes a compound and the payload is a list. */
    private static final String NBT_DEFAULTS = "Defaults";

    private final UUID route;

    private final String name;

    private final ListTag defaults;

    public RouteEnvelopePacket(UUID route, String name, ListTag defaults) {
        this.route = route;
        this.name = name;
        this.defaults = defaults;
    }

    public RouteEnvelopePacket(FriendlyByteBuf buffer) {
        route = buffer.readUUID();
        name = buffer.readUtf(Route.MAX_NAME_LENGTH);
        CompoundTag tag = buffer.readNbt();
        defaults = tag == null ? new ListTag() : tag.getList(NBT_DEFAULTS, Tag.TAG_LIST);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(route);
        buffer.writeUtf(name, Route.MAX_NAME_LENGTH);
        CompoundTag tag = new CompoundTag();
        tag.put(NBT_DEFAULTS, defaults);
        buffer.writeNbt(tag);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get()
            .getSender();
        if (sender != null)
            RouteEditSession.saveEnvelope(sender, route, name, defaults);
    }

}
