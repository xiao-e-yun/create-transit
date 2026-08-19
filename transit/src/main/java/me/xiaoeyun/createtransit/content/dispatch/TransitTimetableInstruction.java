package me.xiaoeyun.createtransit.content.dispatch;

import javax.annotation.Nullable;

import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.TextScheduleInstruction;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createtransit.registry.CtItems;
import net.createmod.catnip.data.Pair;
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

    public static final ResourceLocation ID = CreateTransit.asResource("timetable");

    /** The id as it is spelled in NBT; the mixin that reads a tag back compares against this, not a literal. */
    public static final String ID_TAG = ID.toString();

    @Override
    public ResourceLocation getId() {
        return ID;
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

    /** Abstract in IScheduleInput, so it must exist; nothing a player can do renders it. */
    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(CtItems.TRANSIT_TIMETABLE.asStack(), Component.literal("Transport Timetable"));
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
