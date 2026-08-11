package me.xiaoeyun.createtransit.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.simibubi.create.CreateClient;
import com.simibubi.create.compat.trainmap.TrainMapRenderer;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.content.trains.station.GlobalStation;

import me.xiaoeyun.createtransit.content.route.ClientRoutes;
import me.xiaoeyun.createtransit.content.route.RouteReference;
import net.createmod.catnip.data.Couple;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where the world's stations are, and which of them a stop means.
 *
 * <p>A stop does not name a station. It carries a filter — {@code Harbour *} is
 * one stop and may be four platforms — so the relation between the table and the
 * map is many to many, and two stations sharing a name is not an ambiguity to
 * resolve but two answers to show. {@code start()} does the same thing at
 * runtime: every match goes into a list and the navigation picks the nearest one
 * it can reach.
 *
 * <p>The positions are worked out the way {@code TrainMapManager.drawPoints}
 * works them out, because that is where they have to agree — a marker of ours
 * standing anywhere else than Create's own sprite is a marker pointing at
 * nothing.
 */
public class Stations {

    /**
     * One station, where it stands, and which way its sprite is turned.
     *
     * <p>The turn is carried because a station of ours is Create's own sprite
     * drawn again in another colour, and a sprite drawn the other way up is a
     * second station standing beside the first.
     */
    public record At(String name, int x, int z, int rotation) {}

    private Stations() {}

    /**
     * Every station on the dimension the map is showing.
     *
     * <p>Read fresh rather than kept: stations are placed and renamed while this
     * screen is open, and the list is a few dozen entries on any world that has
     * a railway at all.
     */
    public static List<At> all() {
        List<At> found = new ArrayList<>();
        ResourceKey<Level> dimension = TrainMapRenderer.INSTANCE.trackingDim;
        if (dimension == null)
            return found;

        for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                Couple<TrackNodeLocation> ends = station.edgeLocation;
                TrackNode node = graph.locateNode(ends.getFirst());
                TrackNode other = graph.locateNode(ends.getSecond());
                if (node == null || other == null || node.getLocation().dimension != dimension)
                    continue;

                TrackEdge edge = graph.getConnection(Couple.create(node, other));
                if (edge == null)
                    continue;

                double along = station.getLocationOn(edge);
                Vec3 at = edge.getPosition(graph, along / edge.getLength());
                // Create's own sum, kept to the digit: the sprite is turned to
                // the track it sits on, in eighths, and which end of the track
                // the station faces is half a turn of it.
                Vec3 heading = edge.getDirectionAt(along)
                    .normalize();
                int rotation = Mth.positiveModulo(Mth.floor(0.5 + (Math.atan2(heading.z, heading.x)
                    * Mth.RAD_TO_DEG + 90 + (station.isPrimary(node) ? 180 : 0)) / 45), 8);
                found.add(new At(station.name, Mth.floor(at.x()), Mth.floor(at.z()), rotation));
            }
        }
        return found;
    }

    /**
     * The station nearest a point, within a reach, or null.
     *
     * <p>The reach is the caller's because it is a screen distance divided by
     * the zoom: a fixed distance in blocks is a target that shrinks to nothing
     * the moment the map is pulled back far enough to see the route, which is
     * where a player is most likely to be reaching for a station.
     */
    public static At near(List<At> stations, double x, double z, double reach) {
        At found = null;
        double best = reach * reach;
        for (At station : stations) {
            double dx = station.x() - x;
            double dz = station.z() - z;
            double away = dx * dx + dz * dz;
            if (away > best)
                continue;
            best = away;
            found = station;
        }
        return found;
    }

    /**
     * Which station names a stop means, or nothing at all where it is not going
     * anywhere — a wait, or a route nested inside this one.
     *
     * <p>Compiled once and handed back rather than tested by name, because the
     * caller has one filter and every station in the world to try it against.
     */
    public static Predicate<String> meant(ScheduleInstruction instruction) {
        // Before the destination test, not after it: a follower is a
        // DestinationInstruction — it has to be, to stay visible to Create's
        // predictions — and its field holds a line's name, not a station's.
        RouteReference reference = RouteReference.of(instruction);
        if (reference != null)
            return ClientRoutes.reach(reference.route());
        if (!(instruction instanceof DestinationInstruction destination))
            return name -> false;
        Pattern pattern = Pattern.compile(destination.getFilterForRegex());
        return name -> pattern.matcher(name)
            .matches();
    }

    /**
     * Every stop's filter, in the table's order.
     *
     * <p>Kept as a list rather than folded into one test, because the map asks
     * two questions of it — which stations the route touches at all, and which
     * of them one row means — and only the second can tell the stops apart.
     */
    public static List<Predicate<String>> each(List<ScheduleEntry> entries) {
        return entries.stream()
            .map(entry -> meant(entry.instruction))
            .toList();
    }

}
