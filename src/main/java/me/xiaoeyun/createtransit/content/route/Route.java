package me.xiaoeyun.createtransit.content.route;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * A named run of stops that many trains can share.
 *
 * <p>A route is a schedule with a name. Its stops are ordinary
 * {@link ScheduleEntry ScheduleEntries}, which is not a convenience but the
 * whole design: every instruction and condition Create or another addon ever
 * registers is usable inside a route without us knowing it exists, and a stop
 * can be edited with the widgets that stop already carries.
 *
 * <p>Stops with no conditions of their own borrow the route's
 * {@link #defaults}. That single rule is the entire default/override system —
 * an empty condition list already means "nothing special here", so inheriting
 * needs no flag to mark it.
 *
 * <p>A route may reference another route, and the reference may run it
 * backwards. That is how a there-and-back service is written once instead of
 * twice: one route holds the stops, and the round trip references it forwards
 * and then reversed. {@link #flatten} resolves all of that into a plain list of
 * stops, so nothing downstream has to know a route was nested at all.
 *
 * <p>The {@link #id} is the identity and the {@link #name} is a label. Renaming
 * is then one field write: nothing points at the name, so nothing has to be
 * found and repointed, and a train part way through a route cannot be stranded
 * by someone editing the name it went in under. The name is still what a player
 * types and reads — {@code FollowRouteInstruction} keeps it beside the id it
 * resolved to, so a reference says which route it means without a lookup.
 */
public class Route {

    public static final int MAX_NAME_LENGTH = 32;

    /** Never changes, and is what every reference actually points at. */
    public final UUID id;

    /**
     * How deep references may nest. Cycles are refused when authored, so this
     * is the backstop for a save edited outside the game rather than the real
     * defence.
     */
    private static final int MAX_DEPTH = 8;

    private static final String NBT_ID = "Id";
    private static final String NBT_NAME = "Name";
    private static final String NBT_COLOR = "Color";
    private static final String NBT_ENTRIES = "Entries";
    private static final String NBT_DEFAULTS = "Defaults";

    public String name;
    public int color;

    /** The stops, in the order a train following this route forwards visits them. */
    public List<ScheduleEntry> entries;

    /**
     * Conditions lent to any stop that declares none. Same shape as
     * {@link ScheduleEntry#conditions}, and never empty.
     *
     * <p>Never empty is the invariant the whole default/override system rests
     * on. A stop declares nothing to mean "the usual", so there has to be a
     * usual — and Create's runtime never leaves a stop whose condition list is
     * empty, so "the usual" being nothing would be a train that never departs.
     * The editor refuses to remove the last one for that reason, and a route
     * arriving from anywhere else is given one here.
     */
    public List<List<ScheduleWaitCondition>> defaults;

    public Route(String name) {
        this(UUID.randomUUID(), name);
    }

    private Route(UUID id, String name) {
        this.id = id;
        this.name = name;
        this.color = 0;
        this.entries = new ArrayList<>();
        this.defaults = seed();
    }

    /** The one condition a route cannot be without: a wait the player can set. */
    private static List<List<ScheduleWaitCondition>> seed() {
        List<List<ScheduleWaitCondition>> columns = new ArrayList<>(1);
        columns.add(new ArrayList<>(List.of(new ScheduledDelay())));
        return columns;
    }

    /**
     * The stops this route contributes, with nested references resolved and
     * borrowed conditions already filled in.
     *
     * <p>Flattening rather than walking a stack of positions is what keeps a
     * follower's progress a single index: the shape of the route is decided
     * here, once, and the follower only counts. It also makes reversal and a
     * dropped leading stop structural — the list simply does not contain what
     * was dropped, so no later code has to remember why.
     *
     * @param lookup    resolves a referenced route's name; may answer null for a
     *                  route that has been deleted, which contributes nothing
     * @param reversed  visit the stops back to front
     * @param skipFirst drop the leading stop, which is how a reversal joins the
     *                  pass before it without stopping twice at the station they
     *                  share
     */
    public List<ScheduleEntry> flatten(Function<UUID, Route> lookup, boolean reversed, boolean skipFirst) {
        List<ScheduleEntry> out = new ArrayList<>();
        collect(out, lookup, reversed, new HashSet<>(), 0);
        if (skipFirst && !out.isEmpty())
            out.remove(0);
        return out;
    }

    private void collect(List<ScheduleEntry> out, Function<UUID, Route> lookup, boolean reversed,
        Set<UUID> visiting, int depth) {
        if (depth > MAX_DEPTH || !visiting.add(id))
            return;

        for (int i = 0; i < entries.size(); i++) {
            ScheduleEntry entry = entries.get(reversed ? entries.size() - 1 - i : i);
            RouteReference reference = RouteReference.of(entry.instruction);

            if (reference == null) {
                out.add(withInheritedConditions(entry));
                continue;
            }

            Route nested = lookup.apply(reference.route());
            if (nested == null)
                continue;

            // Reversing the outer route has to reverse what its references do
            // too, or a round trip read backwards would still run each leg
            // forwards and reach that leg's stops in the wrong order.
            int before = out.size();
            nested.collect(out, lookup, reference.reversed() != reversed, visiting, depth + 1);
            if (reference.skipFirst() && out.size() > before)
                out.remove(before);
        }

        visiting.remove(id);
    }

    /**
     * The stop as it will actually be waited on. A stop that declares no
     * conditions is given the route's, deeply, because the result is about to
     * be written into a running train's schedule and must not alias anything
     * the route still holds.
     *
     * <p>And never nothing, because {@link #defaults} is never nothing. That is
     * what the invariant is for: {@code ScheduleRuntime.tickConditions} loops
     * over the columns and moves on inside the loop, so an entry with none is
     * one it never advances past — the train sits at the platform for good.
     */
    private ScheduleEntry withInheritedConditions(ScheduleEntry entry) {
        ScheduleEntry copy = entry.clone();
        if (copy.conditions.isEmpty())
            copy.conditions = copyConditions(defaults);
        return copy;
    }

    private static List<List<ScheduleWaitCondition>> copyConditions(List<List<ScheduleWaitCondition>> source) {
        List<List<ScheduleWaitCondition>> out = new ArrayList<>(source.size());
        for (List<ScheduleWaitCondition> column : source) {
            List<ScheduleWaitCondition> copy = new ArrayList<>(column.size());
            for (ScheduleWaitCondition condition : column)
                copy.add(ScheduleWaitCondition.fromTag(condition.write()));
            out.add(copy);
        }
        return out;
    }

    /**
     * True when any reference this route holds leads back to it.
     *
     * <p>Checked on the way in from an editor, where stops arrive as a set
     * rather than one at a time and so cannot each be refused as they are
     * authored.
     */
    public boolean containsCycle(Function<UUID, Route> lookup) {
        for (ScheduleEntry entry : entries) {
            RouteReference reference = RouteReference.of(entry.instruction);
            if (reference != null && reachedBy(lookup, reference.route()))
                return true;
        }
        return false;
    }

    /**
     * True when {@code candidate} already reaches this route, which would make
     * a reference to it a cycle. Checked when a reference is authored, so the
     * player is told at the moment they cause it rather than when a train hangs.
     */
    public boolean reachedBy(Function<UUID, Route> lookup, UUID candidate) {
        return reaches(lookup, candidate, new HashSet<>());
    }

    private boolean reaches(Function<UUID, Route> lookup, UUID candidate, Set<UUID> seen) {
        if (candidate.equals(id))
            return true;
        if (!seen.add(candidate))
            return false;
        Route route = lookup.apply(candidate);
        if (route == null)
            return false;
        for (ScheduleEntry entry : route.entries) {
            RouteReference reference = RouteReference.of(entry.instruction);
            if (reference != null && reaches(lookup, reference.route(), seen))
                return true;
        }
        return false;
    }

    /**
     * A list of alternatives, in NBT. The same shape Create gives
     * {@link ScheduleEntry#conditions}, and public because the defaults travel
     * to the editor and back on their own — a {@code Schedule} has nowhere to
     * carry them.
     */
    public static ListTag writeConditions(List<List<ScheduleWaitCondition>> columns) {
        ListTag out = new ListTag();
        for (List<ScheduleWaitCondition> column : columns)
            out.add(NBTHelper.writeCompoundList(column, ScheduleWaitCondition::write));
        return out;
    }

    public static List<List<ScheduleWaitCondition>> readConditions(ListTag columns) {
        List<List<ScheduleWaitCondition>> out = new ArrayList<>();
        for (Tag column : columns)
            if (column instanceof ListTag list)
                out.add(NBTHelper.readCompoundList(list, ScheduleWaitCondition::fromTag));
        return out;
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(NBT_ID, id);
        tag.putString(NBT_NAME, name);
        tag.putInt(NBT_COLOR, color);
        tag.put(NBT_ENTRIES, NBTHelper.writeCompoundList(entries, ScheduleEntry::write));
        tag.put(NBT_DEFAULTS, writeConditions(defaults));
        return tag;
    }

    @Nullable
    public static Route read(CompoundTag tag) {
        String name = tag.getString(NBT_NAME);
        if (name.isBlank())
            return null;
        // A hand-written route is allowed to leave the id out; it gets one here
        // and keeps it from the next save on. Nothing can be pointing at a route
        // that was not in the file a moment ago, so there is nothing to break.
        Route route = tag.hasUUID(NBT_ID) ? new Route(tag.getUUID(NBT_ID), name) : new Route(name);
        route.color = tag.getInt(NBT_COLOR);
        route.entries = NBTHelper.readCompoundList(tag.getList(NBT_ENTRIES, Tag.TAG_COMPOUND), ScheduleEntry::fromTag);
        // Read, then held to the invariant: a file written by hand, or by a
        // version of this that let the last one go, must not come back as a
        // route whose trains never leave a platform.
        List<List<ScheduleWaitCondition>> defaults = readConditions(tag.getList(NBT_DEFAULTS, Tag.TAG_LIST));
        if (!defaults.isEmpty())
            route.defaults = defaults;
        return route;
    }

}
