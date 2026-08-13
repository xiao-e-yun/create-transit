package me.xiaoeyun.createtransit.registry;

import com.simibubi.create.content.trains.schedule.Schedule;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createtransit.content.freight.PackageRoundInstruction;
import me.xiaoeyun.createtransit.content.route.FollowRouteInstruction;
import net.createmod.catnip.data.Pair;

/**
 * Adds our schedule entries to Create's own tables.
 *
 * <p>{@code Schedule.INSTRUCTION_TYPES} is a plain static list that Create
 * fills in a static block; nothing is discovered, so an addon has to say so
 * itself. The same list drives both the editor's dropdown and the lookup that
 * reads an entry back out of NBT, which is why registration has to happen
 * before any world loads.
 *
 * <p>An unregistered id is not fatal on the way back in — Create logs it and
 * hands back a plain destination — so a world that loses this mod keeps
 * readable schedules instead of broken ones.
 */
public class CtSchedule {

    public static void register() {
        Schedule.INSTRUCTION_TYPES
            .add(Pair.of(CreateTransit.asResource("follow_route"), FollowRouteInstruction::new));
        Schedule.INSTRUCTION_TYPES
            .add(Pair.of(CreateTransit.asResource("package_round"), PackageRoundInstruction::new));
    }

}
