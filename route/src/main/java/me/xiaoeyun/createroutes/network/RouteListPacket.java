package me.xiaoeyun.createroutes.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import me.xiaoeyun.createroutes.CreateRoutes;
import me.xiaoeyun.createroutes.content.route.ClientRoutes;
import me.xiaoeyun.createroutes.content.route.Route;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Which routes exist, what each is called, and where each one goes — filters rather than stops, so a player's own typed contents never leave the server. */
public record RouteListPacket(Map<UUID, Line> routes) implements CustomPacketPayload {

    /** What one route is, as far as any client needs to know. */
    public record Line(String name, List<String> filters, List<UUID> references) {}

    /** A filter arrives as the regex it is compiled from, so it outgrows the name it was written against. */
    private static final int MAX_FILTER_LENGTH = 256;

    public static final Type<RouteListPacket> TYPE = new Type<>(CreateRoutes.asResource("route_list"));

    // Written out rather than composed: the payload is a map of records holding
    // two lists, and the length caps below are the point of writing it by hand.
    public static final StreamCodec<FriendlyByteBuf, RouteListPacket> STREAM_CODEC =
        StreamCodec.of(RouteListPacket::write, RouteListPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buffer, RouteListPacket packet) {
        buffer.writeVarInt(packet.routes.size());
        for (Map.Entry<UUID, Line> route : packet.routes.entrySet()) {
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

    private static RouteListPacket read(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        Map<UUID, Line> routes = new LinkedHashMap<>(count);
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
        return new RouteListPacket(routes);
    }

    public static void handle(RouteListPacket packet, IPayloadContext context) {
        // A plain dist check replaces DistExecutor: the class only loads when
        // the branch actually runs, which is all the old dance guaranteed.
        if (FMLEnvironment.dist == Dist.CLIENT)
            ClientRoutes.accept(packet.routes);
    }

}
