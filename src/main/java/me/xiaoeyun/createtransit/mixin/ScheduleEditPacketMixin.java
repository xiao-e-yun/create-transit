package me.xiaoeyun.createtransit.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEditPacket;

import me.xiaoeyun.createtransit.content.route.RouteEditSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * Sends a route's edits to the route instead of to whatever the player holds.
 *
 * <p>A closing schedule screen always sends this packet, and Create's handler
 * writes its payload into the sender's main hand:
 *
 * <pre>
 * ItemStack mainHandItem = sender.getMainHandItem();
 * if (!AllItems.SCHEDULE.isIn(mainHandItem))
 *     return;
 * tag.put("Schedule", schedule.write());
 * </pre>
 *
 * <p>For a route session that hand holds something unrelated — quite possibly
 * the player's own schedule, which would be silently overwritten with the route.
 * So the session is claimed and the handler is stopped before it runs, rather
 * than allowed to write and corrected afterwards: there is no arrangement of the
 * player's inventory that can make a cancelled handler damage anything.
 *
 * <p>The save is enqueued rather than done here because {@code handle} runs on
 * the network thread; world data is only safe to touch on the server thread,
 * which is what {@code enqueueWork} is for.
 */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = ScheduleEditPacket.class, remap = false)
public class ScheduleEditPacketMixin {

    @Shadow
    private Schedule schedule;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void createTransit$divertToRoute(NetworkEvent.Context context,
        CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer sender = context.getSender();
        if (sender == null)
            return;

        UUID route = RouteEditSession.claim(sender);
        if (route == null)
            return;

        Schedule edited = schedule;
        context.enqueueWork(() -> RouteEditSession.save(sender, route, edited));
        cir.setReturnValue(true);
    }

}
