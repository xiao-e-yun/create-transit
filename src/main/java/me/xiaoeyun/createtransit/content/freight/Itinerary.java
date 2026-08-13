package me.xiaoeyun.createtransit.content.freight;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DeliverPackagesInstruction;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.content.trains.station.GlobalPackagePort;
import com.simibubi.create.content.trains.station.GlobalStation;

import me.xiaoeyun.createtransit.content.route.Route;
import me.xiaoeyun.createtransit.content.route.RouteReference;
import me.xiaoeyun.createtransit.content.route.RouteStore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Where a schedule says a train is going, and therefore what it may pick up.
 *
 * <p>One rule: <em>load only what you can unload.</em> A train that takes a
 * package for a gate it never calls at has not moved it, it has hidden it — the
 * package rides in a slot that nothing will ever empty, and enough of them fill
 * the train and stop it working. Create lets this happen because a station hands
 * over everything it holds to whoever stops there, and until now so did we.
 *
 * <p>Read from the schedule rather than typed into a filter. The stops are
 * already written down; asking the player to list them a second time is asking
 * them to keep two copies in step, and the second copy is the one that goes
 * stale after the itinerary is edited. Same reason the round's dispatcher reads
 * a train's claim off {@code navigation.destination} instead of keeping a table.
 *
 * <p>Only transit trains are asked. An ordinary train picking up freight it
 * cannot deliver is Create's own behaviour, and one this mod has no business
 * changing under saves that were built on it.
 */
public final class Itinerary {

    private Itinerary() {}

    /**
     * Whether this train's schedule can put that package down somewhere.
     *
     * <p>An entry that delivers wherever the freight needs to go answers yes for
     * the whole schedule — {@code package_delivery} and our own round both do,
     * and the round is why a transit train using it is unrestricted. Create's
     * {@code package_retrieval} deliberately does not: it goes anywhere to
     * <em>collect</em>, which leaves the collected package no more deliverable
     * than before.
     *
     * <p>A schedule that names nowhere at all answers yes too. It has said
     * nothing about where it goes, and refusing everything on the strength of
     * silence would strand a train rather than a package.
     */
    public static boolean reaches(Train train, ItemStack stack) {
        Schedule schedule = train.runtime.schedule;
        if (schedule == null)
            return true;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        RouteStore store = server == null ? null : RouteStore.get(server);

        List<Pattern> named = new ArrayList<>();
        if (anywhere(schedule.entries, named, store))
            return true;
        if (named.isEmpty())
            return true;

        // ponytail: rebuilt for every package of every transfer. A world of this
        // size is a dozen stations, so it is a scan of nothing; cache it against
        // the schedule if that ever stops being true.
        for (GlobalStation station : train.graph.getPoints(EdgePointType.STATION)) {
            if (named.stream()
                .noneMatch(pattern -> pattern.matcher(station.name)
                    .matches()))
                continue;
            for (GlobalPackagePort port : station.connectedPorts.values())
                if (PackageItem.matchAddress(stack, port.address))
                    return true;
        }
        return false;
    }

    /**
     * Collects the station patterns an entry list names, and answers whether one
     * of them goes wherever it likes.
     *
     * <p>References are expanded here rather than left as one opaque stop,
     * because a route is exactly a list of stops and a train following one is as
     * committed to them as if they had been typed out. Missing its route
     * contributes nothing, which is the same thing that happens when the train
     * reaches that entry.
     */
    private static boolean anywhere(List<ScheduleEntry> entries, List<Pattern> named, RouteStore store) {
        for (ScheduleEntry entry : entries) {
            ScheduleInstruction instruction = entry.instruction;

            if (instruction instanceof DeliverPackagesInstruction
                || instruction instanceof PackageRoundInstruction)
                return true;

            // Before the destination test: a follower is a DestinationInstruction
            // too, and its filter is a route's name rather than a station's.
            RouteReference reference = RouteReference.of(instruction);
            if (reference != null) {
                Route route = store == null ? null : store.get(reference.route());
                if (route != null && anywhere(
                    route.flatten(store::get, reference.reversed(), reference.skipFirst()), named, store))
                    return true;
                continue;
            }

            if (!(instruction instanceof DestinationInstruction destination))
                continue;
            try {
                named.add(Pattern.compile(destination.getFilterForRegex()));
            } catch (PatternSyntaxException malformed) {
                // Half-typed. Contributing nothing is right: it names no station
                // yet, and this runs while a train is being handed a package.
            }
        }
        return false;
    }

}
