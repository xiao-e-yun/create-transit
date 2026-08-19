package me.xiaoeyun.createtransit.content.dispatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.station.GlobalPackagePort;
import com.simibubi.create.content.trains.station.GlobalStation;

import me.xiaoeyun.createtransit.content.transit.TransitPackageItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * The network's brain, asked by every enrolled train's poll and by the loading gate.
 *
 * The only state it keeps is a claim ledger, and only between assignment and loading —
 * cargo aboard names its own destinations, mail in postboxes is the backlog, so a lost
 * ledger merely re-derives itself from the world on the next round of polls.
 */
public class TransitDispatch {

    /** A claim on one station's outgoing transit mail; expiry is the only failure handling an order needs. */
    private record Order(UUID graph, String station, long deadline) {}

    /** Generous because it covers the whole trip out plus loading; a stuck train forfeits, and the mail gets re-claimed. */
    private static final long ORDER_TIMEOUT = 20 * 60 * 5;

    private static final Map<UUID, Order> ORDERS = new HashMap<>();

    /**
     * The next move for an enrolled train: deliver what it carries, else collect what it
     * claimed, else go home. Null stands the train down for one engine cooldown.
     */
    @Nullable
    public static DiscoveredPath nextLeg(ScheduleRuntime runtime, Level level, String depot) {
        MinecraftServer server = level.getServer();
        if (server == null)
            return null;
        Train train = runtime.train;
        sweep(level);

        // Upstream's own bar: any conductor drives; whether a route needs both ends is the pathfinder's verdict.
        if (!train.hasForwardConductor() && !train.hasBackwardConductor()) {
            train.status.missingConductor();
            ORDERS.remove(train.id);
            return null;
        }

        GlobalStation here = train.getCurrentStation();
        Order order = ORDERS.get(train.id);

        if (order != null && here != null && here.name.equals(order.station)) {
            if (hasWaitingMail(server, here) && hasRoom(train))
                return null;
            ORDERS.remove(train.id);
            order = null;
        }

        List<ItemStack> aboard = transitAboard(train);
        if (!aboard.isEmpty()) {
            ArrayList<GlobalStation> unloadable = deliverable(train, aboard, here);
            if (!unloadable.isEmpty()) {
                DiscoveredPath path = train.navigation.findPathTo(unloadable, Double.MAX_VALUE);
                if (path != null)
                    return path;
                train.status.failedNavigation();
                return null;
            }
            // No port anywhere answers to this cargo yet; home beats standing here forever.
            train.status.failedPackageNoTarget(PackageItem.getAddress(aboard.get(0)));
        }

        if (order != null) {
            ArrayList<GlobalStation> claimed = stationsNamed(train, order.station);
            if (!claimed.isEmpty()) {
                DiscoveredPath path = train.navigation.findPathTo(claimed, Double.MAX_VALUE);
                if (path != null)
                    return path;
            }
            // Renamed, derailed graph or no route: forfeit, and the mail is claimable again.
            ORDERS.remove(train.id);
            train.status.failedNavigation();
            return null;
        }

        // A one-way train leaves claimable work to an idle double-header -- some deliveries only they can finish.
        boolean oneWay = !train.hasForwardConductor() || !train.hasBackwardConductor();
        if (aboard.isEmpty() && hasRoom(train) && !(oneWay && idleDoubleHeader(train))) {
            ArrayList<GlobalStation> backlog = new ArrayList<>();
            for (GlobalStation station : train.graph.getPoints(EdgePointType.STATION))
                if (!claimed(train.graph.id, station.name) && hasWaitingMail(server, station))
                    backlog.add(station);
            if (!backlog.isEmpty()) {
                if (here != null && backlog.contains(here)) {
                    ORDERS.put(train.id,
                        new Order(train.graph.id, here.name, level.getGameTime() + ORDER_TIMEOUT));
                    return null;
                }
                DiscoveredPath path = train.navigation.findPathTo(backlog, Double.MAX_VALUE);
                if (path != null) {
                    ORDERS.put(train.id, new Order(train.graph.id, path.destination.name,
                        level.getGameTime() + ORDER_TIMEOUT));
                    return path;
                }
            }
        }

        // Every station wearing the depot's name is home, which is a depot group for free.
        if (here != null && here.name.equals(depot))
            return null;
        ArrayList<GlobalStation> home = stationsNamed(train, depot);
        if (home.isEmpty()) {
            train.status.failedNavigationNoTarget(depot);
            return null;
        }
        // Same-named bays spread by skipping ones another train has eyes on: Create re-reserves its
        // nearestTrain slot every navigation tick, in the same server tick a path starts, so a whole
        // batch released together still picks distinct bays. All taken -> queue like before.
        ArrayList<GlobalStation> free = new ArrayList<>();
        for (GlobalStation bay : home) {
            Train eyeing = bay.getNearestTrain();
            if (eyeing == null || eyeing == train)
                free.add(bay);
        }
        DiscoveredPath path = train.navigation.findPathTo(free.isEmpty() ? home : free, Double.MAX_VALUE);
        if (path == null)
            train.status.failedNavigation();
        return path;
    }

    /**
     * Whether the mail transfer may load this stack: transit mail boards only the train
     * that claimed this station, and a dispatched train carries nothing else.
     */
    public static boolean mayLoad(Train train, GlobalStation station, ItemStack stack) {
        boolean transit = stack.getItem() instanceof TransitPackageItem;
        if (TransitTimetableInstruction.depotOf(train.runtime.getSchedule()) == null)
            return !transit;
        if (!transit)
            return false;
        Order order = ORDERS.get(train.id);
        return order != null && order.station.equals(station.name);
    }

    /**
     * A bay just opened: wake the most-stuck homebound train queued for a taken bay
     * of the same name. Navigation never re-plans on its own, so a train that settled
     * for the queue when everything was full would otherwise hold the yard's throat
     * forever; cancelling re-polls it, and selection then finds this bay free.
     * Disassembling a parked train frees its bay without a departure — that one waits
     * for the next real departure.
     */
    public static void bayFreed(GlobalStation bay, Train departed) {
        if (bay.getNearestTrain() != null)
            return;
        Train best = null;
        for (Train other : Create.RAILWAYS.trains.values()) {
            // Paused runtimes never reach the re-poll, so a cancel would strand them where they roll out.
            if (other == departed || other.graph != departed.graph || other.runtime.paused)
                continue;
            GlobalStation target = other.navigation.destination;
            if (target == null || target == bay || !bay.name.equals(target.name))
                continue;
            // Its own depot only, so a delivery leg to a same-named stop is never yanked mid-journey.
            if (!bay.name.equals(TransitTimetableInstruction.depotOf(other.runtime.getSchedule())))
                continue;
            Train holder = target.getNearestTrain();
            if (holder == null || holder == other)
                continue;
            if (best == null
                || other.navigation.ticksWaitingForSignal > best.navigation.ticksWaitingForSignal)
                best = other;
        }
        if (best != null)
            best.navigation.cancelNavigation();
    }

    /** Orders whose train is gone or whose time is up; checked lazily since the map is fleet-sized. */
    private static void sweep(Level level) {
        long now = level.getGameTime();
        for (var iterator = ORDERS.entrySet().iterator(); iterator.hasNext();) {
            Entry<UUID, Order> entry = iterator.next();
            if (entry.getValue().deadline < now || !Create.RAILWAYS.trains.containsKey(entry.getKey()))
                iterator.remove();
        }
    }

    /** An enrolled, unpaused, empty double-header with no order and not mid-journey — able to take the job now, not eventually. */
    private static boolean idleDoubleHeader(Train self) {
        for (Train other : Create.RAILWAYS.trains.values()) {
            if (other == self || other.graph != self.graph || ORDERS.containsKey(other.id)
                || other.runtime.paused)
                continue;
            if (other.navigation.destination != null)
                continue;
            if (!other.hasForwardConductor() || !other.hasBackwardConductor())
                continue;
            if (TransitTimetableInstruction.depotOf(other.runtime.getSchedule()) == null)
                continue;
            if (transitAboard(other).isEmpty())
                return true;
        }
        return false;
    }

    /** Per graph, since two networks may both have a "Factory" and neither claim should shadow the other. */
    private static boolean claimed(UUID graph, String station) {
        for (Order order : ORDERS.values())
            if (order.graph.equals(graph) && order.station.equals(station))
                return true;
        return false;
    }

    /** Whether any port here holds outgoing transit mail — Create's own test: addressed away from the port it sits in. */
    private static boolean hasWaitingMail(MinecraftServer server, GlobalStation station) {
        ServerLevel level = server.getLevel(station.blockEntityDimension);
        if (level == null)
            return false;
        for (Entry<BlockPos, GlobalPackagePort> entry : station.connectedPorts.entrySet()) {
            GlobalPackagePort port = entry.getValue();
            BlockPos pos = entry.getKey();

            // The buffer is where an unloaded postbox keeps its mail; a loaded one has the real inventory.
            IItemHandlerModifiable inventory = port.offlineBuffer;
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PostboxBlockEntity postbox)
                inventory = postbox.inventory;

            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.getItem() instanceof TransitPackageItem
                    && !PackageItem.matchAddress(stack, port.address))
                    return true;
            }
        }
        return false;
    }

    /** Stations with a port answering to anything aboard; the standing one excluded, since mail already unloads there. */
    private static ArrayList<GlobalStation> deliverable(Train train, List<ItemStack> aboard,
        @Nullable GlobalStation here) {
        ArrayList<GlobalStation> stations = new ArrayList<>();
        for (GlobalStation station : train.graph.getPoints(EdgePointType.STATION)) {
            if (station == here)
                continue;
            for (GlobalPackagePort port : station.connectedPorts.values())
                for (ItemStack carried : aboard)
                    if (PackageItem.matchAddress(carried, port.address)) {
                        stations.add(station);
                        break;
                    }
        }
        return stations;
    }

    private static ArrayList<GlobalStation> stationsNamed(Train train, String name) {
        ArrayList<GlobalStation> stations = new ArrayList<>();
        for (GlobalStation station : train.graph.getPoints(EdgePointType.STATION))
            if (station.name.equals(name))
                stations.add(station);
        return stations;
    }

    private static List<ItemStack> transitAboard(Train train) {
        List<ItemStack> packages = new ArrayList<>();
        for (Carriage carriage : train.carriages) {
            IItemHandlerModifiable inventory = carriage.storage.getAllItems();
            if (inventory == null)
                continue;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.getItem() instanceof TransitPackageItem)
                    packages.add(stack);
            }
        }
        return packages;
    }

    /** Whether anything more could be loaded; an empty slot, since no two packages ever stack. */
    private static boolean hasRoom(Train train) {
        for (Carriage carriage : train.carriages) {
            IItemHandlerModifiable inventory = carriage.storage.getAllItems();
            if (inventory == null)
                continue;
            for (int slot = 0; slot < inventory.getSlots(); slot++)
                if (inventory.getStackInSlot(slot)
                    .isEmpty())
                    return true;
        }
        return false;
    }

}
