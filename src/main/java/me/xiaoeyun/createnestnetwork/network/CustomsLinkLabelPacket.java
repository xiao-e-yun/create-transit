package me.xiaoeyun.createnestnetwork.network;

import java.util.function.Supplier;

import me.xiaoeyun.createnestnetwork.content.customs.CustomsLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** Sent when the customs link's label screen is closed. */
public class CustomsLinkLabelPacket {

    /** Matches the client-side edit box limit; the wire must not trust it. */
    private static final int MAX_LABEL_LENGTH = 25;

    private final BlockPos pos;
    private final String label;

    public CustomsLinkLabelPacket(BlockPos pos, String label) {
        this.pos = pos;
        this.label = label;
    }

    public CustomsLinkLabelPacket(FriendlyByteBuf buffer) {
        pos = buffer.readBlockPos();
        label = buffer.readUtf(MAX_LABEL_LENGTH);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeUtf(label, MAX_LABEL_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get()
            .enqueueWork(() -> {
                ServerPlayer sender = context.get()
                    .getSender();
                if (sender == null)
                    return;
                Level level = sender.level();
                // Guard against a spoofed position pointing at unloaded or
                // out-of-reach chunks, then defer to the network's own
                // permissions so links cannot be relabelled by outsiders.
                if (!level.isLoaded(pos) || sender.distanceToSqr(Vec3.atCenterOf(pos)) > 64 * 64)
                    return;
                if (!(level.getBlockEntity(pos) instanceof CustomsLinkBlockEntity link))
                    return;
                if (!link.behaviour.mayInteract(sender))
                    return;
                link.setLabel(label);
            });
        context.get()
            .setPacketHandled(true);
    }

}
