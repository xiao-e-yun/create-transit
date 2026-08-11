package me.xiaoeyun.createtransit.content.route;

import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;

/**
 * One route standing in for a run of stops somewhere else.
 *
 * <p>Kept apart from the instruction that carries it so {@link Route} can walk
 * a nested structure without touching schedule internals, and so the two flags
 * that describe a join live next to each other with names that say what they
 * join.
 *
 * @param route     the referenced route's id, never its name — a name is a
 *                  label and may change under a train already following it
 * @param reversed  run its stops back to front
 * @param skipFirst drop its leading stop, because whoever placed this reference
 *                  already stopped at that station
 */
public record RouteReference(UUID route, boolean reversed, boolean skipFirst) {

    /** The reference an instruction carries, or null when it is an ordinary stop. */
    @Nullable
    public static RouteReference of(@Nullable ScheduleInstruction instruction) {
        if (!(instruction instanceof FollowRouteInstruction follow))
            return null;
        return follow.reference();
    }

}
