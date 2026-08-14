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

/** A named run of stops, made of ordinary {@link ScheduleEntry ScheduleEntries} so any Create or addon instruction works inside it. */
public class Route {

    public static final int MAX_NAME_LENGTH = 32;

    /** Never changes, and is what every reference actually points at. */
    public final UUID id;

    /** How deep references may nest; a backstop for a save edited outside the game, since cycles are refused when authored. */
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

    /** Conditions lent to any stop that declares none; never empty, since Create's runtime never advances past an empty condition list. */
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

    private static List<List<ScheduleWaitCondition>> seed() {
        List<List<ScheduleWaitCondition>> columns = new ArrayList<>(1);
        columns.add(new ArrayList<>(List.of(new ScheduledDelay())));
        return columns;
    }

    /**
     * The stops this route contributes, with nested references resolved and borrowed conditions filled in.
     *
     * @param lookup resolves a referenced route; null for a deleted route, which contributes nothing
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

            int before = out.size();
            nested.collect(out, lookup, reference.reversed() != reversed, visiting, depth + 1);
            if (reference.skipFirst() && out.size() > before)
                out.remove(before);
        }

        visiting.remove(id);
    }

    /** Deep-copies inherited conditions, since the result is written into a running train's schedule and must not alias the route's own. */
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

    /** True when any reference this route holds leads back to it. */
    public boolean containsCycle(Function<UUID, Route> lookup) {
        for (ScheduleEntry entry : entries) {
            RouteReference reference = RouteReference.of(entry.instruction);
            if (reference != null && reaches(lookup, reference.route(), new HashSet<>()))
                return true;
        }
        return false;
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

    /** A list of alternatives, in NBT — public because the defaults travel to the editor and back on their own; {@code Schedule} has nowhere to carry them. */
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
        // A hand-written route may leave the id out; it gets one here and keeps it from the next save on.
        Route route = tag.hasUUID(NBT_ID) ? new Route(tag.getUUID(NBT_ID), name) : new Route(name);
        route.color = tag.getInt(NBT_COLOR);
        route.entries = NBTHelper.readCompoundList(tag.getList(NBT_ENTRIES, Tag.TAG_COMPOUND), ScheduleEntry::fromTag);
        // Held to the invariant here too: defaults must never end up empty, or these trains never leave the platform.
        List<List<ScheduleWaitCondition>> defaults = readConditions(tag.getList(NBT_DEFAULTS, Tag.TAG_LIST));
        if (!defaults.isEmpty())
            route.defaults = defaults;
        return route;
    }

}
