package me.xiaoeyun.createtransit.network;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createtransit.content.transit.TransitLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent when the transit link's label screen is closed. */
public record TransitLinkLabelPacket(BlockPos pos, String label) implements CustomPacketPayload {

    /** Matches the client-side edit box limit; the wire must not trust it. */
    private static final int MAX_LABEL_LENGTH = 25;

    public static final Type<TransitLinkLabelPacket> TYPE =
        new Type<>(CreateTransit.asResource("transit_link_label"));

    public static final StreamCodec<FriendlyByteBuf, TransitLinkLabelPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, TransitLinkLabelPacket::pos,
            ByteBufCodecs.stringUtf8(MAX_LABEL_LENGTH), TransitLinkLabelPacket::label,
            TransitLinkLabelPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Payload handlers run on the main thread unless registered otherwise. */
    public static void handle(TransitLinkLabelPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender))
            return;
        BlockPos pos = packet.pos;
        Level level = sender.level();
        // Guard against a spoofed position pointing at unloaded or out-of-reach
        // chunks, then defer to the network's own permissions so links cannot
        // be relabelled by outsiders.
        if (!level.isLoaded(pos) || sender.distanceToSqr(Vec3.atCenterOf(pos)) > 64 * 64)
            return;
        if (!(level.getBlockEntity(pos) instanceof TransitLinkBlockEntity link))
            return;
        if (!link.behaviour.mayInteract(sender))
            return;
        link.setLabel(packet.label);
    }

}
