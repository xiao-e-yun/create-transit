package me.xiaoeyun.createroutes.network;

import com.simibubi.create.content.trains.schedule.ScheduleItem;

import me.xiaoeyun.createroutes.CreateRoutes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Puts the player back on the schedule they were holding, by reopening whatever {@code ScheduleItem} is in their main hand; closes quietly if there is none. */
public record ScheduleReopenPacket() implements CustomPacketPayload {

    public static final Type<ScheduleReopenPacket> TYPE = new Type<>(CreateRoutes.asResource("schedule_reopen"));

    /** The message is its own content: asking is the whole payload. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ScheduleReopenPacket> STREAM_CODEC =
        StreamCodec.unit(new ScheduleReopenPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScheduleReopenPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer sender))
            return;

        ItemStack held = sender.getMainHandItem();
        if (held.getItem() instanceof ScheduleItem schedule)
            // Create's own ScheduleItem.use opens it exactly this way.
            sender.openMenu(schedule, buffer -> ItemStack.STREAM_CODEC.encode(buffer, held));
        else
            sender.closeContainer();
    }

}
