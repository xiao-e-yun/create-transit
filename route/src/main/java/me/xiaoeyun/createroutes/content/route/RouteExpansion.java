package me.xiaoeyun.createroutes.content.route;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;

import net.minecraft.core.HolderLookup;

/**
 * Turns a {@link FollowRouteInstruction} into the stops it stands for, inside a running train's schedule only.
 *
 * <p>Every reader of a schedule takes one entry to be one stop, so the only way to be legible to all of them
 * is to actually be N entries.
 */
public class RouteExpansion {

    /** Marks an inserted stop, and gives its place in the block. */
    private static final String NBT_ORDINAL = "CreateRoutesOrdinal";

    private RouteExpansion() {}

    /** A rebuilt schedule and where the train sits in it. */
    public record Expansion(Schedule schedule, int currentEntry) {}

    /** Where a train sits, in terms that survive a block changing length. */
    private record Anchor(int authored, int ordinal) {

        static final int HEADER = -1;
    }

    /**
     * Rebuilds every block from the routes as they are now, into a fresh {@link Schedule}.
     *
     * <p>A new object rather than an edit in place, because that is how readers are told a schedule changed —
     * Create Railways Navigator holds its parse until {@code runtime.getSchedule()} is a different instance.
     */
    public static Expansion expand(HolderLookup.Provider registries, Schedule schedule, RouteStore store,
        int currentEntry) {
        Anchor anchor = anchorOf(schedule.entries, currentEntry);
        List<ScheduleEntry> out = new ArrayList<>(schedule.entries.size());
        int authored = 0;
        int moved = 0;

        for (ScheduleEntry entry : schedule.entries) {
            // Dropping the old stops is what makes this safe to run again over its own output.
            if (isStop(entry))
                continue;

            int header = out.size();
            out.add(entry);
            int block = 0;

            RouteReference reference = RouteReference.of(entry.instruction);
            if (reference != null) {
                entry.instruction.getData()
                    .putInt(FollowRouteInstruction.NBT_HASH, store.hashOf(reference.route()));
                block = appendStops(registries, out, reference, store);
            }

            if (authored == anchor.authored())
                // Past the end means the route lost stops under this train, and the block is simply over.
                moved = anchor.ordinal() == Anchor.HEADER ? header
                    : header + 1 + Math.min(anchor.ordinal(), block);
            authored++;
        }

        Schedule rebuilt = new Schedule();
        rebuilt.entries = out;
        rebuilt.cyclic = schedule.cyclic;
        rebuilt.savedProgress = schedule.savedProgress;
        // One past the end is left alone: checkEndOfScheduleReached wraps a cyclic schedule and stops the rest.
        return new Expansion(rebuilt, Math.max(0, Math.min(moved, out.size())));
    }

    private static int appendStops(HolderLookup.Provider registries, List<ScheduleEntry> out,
        RouteReference reference, RouteStore store) {
        Route route = store.get(reference.route());
        if (route == null)
            return 0;

        List<ScheduleEntry> stops =
            route.flatten(registries, store::get, reference.reversed(), reference.skipTerminus());
        for (int i = 0; i < stops.size(); i++) {
            stops.get(i).instruction.getData()
                .putInt(NBT_ORDINAL, i);
            out.add(stops.get(i));
        }
        return stops.size();
    }

    /** True when any block was built against a route that has changed since. */
    public static boolean isStale(List<ScheduleEntry> entries, RouteStore store) {
        for (ScheduleEntry entry : entries) {
            // An inserted stop carries no hash of its own; only the headers were stamped.
            if (isStop(entry))
                continue;

            RouteReference reference = RouteReference.of(entry.instruction);
            if (reference != null && entry.instruction.getData()
                .getInt(FollowRouteInstruction.NBT_HASH) != store.hashOf(reference.route()))
                return true;
        }
        return false;
    }

    /**
     * Strips every inserted stop and the marks that only mean something to a running train.
     *
     * @return where {@code currentEntry} lands among the authored entries
     */
    public static int collapse(List<ScheduleEntry> entries, int currentEntry) {
        Anchor anchor = anchorOf(entries, currentEntry);
        entries.removeIf(RouteExpansion::isStop);
        for (ScheduleEntry entry : entries)
            entry.instruction.getData()
                .remove(FollowRouteInstruction.NBT_HASH);
        return Math.max(0, Math.min(anchor.authored(), entries.size() - 1));
    }

    private static Anchor anchorOf(List<ScheduleEntry> entries, int currentEntry) {
        if (currentEntry < 0 || currentEntry >= entries.size())
            return new Anchor(0, Anchor.HEADER);

        int authored = 0;
        for (int i = 0; i < currentEntry; i++)
            if (!isStop(entries.get(i)))
                authored++;

        ScheduleEntry at = entries.get(currentEntry);
        // A stop belongs to the last authored entry before it, one short of the count just taken.
        return isStop(at) ? new Anchor(Math.max(0, authored - 1), ordinalOf(at))
            : new Anchor(authored, Anchor.HEADER);
    }

    static boolean isStop(ScheduleEntry entry) {
        return entry.instruction.getData()
            .contains(NBT_ORDINAL);
    }

    private static int ordinalOf(ScheduleEntry entry) {
        return entry.instruction.getData()
            .getInt(NBT_ORDINAL);
    }

}
