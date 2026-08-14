package me.xiaoeyun.createtransit.registry;

import com.simibubi.create.content.trains.schedule.Schedule;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createtransit.content.freight.PackageRoundInstruction;
import net.createmod.catnip.data.Pair;

/** Adds our schedule entry to {@code Schedule.INSTRUCTION_TYPES}, Create's plain static list — nothing here is discovered automatically. */
public class CtSchedule {

    public static void register() {
        Schedule.INSTRUCTION_TYPES
            .add(Pair.of(CreateTransit.asResource("package_round"), PackageRoundInstruction::new));
    }

}
