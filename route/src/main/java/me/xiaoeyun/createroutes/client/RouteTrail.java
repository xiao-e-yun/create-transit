package me.xiaoeyun.createroutes.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import me.xiaoeyun.createroutes.network.CrPackets;
import me.xiaoeyun.createroutes.network.RouteEditPacket;
import me.xiaoeyun.createroutes.network.ScheduleReopenPacket;

/** How the editor got to the route it is on. */
public class RouteTrail {

    private static final Deque<UUID> TRAIL = new ArrayDeque<>();

    /** Whether the bottom of the trail is a schedule the player is holding. */
    private static boolean schedule;

    private RouteTrail() {}

    /** Remembers the route being left, before opening the one it follows. */
    public static void push(UUID route) {
        TRAIL.push(route);
    }

    /** The trip started at a train's schedule, which is what to go back to. */
    public static void fromSchedule() {
        TRAIL.clear();
        schedule = true;
    }

    /** Goes back one step, and says whether there was anywhere to go. */
    public static boolean leave() {
        UUID opener = TRAIL.poll();
        if (opener != null) {
            CrPackets.CHANNEL.sendToServer(new RouteEditPacket(opener));
            return true;
        }
        if (!schedule)
            return false;
        schedule = false;
        CrPackets.CHANNEL.sendToServer(new ScheduleReopenPacket());
        return true;
    }

    /** Whether there is anywhere to go back to at all. */
    public static boolean leadsBack() {
        return !TRAIL.isEmpty() || schedule;
    }

}
