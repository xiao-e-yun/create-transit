package me.xiaoeyun.createtransit.content.freight;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;

import me.xiaoeyun.createtransit.content.transit.TransitPackageItem;
import net.minecraft.world.item.ItemStack;

/**
 * Whether a schedule runs this mod's post or Create's; off loads Create's packages and never a
 * {@link TransitPackageItem}, on loads {@link TransitPackageItem} and nothing else. Implemented by a mixin
 * onto {@link Schedule}, since the flag belongs to the schedule rather than the train.
 */
public interface TransitTrain {

    boolean createTransit$isTransitTrain();

    void createTransit$setTransitTrain(boolean transit);

    /** Whether a schedule, which may be absent or foreign, says it is one. */
    static boolean of(Schedule schedule) {
        return schedule instanceof TransitTrain flag && flag.createTransit$isTransitTrain();
    }

    /** Whether this train would take that box: the lane must match, and a transit train also needs {@link Itinerary#reaches}. */
    static boolean carries(Train train, ItemStack stack) {
        boolean lane = of(train.runtime.schedule);
        if (lane != stack.getItem() instanceof TransitPackageItem)
            return false;
        return !lane || Itinerary.reaches(train, stack);
    }

}
