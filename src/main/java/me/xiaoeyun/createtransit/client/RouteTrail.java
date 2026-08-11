package me.xiaoeyun.createtransit.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import me.xiaoeyun.createtransit.network.CtPackets;
import me.xiaoeyun.createtransit.network.RouteEditPacket;
import me.xiaoeyun.createtransit.network.ScheduleReopenPacket;

/**
 * How the editor got to the route it is on.
 *
 * <p>A route may follow another, and opening the one it follows replaces the
 * screen — so without this, coming back out of a nested route means going out to
 * the list of them and in again, having lost where you were.
 *
 * <p>Client side and nothing more. A menu is the server's to hand out, but which
 * route the player would like to see next is not a fact about the world; the
 * server is asked to open one either way, and it neither knows nor needs to know
 * that the asking came from inside another route.
 *
 * <p>Going out to the list of routes does not end the trip. Picking a different
 * route there replaces the one that was open rather than starting again, so
 * a train's schedule that led to A which led to B still leads to A when B is
 * swapped for C — the list is a way of changing where you are, not of forgetting
 * how you got there.
 */
public class RouteTrail {

    private static final Deque<UUID> TRAIL = new ArrayDeque<>();

    /**
     * Whether the bottom of the trail is a schedule the player is holding.
     *
     * <p>Not a route, so it cannot be an id — and there is only ever one of it,
     * because a train's schedule is where a trip starts and never somewhere it
     * passes through.
     */
    private static boolean schedule;

    private RouteTrail() {}

    /** Remembers the route being left, before opening the one it follows. */
    public static void push(UUID route) {
        TRAIL.push(route);
    }

    /**
     * The trip started at a train's schedule, which is what to go back to.
     *
     * <p>The one place the trail is reset, and it resets by being a beginning:
     * a schedule is only ever the bottom, so reaching one means whatever was
     * left of an earlier trip is not on the way back from this one.
     */
    public static void fromSchedule() {
        TRAIL.clear();
        schedule = true;
    }

    /**
     * Goes back one step, and says whether there was anywhere to go.
     *
     * <p>Both steps are the same ask — the server hands out a menu and the
     * screen that had one goes away when it arrives, saving what it held. False
     * means the top: what the caller does there is the caller's, because a
     * screen with a container closes it and a screen without one simply ends.
     */
    public static boolean leave() {
        UUID opener = TRAIL.poll();
        if (opener != null) {
            CtPackets.CHANNEL.sendToServer(new RouteEditPacket(opener));
            return true;
        }
        if (!schedule)
            return false;
        schedule = false;
        CtPackets.CHANNEL.sendToServer(new ScheduleReopenPacket());
        return true;
    }

    /** Whether there is anywhere to go back to at all. */
    public static boolean leadsBack() {
        return !TRAIL.isEmpty() || schedule;
    }

}
