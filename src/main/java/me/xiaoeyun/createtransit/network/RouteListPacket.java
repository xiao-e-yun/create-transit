package me.xiaoeyun.createtransit.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import me.xiaoeyun.createtransit.content.route.ClientRoutes;
import me.xiaoeyun.createtransit.content.route.Route;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Which routes exist, what each is called, and where each one goes.
 *
 * <p>The names are what a client turns a typed reference into and back again.
 * The rest is what the map needs to draw a route it is not editing: a stop that
 * follows another route is a run of stations somewhere else, and without them
 * that leg of the line is a gap. Cycle checking falls out of the same data —
 * the server refuses one on save, and this is what lets the editor stop
 * offering the route that would cause it.
 *
 * <p>Filters rather than stops: a name and its wildcards is all either use
 * needs, so an instruction's own contents — items, conditions, everything a
 * player actually typed — never leave the server. That also keeps this small
 * enough to resend whole whenever any route changes, rather than tracking
 * deltas.
 */
public class RouteListPacket {

    /** What one route is, as far as any client needs to know. */
    public record Line(String name, List<String> filters, List<UUID> references) {}

    /**
     * A station filter, already in the form a pattern is compiled from. The
     * conversion is the instruction's own and the server has the instruction.
     */
    private static final int MAX_FILTER_LENGTH = 256;

    private final Map<UUID, Line> routes;

    public RouteListPacket(Map<UUID, Line> routes) {
        this.routes = routes;
    }

    public RouteListPacket(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        routes = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buffer.readUUID();
            String name = buffer.readUtf(Route.MAX_NAME_LENGTH);

            List<String> filters = new ArrayList<>();
            for (int f = buffer.readVarInt(); f > 0; f--)
                filters.add(buffer.readUtf(MAX_FILTER_LENGTH));

            List<UUID> references = new ArrayList<>();
            for (int r = buffer.readVarInt(); r > 0; r--)
                references.add(buffer.readUUID());

            routes.put(id, new Line(name, filters, references));
        }
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(routes.size());
        for (Map.Entry<UUID, Line> route : routes.entrySet()) {
            buffer.writeUUID(route.getKey());
            Line line = route.getValue();
            buffer.writeUtf(line.name(), Route.MAX_NAME_LENGTH);

            buffer.writeVarInt(line.filters()
                .size());
            for (String filter : line.filters())
                buffer.writeUtf(filter, MAX_FILTER_LENGTH);

            buffer.writeVarInt(line.references()
                .size());
            for (UUID reference : line.references())
                buffer.writeUUID(reference);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRoutes.accept(routes));
    }

}
