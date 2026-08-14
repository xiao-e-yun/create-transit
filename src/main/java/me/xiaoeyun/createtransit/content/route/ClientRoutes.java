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

/** What the client knows about routes; the route currently open in the editor is read live from there instead. */
public class ClientRoutes {

    private static Map<UUID, RouteListPacket.Line> routes = Map.of();

    /** {@link #reach} worked out, until the next time any of this changes. */
    private static final Map<UUID, Predicate<String>> reaching = new HashMap<>();

    public static void accept(Map<UUID, RouteListPacket.Line> incoming) {
        routes = incoming;
        reaching.clear();
    }

    /** Every route, in the order the server holds them. */
    public static Map<UUID, String> all() {
        Map<UUID, String> labels = new LinkedHashMap<>(routes.size());
        routes.forEach((id, line) -> labels.put(id, line.name()));
        return Collections.unmodifiableMap(labels);
    }

    /** What a route is currently called, or null when it is gone. */
    @Nullable
    public static String nameOf(UUID id) {
        RouteListPacket.Line line = routes.get(id);
        return line == null ? null : line.name();
    }

    /** Every route except those that would cycle back to {@code editing}; a cycle is refused only where the route is saved. */
    public static List<UUID> referenceable(@Nullable UUID editing) {
        return routes.keySet()
            .stream()
            .filter(id -> editing == null || !reaches(id, editing))
            .toList();
    }

    /** Every station reachable by following this route to any depth, cached until {@link #accept} changes what routes exist. */
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

    /** Whether following {@code candidate} eventually reaches {@code target}, counting self-reference as a cycle. */
    private static boolean reaches(UUID candidate, UUID target) {
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
