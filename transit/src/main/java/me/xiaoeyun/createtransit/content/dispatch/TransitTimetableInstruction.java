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
import net.minecraft.nbt.CompoundTag;
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

    /*
     * The display overrides below are English literals rather than lang keys, because
     * nothing a player can do reaches them. They exist for ScheduleScreen, and this
     * instruction never appears there: it is unregistered, so the editor's list cannot
     * offer it, and ScheduleRuntimeMixin hands back the timetable item instead of a
     * schedule, so an enrolled train's schedule cannot be opened either. The only way
     * left is a schedule whose FromItem flag was edited away by hand, and that does not
     * earn three translations -- it earns something readable.
     */

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

    /** The one-stop schedule a timetable item applies. */
    public static Schedule schedule(String depot) {
        TransitTimetableInstruction instruction = new TransitTimetableInstruction();
        instruction.getData()
            .putString("Text", depot);
        instruction.getData()
            .putBoolean("FromItem", true);
        ScheduleEntry entry = new ScheduleEntry();
        entry.instruction = instruction;
        Schedule schedule = new Schedule();
        schedule.entries.add(entry);
        return schedule;
    }

    /** Mirrors {@code ScheduleInstruction.fromTag}'s tail, which our unregistered id never reaches. */
    public static TransitTimetableInstruction read(CompoundTag tag) {
        TransitTimetableInstruction instruction = new TransitTimetableInstruction();
        instruction.readAdditional(tag);
        CompoundTag data = tag.getCompound("Data");
        instruction.readAdditional(data);
        instruction.data = data;
        return instruction;
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
        return Pair.of(getSecondLineIcon(), Component.literal("Transport Timetable"));
    }

    /** Overridden because the inherited one looks up a key under Create's own namespace, not ours. */
    @Override
    public List<Component> getTitleAs(String type) {
        return ImmutableList.of(Component.literal("Serves the dispatcher, parking at:")
            .withStyle(ChatFormatting.GOLD),
            Component.translatable("create.generic.in_quotes", Component.literal(depot())));
    }

    @Override
    public List<Component> getSecondLineTooltip(int slot) {
        return ImmutableList.of(Component.literal("Parking Bay Station"),
            Component.literal("The exact station name of this train's own bay")
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
