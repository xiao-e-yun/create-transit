package me.xiaoeyun.createtransit.content.dispatch;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.TextScheduleInstruction;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createtransit.registry.CtItems;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The train's antenna: every move is the dispatcher's answer, this entry only asks.
 *
 * Conditions stay unsupported on purpose — {@code tickConditions} then advances right after
 * arrival and the cyclic wrap re-asks; standing by is {@code start} returning null on the
 * engine's own cooldown.
 */
public class TransitTimetableInstruction extends TextScheduleInstruction {

    // Spelled out, not built from a prefix, so scripts/lang_audit.py can see these keys.
    private static final String LANG_NAME = "create_transit.schedule.instruction.timetable";
    private static final String LANG_SUMMARY = "create_transit.schedule.instruction.timetable.summary";
    private static final String LANG_DEPOT = "create_transit.schedule.instruction.timetable.depot";
    private static final String LANG_DEPOT_1 = "create_transit.schedule.instruction.timetable.depot_1";

    @Override
    public ResourceLocation getId() {
        return CreateTransit.asResource("timetable");
    }

    @Override
    public boolean supportsConditions() {
        return false;
    }

    /** The parking bay's station name. */
    public String depot() {
        return getLabelText();
    }

    /** The bay a schedule reports to, or null when it holds no timetable. */
    @Nullable
    public static String depotOf(@Nullable Schedule schedule) {
        if (schedule == null)
            return null;
        for (ScheduleEntry entry : schedule.entries)
            if (entry.instruction instanceof TransitTimetableInstruction timetable)
                return timetable.depot();
        return null;
    }

    /** Whether the schedule came from a timetable item, which is what makes handing one back not an item dupe. */
    public static boolean fromItem(@Nullable Schedule schedule) {
        if (schedule == null)
            return false;
        for (ScheduleEntry entry : schedule.entries)
            if (entry.instruction instanceof TransitTimetableInstruction timetable)
                return timetable.getData()
                    .getBoolean("FromItem");
        return false;
    }

    @Override
    public ItemStack getSecondLineIcon() {
        return CtItems.TRANSIT_TIMETABLE.asStack();
    }

    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(getSecondLineIcon(), Component.translatable(LANG_NAME));
    }

    /** Overridden because the inherited one looks up a key under Create's own namespace, not ours. */
    @Override
    public List<Component> getTitleAs(String type) {
        return ImmutableList.of(Component.translatable(LANG_SUMMARY)
            .withStyle(ChatFormatting.GOLD),
            Component.translatable("create.generic.in_quotes", Component.literal(depot())));
    }

    @Override
    public List<Component> getSecondLineTooltip(int slot) {
        return ImmutableList.of(Component.translatable(LANG_DEPOT),
            Component.translatable(LANG_DEPOT_1)
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    @Nullable
    public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        // Assembly mode re-runs the live entry; null lets Create cancel and cleanly re-plan next tick.
        if (runtime.train.navigation.destination != null)
            return null;

        // Unconditional, and doing double duty: it breaks the already-there hot loop, and since travel
        // never consumes it, the leftover count is a free dwell of extra mail cycles after every arrival.
        runtime.startCooldown();
        return TransitDispatch.nextLeg(runtime, level, depot());
    }

}
