package me.xiaoeyun.createroutes.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

import me.xiaoeyun.createroutes.content.route.ClientRoutes;
import me.xiaoeyun.createroutes.content.route.RouteReference;
import net.createmod.catnip.data.Couple;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where the world's stations are, and which of them a stop means. A stop carries a filter, not a name
 * (for example {@code Harbour *}), so the relation is many to many.
 *
 * <p>Positions are worked out the way {@code TrainMapManager.drawPoints} does, because a marker anywhere
 * but under Create's own sprite points at nothing.
 */
public class Stations {

    /** One station, where it stands, and which way its sprite is turned. */
    public record At(String name, int x, int z, int rotation) {}

    /**
     * Filters already compiled. A pattern for a given filter never goes stale, so this needs no
     * invalidation; it is cleared on size alone because the keys are typed by a player.
     */
    private static final Map<String, Pattern> PATTERNS = new HashMap<>();

    private Stations() {}

    /** Every station on the dimension the map is showing. */
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
                // Create's own sum, kept to the digit: turned to the track in
                // eighths, plus half a turn if the station faces the far end.
                Vec3 heading = edge.getDirectionAt(along)
                    .normalize();
                int rotation = Mth.positiveModulo(Mth.floor(0.5 + (Math.atan2(heading.z, heading.x)
                    * Mth.RAD_TO_DEG + 90 + (station.isPrimary(node) ? 180 : 0)) / 45), 8);
                found.add(new At(station.name, Mth.floor(at.x()), Mth.floor(at.z()), rotation));
            }
        }
        return found;
    }

    /** The station nearest a point, within a reach, or null. */
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

    /** Which station names a stop means, or nothing at all where it is not going anywhere. */
    public static Predicate<String> meant(ScheduleInstruction instruction) {
        // Checked before the destination test: a follower is also a
        // DestinationInstruction, but it stands for every station its whole route
        // reaches, where its own filter is only the stop it is heading for now.
        RouteReference reference = RouteReference.of(instruction);
        if (reference != null)
            return ClientRoutes.reach(reference.route());
        if (!(instruction instanceof DestinationInstruction destination))
            return name -> false;
        if (PATTERNS.size() > 256)
            PATTERNS.clear();
        Pattern pattern = PATTERNS.computeIfAbsent(destination.getFilterForRegex(), Pattern::compile);
        return name -> pattern.matcher(name)
            .matches();
    }

    /** Every stop's filter, in the table's order. */
    public static List<Predicate<String>> each(List<ScheduleEntry> entries) {
        return entries.stream()
            .map(entry -> meant(entry.instruction))
            .toList();
    }

}
