package me.xiaoeyun.createtransit.content.schedule;

import com.simibubi.create.content.trains.schedule.ScheduleEntry;

/**
 * An instruction that wants its entry run again once the train departs.
 *
 * <p>Create's runtime moves on the moment a stop's conditions are met —
 * {@code tickConditions} increments the entry and nothing asks the instruction
 * whether it had more to do. So an instruction that stands for a whole run of
 * stations rather than one journey has no way to express itself, and every such
 * run has to be written out as one entry per station.
 *
 * <p>This is the missing question. A mixin asks it where the runtime would
 * otherwise move on, and an instruction that answers yes keeps the entry.
 * Nothing else changes: the entry is started again from the top, so the
 * instruction decides where to go next exactly as it decided the first time.
 *
 * <p>Whoever answers yes owns the ending. There is no loop counter and no
 * backstop here — an instruction that always answers yes is a train that never
 * reaches its next entry.
 *
 * <p>Lives here rather than beside either of the two instructions that answer
 * it. It is about Create's schedule runtime and nothing else, and putting it
 * with one of them would have the other's package depend on a subject it has no
 * business knowing — freight on routes, or routes on freight.
 */
public interface Repeats {

    boolean again();

    /**
     * Drops whatever about this entry only makes sense while a particular
     * train is running it. Called when a schedule is taken back out of a
     * train — deliberately not when the train itself is saved, where that
     * state is exactly what should be remembered.
     *
     * @param entry the entry this instruction is on, since some of what is
     *              dropped — a follower's borrowed wait conditions — lives on
     *              the entry rather than the instruction
     */
    void clearTransient(ScheduleEntry entry);

}
