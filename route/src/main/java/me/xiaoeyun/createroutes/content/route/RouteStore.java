package me.xiaoeyun.createroutes.content.route;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;

import me.xiaoeyun.createroutes.network.CrPackets;
import me.xiaoeyun.createroutes.network.RouteListPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/** Every route in the world, in one place — the overworld's save data, since a route named in one dimension means the same everywhere. */
public class RouteStore extends SavedData {

    private static final String ID = "create_routes_routes";
    private static final String NBT_ROUTES = "Routes";

    private final Map<UUID, Route> routes = new LinkedHashMap<>();

    public static RouteStore get(MinecraftServer server) {
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(new SavedData.Factory<>(RouteStore::new, RouteStore::load), ID);
    }

    @Nullable
    public Route get(UUID id) {
        return routes.get(id);
    }

    @Nullable
    public Route byName(String name) {
        for (Route route : routes.values())
            if (route.name.equals(name))
                return route;
        return null;
    }

    public void put(Route route) {
        routes.put(route.id, route);
        setDirty();
    }

    /** Tells every client which routes exist and where they go. */
    public void syncNames() {
        PacketDistributor.sendToAllPlayers(lines());
    }

    public static void syncNamesTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, get(player.server).lines());
    }

    /** Every route as a name, the stations it stops at, and the routes it follows. */
    private RouteListPacket lines() {
        Map<UUID, RouteListPacket.Line> lines = new LinkedHashMap<>();
        for (Route route : routes.values()) {
            List<String> filters = new ArrayList<>();
            List<UUID> references = new ArrayList<>();
            for (ScheduleEntry entry : route.entries) {
                RouteReference reference = RouteReference.of(entry.instruction);
                if (reference != null)
                    references.add(reference.route());
                else if (entry.instruction instanceof DestinationInstruction destination)
                    filters.add(destination.getFilterForRegex());
            }
            lines.put(route.id, new RouteListPacket.Line(route.name, filters, references));
        }
        return new RouteListPacket(lines);
    }

    public boolean remove(UUID id) {
        if (routes.remove(id) == null)
            return false;
        setDirty();
        return true;
    }

    /** False for a blank, over-long, or already-taken name, none of which are written. */
    public boolean rename(UUID id, String to) {
        Route route = routes.get(id);
        if (route == null || to.isBlank() || to.length() > Route.MAX_NAME_LENGTH)
            return false;

        Route taken = byName(to);
        if (taken != null && !taken.id.equals(id))
            return false;

        route.name = to;
        setDirty();
        syncNames();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Route route : routes.values())
            list.add(route.write(registries));
        tag.put(NBT_ROUTES, list);
        return tag;
    }

    private static RouteStore load(CompoundTag tag, HolderLookup.Provider registries) {
        RouteStore store = new RouteStore();
        for (Tag entry : tag.getList(NBT_ROUTES, Tag.TAG_COMPOUND)) {
            Route route = Route.read(registries, (CompoundTag) entry);
            if (route != null)
                store.routes.put(route.id, route);
        }
        return store;
    }

}
