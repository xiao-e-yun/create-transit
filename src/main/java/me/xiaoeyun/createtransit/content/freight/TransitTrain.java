package me.xiaoeyun.createtransit.content.freight;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;

import me.xiaoeyun.createtransit.content.transit.TransitPackageItem;
import net.minecraft.world.item.ItemStack;

/**
 * Whether a schedule runs this mod's post or Create's.
 *
 * <p>Two postal systems that share the same rails and never share a package.
 * A schedule is one or the other, and what it will load says which:
 *
 * <table>
 * <tr><th>Transit train</th><th>Loads</th></tr>
 * <tr><td>off</td><td>Create's packages, and never a {@link TransitPackageItem}</td></tr>
 * <tr><td>on</td><td>{@link TransitPackageItem} and nothing else</td></tr>
 * </table>
 *
 * <p>Both halves of that only ever mention our own item, which is what makes
 * this safe to impose on every train in the world: a transit box did not exist
 * before this mod, so no schedule anyone already built loses a package it used
 * to carry. The alternative — deciding by whether a schedule looks like it
 * means to handle mail — would have quietly stopped every plain loop that has
 * been delivering post since Create 6.
 *
 * <p>Unloading is deliberately not asked. A train that changes lanes, or that
 * was loaded before this existed, has to be able to put down what it is
 * carrying; a filter on the way out would strand it for good.
 *
 * <p>Implemented by a mixin onto {@link Schedule}, because the flag belongs to
 * the piece of paper rather than the train — it travels with the schedule into
 * a train and back out into the player's hand, and two trains running copies of
 * the same schedule should not have to be told twice.
 */
public interface TransitTrain {

    boolean createTransit$isTransitTrain();

    void createTransit$setTransitTrain(boolean transit);

    /** Whether a schedule, which may be absent or foreign, says it is one. */
    static boolean of(Schedule schedule) {
        return schedule instanceof TransitTrain flag && flag.createTransit$isTransitTrain();
    }

    /**
     * Whether this train would take that box.
     *
     * <p>Two questions, and the first is an equality because that is what the
     * lane rule is, in both directions at once. The second is only asked of a
     * transit train: {@link Itinerary#reaches} — load only what you can unload.
     *
     * <p>Everywhere it is asked has to give the same answer or a train sets out
     * for freight it will be refused on arrival, so it is asked here and nowhere
     * else: the station handing a package over, our own round choosing where to
     * go, and Create's retrieval choosing where to go.
     */
    static boolean carries(Train train, ItemStack stack) {
        boolean lane = of(train.runtime.schedule);
        if (lane != stack.getItem() instanceof TransitPackageItem)
            return false;
        return !lane || Itinerary.reaches(train, stack);
    }

}
