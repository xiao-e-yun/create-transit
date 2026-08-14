package me.xiaoeyun.createtransit.content.schedule;

import com.simibubi.create.content.trains.schedule.ScheduleEntry;

/**
 * An instruction that wants its entry run again instead of the runtime advancing; a mixin asks {@link #again}
 * at the exact point Create would otherwise move on.
 *
 * <p>Whoever answers yes owns the ending — an instruction that always answers yes is a train that never
 * reaches its next entry.
 */
public interface Repeats {

    boolean again();

    /** Drops whatever about this entry only makes sense while a particular train is running it. */
    void clearTransient(ScheduleEntry entry);

}
