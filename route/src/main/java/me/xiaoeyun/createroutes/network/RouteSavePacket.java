package me.xiaoeyun.createroutes.network;

import java.util.UUID;

import com.simibubi.create.content.trains.schedule.Schedule;

import me.xiaoeyun.createroutes.CreateRoutes;
import me.xiaoeyun.createroutes.content.route.Route;
import me.xiaoeyun.createroutes.content.route.RouteEditSession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A route editor's whole close, in one message; self-names its route since the server keeps no record of who is editing what. */
public record RouteSavePacket(UUID route, CompoundTag schedule, String name, ListTag defaults)
    implements CustomPacketPayload {

    /** One key, because a buffer writes a compound and the payload is a list. */
    private static final String NBT_DEFAULTS = "Defaults";

    public static final Type<RouteSavePacket> TYPE = new Type<>(CreateRoutes.asResource("route_save"));

    public static final StreamCodec<FriendlyByteBuf, RouteSavePacket> STREAM_CODEC =
        StreamCodec.of(RouteSavePacket::write, RouteSavePacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, RouteSavePacket packet) {
        buffer.writeUUID(packet.route);
        buffer.writeNbt(packet.schedule);
        buffer.writeUtf(packet.name, Route.MAX_NAME_LENGTH);
        CompoundTag tag = new CompoundTag();
        tag.put(NBT_DEFAULTS, packet.defaults);
        buffer.writeNbt(tag);
    }

    private static RouteSavePacket read(FriendlyByteBuf buffer) {
        UUID route = buffer.readUUID();
        CompoundTag schedule = buffer.readNbt();
        String name = buffer.readUtf(Route.MAX_NAME_LENGTH);
        CompoundTag tag = buffer.readNbt();
        return new RouteSavePacket(route, schedule, name,
            tag == null ? new ListTag() : tag.getList(NBT_DEFAULTS, Tag.TAG_LIST));
    }

    public static void handle(RouteSavePacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender))
            return;
        // Create reads a schedule against the registries on 1.21; the sender's
        // are the same ones the editor wrote it with.
        Schedule edited = Schedule.fromTag(sender.registryAccess(),
            packet.schedule == null ? new CompoundTag() : packet.schedule);
        RouteEditSession.save(sender, packet.route, edited, packet.name, packet.defaults);
    }

}
