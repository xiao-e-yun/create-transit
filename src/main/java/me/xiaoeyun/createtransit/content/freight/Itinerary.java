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

/** Where a schedule says a train is going, and therefore what it may pick up: load only what you can unload; only transit trains are filtered. */
public final class Itinerary {

    private Itinerary() {}

    /** Whether this train's schedule can put that package down somewhere; naming nowhere, or an entry that delivers unconditionally, answers yes. */
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

    /** Collects the station patterns an entry list names, and whether any entry goes wherever it likes; route references are expanded inline. */
    private static boolean anywhere(List<ScheduleEntry> entries, List<Pattern> named, RouteStore store) {
        for (ScheduleEntry entry : entries) {
            ScheduleInstruction instruction = entry.instruction;

            if (instruction instanceof DeliverPackagesInstruction
                || instruction instanceof PackageRoundInstruction)
                return true;

            // Checked before the destination test: a follower is a DestinationInstruction too, and its filter is a route's name, not a station's.
            RouteReference reference = RouteReference.of(instruction);
            if (reference != null) {
                Route route = store == null ? null : store.get(reference.route());
                if (route != null && anywhere(
                    route.flatten(store::get, reference.reversed(), reference.skipTerminus()), named, store))
                    return true;
                continue;
            }

            if (!(instruction instanceof DestinationInstruction destination))
                continue;
            try {
                named.add(Pattern.compile(destination.getFilterForRegex()));
            } catch (PatternSyntaxException malformed) {
                // Half-typed filter; contributes nothing rather than throwing while a train is being handed a package.
            }
        }
        return false;
    }

}
