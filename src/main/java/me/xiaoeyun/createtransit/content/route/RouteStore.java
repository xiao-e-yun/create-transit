package me.xiaoeyun.createtransit.content.route;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;

import me.xiaoeyun.createtransit.network.CtPackets;
import me.xiaoeyun.createtransit.network.RouteListPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

/**
 * Every route in the world, in one place.
 *
 * <p>Routes are shared by definition — a train references one, it does not
 * carry a copy — so they cannot live in an item or a block. They live with the
 * overworld's save data because a rail network spans dimensions and a route
 * named in the Nether has to mean the same thing as the one named on the
 * surface.
 *
 * <p>Insertion order is kept so a list of routes reads the way the player built
 * it rather than the way a hash landed.
 */
public class RouteStore extends SavedData {

    private static final String ID = "create_transit_routes";
    private static final String NBT_ROUTES = "Routes";

    private final Map<UUID, Route> routes = new LinkedHashMap<>();

    public static RouteStore get(MinecraftServer server) {
        return server.overworld()
            .getDataStorage()
            .computeIfAbsent(RouteStore::load, RouteStore::new, ID);
    }

    @Nullable
    public Route get(UUID id) {
        return routes.get(id);
    }

    /**
     * The route a player means when they type a name.
     *
     * <p>A scan rather than a second index: names change and ids do not, so a
     * map keyed by name is one more thing to keep in agreement for a lookup that
     * happens when somebody types, over a list that is tens long.
     */
    @Nullable
    public Route byName(String name) {
        for (Route route : routes.values())
            if (route.name.equals(name))
                return route;
        return null;
    }

    public Collection<Route> all() {
        return routes.values();
    }

    public void put(Route route) {
        routes.put(route.id, route);
        setDirty();
    }

    /**
     * Tells every client which routes exist and where they go.
     *
     * <p>Called after anything that changes either, rather than hooked onto
     * {@link #setDirty}: the same call now covers a rename and a re-ordered set
     * of stops, because a client draws the second one on its map.
     */
    public void syncNames() {
        CtPackets.CHANNEL.send(PacketDistributor.ALL.noArg(), lines());
    }

    public static void syncNamesTo(ServerPlayer player) {
        CtPackets.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), get(player.server).lines());
    }

    /**
     * Every route as a name, the stations it stops at, and the routes it
     * follows. An instruction that is neither — a wait, a throttle — is nowhere,
     * so it contributes nothing to a map and nothing to a cycle.
     */
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

    /**
     * Renames a route, which is one field: references point at the id, so there
     * is nothing else that knows the old name.
     *
     * <p>Names still have to be unique, because typing one is how a reference is
     * authored and two routes called the same thing would make that ambiguous.
     * That is a rule about the editor rather than about routes, and it goes when
     * a route is picked from a list instead of typed.
     */
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
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Route route : routes.values())
            list.add(route.write());
        tag.put(NBT_ROUTES, list);
        return tag;
    }

    private static RouteStore load(CompoundTag tag) {
        RouteStore store = new RouteStore();
        for (Tag entry : tag.getList(NBT_ROUTES, Tag.TAG_COMPOUND)) {
            Route route = Route.read((CompoundTag) entry);
            if (route != null)
                store.routes.put(route.id, route);
        }
        return store;
    }

}
