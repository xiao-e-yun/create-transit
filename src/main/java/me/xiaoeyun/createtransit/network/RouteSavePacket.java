package me.xiaoeyun.createtransit.network;

import java.util.UUID;
import java.util.function.Supplier;

import com.simibubi.create.content.trains.schedule.Schedule;

import me.xiaoeyun.createtransit.content.route.Route;
import me.xiaoeyun.createtransit.content.route.RouteEditSession;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** A route editor's whole close, in one message; self-names its route since the server keeps no record of who is editing what. */
public class RouteSavePacket {

    /** One key, because a buffer writes a compound and the payload is a list. */
    private static final String NBT_DEFAULTS = "Defaults";

    private final UUID route;

    private final CompoundTag schedule;

    private final String name;

    private final ListTag defaults;

    public RouteSavePacket(UUID route, CompoundTag schedule, String name, ListTag defaults) {
        this.route = route;
        this.schedule = schedule;
        this.name = name;
        this.defaults = defaults;
    }

    public RouteSavePacket(FriendlyByteBuf buffer) {
        route = buffer.readUUID();
        schedule = buffer.readNbt();
        name = buffer.readUtf(Route.MAX_NAME_LENGTH);
        CompoundTag tag = buffer.readNbt();
        defaults = tag == null ? new ListTag() : tag.getList(NBT_DEFAULTS, Tag.TAG_LIST);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(route);
        buffer.writeNbt(schedule);
        buffer.writeUtf(name, Route.MAX_NAME_LENGTH);
        CompoundTag tag = new CompoundTag();
        tag.put(NBT_DEFAULTS, defaults);
        buffer.writeNbt(tag);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get()
            .getSender();
        if (sender == null)
            return;
        Schedule edited = Schedule.fromTag(schedule == null ? new CompoundTag() : schedule);
        RouteEditSession.save(sender, route, edited, name, defaults);
    }

}
