package me.xiaoeyun.createtransit.network;

import java.util.function.Supplier;

import me.xiaoeyun.createtransit.content.transit.TransitLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Sent when the transit link's label screen is closed. */
public class TransitLinkLabelPacket {

    /** Matches the client-side edit box limit; the wire must not trust it. */
    private static final int MAX_LABEL_LENGTH = 25;

    private final BlockPos pos;
    private final String label;

    public TransitLinkLabelPacket(BlockPos pos, String label) {
        this.pos = pos;
        this.label = label;
    }

    public TransitLinkLabelPacket(FriendlyByteBuf buffer) {
        pos = buffer.readBlockPos();
        label = buffer.readUtf(MAX_LABEL_LENGTH);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(label, MAX_LABEL_LENGTH);
    }

    /** Registered with {@code consumerMainThread}, which does the enqueueWork and the setPacketHandled itself. */
    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get()
            .getSender();
        if (sender == null)
            return;
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
        link.setLabel(label);
    }

}
