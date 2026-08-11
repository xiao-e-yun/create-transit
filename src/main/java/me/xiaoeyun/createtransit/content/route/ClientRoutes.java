package me.xiaoeyun.createtransit.content.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import me.xiaoeyun.createtransit.network.RouteListPacket;

/**
 * What the client knows about routes: which ones exist, what each is called,
 * and where each one goes.
 *
 * <p>Both directions of the name are needed because they are asked at different
 * moments. A player types a name into a reference, and that has to become the id
 * the reference actually stores — a name is a label, and the label is not what
 * anything points at. Going back the other way is how a reference already
 * authored says which route it means without asking the server.
 *
 * <p>Where a route goes is here for the routes this client is <em>not</em>
 * editing: the one on screen is read live from the editor, and a stop that
 * follows another route can only say what it means by asking this.
 *
 * <p>The server stays the authority. Nothing here is trusted; a reference to a
 * route that has since been deleted simply fails to resolve when a train reaches
 * it, and a cycle is refused where the route is saved rather than here.
 *
 * <p>Deliberately not cleared on disconnect: a stale list costs a wrong
 * suggestion, while clearing it would need a hook on every way a session can
 * end. The next join replaces it wholesale.
 */
public class ClientRoutes {

    private static Map<UUID, RouteListPacket.Line> routes = Map.of();

    /** The same thing with only the names, which is what most of this is asked for. */
    private static Map<UUID, String> names = Map.of();

    /** {@link #reach} worked out, until the next time any of this changes. */
    private static final Map<UUID, Predicate<String>> reaching = new HashMap<>();

    public static void accept(Map<UUID, RouteListPacket.Line> incoming) {
        // Copied into a map that keeps its order, so a list of routes reads the
        // way the server sent it rather than the way a hash landed.
        routes = new LinkedHashMap<>(incoming);
        Map<UUID, String> labels = new LinkedHashMap<>(incoming.size());
        routes.forEach((id, line) -> labels.put(id, line.name()));
        names = labels;
        reaching.clear();
    }

    /** Every route, in the order the server holds them. */
    public static Map<UUID, String> all() {
        return Collections.unmodifiableMap(names);
    }

    /** The route a typed name means, or null when nothing is called that. */
    @Nullable
    public static UUID idOf(String name) {
        for (Map.Entry<UUID, String> route : names.entrySet())
            if (route.getValue()
                .equals(name))
                return route.getKey();
        return null;
    }

    /** What a route is currently called, or null when it is gone. */
    @Nullable
    public static String nameOf(UUID id) {
        return names.get(id);
    }

    /**
     * The routes a reference inside {@code editing} may point at, in order.
     *
     * <p>Everything, minus the ones that lead back to where the reference is
     * being written. The server refuses a cycle when the route is saved, and
     * being offered a name that will be refused — after the screen it was chosen
     * on has closed — is a choice that only wastes the trip.
     *
     * <p>Null means a train's own schedule, which nothing can lead back to.
     */
    public static List<UUID> referenceable(@Nullable UUID editing) {
        return names.keySet()
            .stream()
            .filter(id -> editing == null || !reaches(id, editing))
            .toList();
    }

    /**
     * Every station a route stops at, to whatever depth it nests.
     *
     * <p>One predicate for the whole closure rather than a list per level: what
     * asks is a stop on a map, and a stop that follows a route means all of
     * where that route goes — including the routes it follows in turn.
     *
     * <p>Kept, because the asking is once a frame for every row that follows a
     * route and the answer is a compiled pattern. There is exactly one moment it
     * could go stale — {@link #accept}, which is the only thing that changes
     * what any of this says — so that is where it is dropped. The route being
     * edited is not in here at all: its stops are read live from the editor,
     * because they are not the server's yet.
     */
    public static Predicate<String> reach(UUID id) {
        return reaching.computeIfAbsent(id, route -> {
            List<Pattern> patterns = new ArrayList<>();
            gather(route, patterns, new HashSet<>());
            if (patterns.isEmpty())
                return name -> false;
            return name -> patterns.stream()
                .anyMatch(pattern -> pattern.matcher(name)
                    .matches());
        });
    }

    private static void gather(UUID id, List<Pattern> into, Set<UUID> seen) {
        RouteListPacket.Line line = routes.get(id);
        if (line == null || !seen.add(id))
            return;
        for (String filter : line.filters())
            into.add(Pattern.compile(filter));
        for (UUID nested : line.references())
            gather(nested, into, seen);
    }

    /**
     * Whether following {@code candidate} comes back to {@code target}, which is
     * what makes a reference to it a cycle.
     *
     * <p>A route reaching itself counts, because the shortest cycle there is is
     * a route that follows itself.
     */
    public static boolean reaches(UUID candidate, UUID target) {
        return candidate.equals(target) || leadsTo(candidate, target, new HashSet<>());
    }

    private static boolean leadsTo(UUID id, UUID target, Set<UUID> seen) {
        RouteListPacket.Line line = routes.get(id);
        if (line == null || !seen.add(id))
            return false;
        for (UUID nested : line.references())
            if (nested.equals(target) || leadsTo(nested, target, seen))
                return true;
        return false;
    }

}
