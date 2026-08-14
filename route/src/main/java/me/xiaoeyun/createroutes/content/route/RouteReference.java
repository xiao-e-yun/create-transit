package me.xiaoeyun.createroutes.content.route;

import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;

/**
 * One route standing in for a run of stops somewhere else, kept apart from the instruction that carries it
 * so {@link Route} can walk a nested structure without touching schedule internals.
 *
 * @param route        the referenced route's id, never its name — a name may change under a train already following it
 * @param reversed     run its stops back to front
 * @param skipTerminus drop its final stop after reversal, for a following leg that starts there
 */
public record RouteReference(UUID route, boolean reversed, boolean skipTerminus) {

    /** The reference an instruction carries, or null when it is an ordinary stop. */
    @Nullable
    public static RouteReference of(@Nullable ScheduleInstruction instruction) {
        if (!(instruction instanceof FollowRouteInstruction follow))
            return null;
        return follow.reference();
    }

}
