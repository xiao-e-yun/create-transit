package me.xiaoeyun.createroutes.registry;

import com.simibubi.create.content.trains.schedule.Schedule;

import me.xiaoeyun.createroutes.CreateRoutes;
import me.xiaoeyun.createroutes.content.route.FollowRouteInstruction;
import net.createmod.catnip.data.Pair;

/** Adds this mod's schedule entry to {@code Schedule.INSTRUCTION_TYPES}, Create's plain static list — nothing here is discovered automatically. */
public class CrSchedule {

    public static void register() {
        Schedule.INSTRUCTION_TYPES
            .add(Pair.of(CreateRoutes.asResource("follow_route"), FollowRouteInstruction::new));
    }

}
