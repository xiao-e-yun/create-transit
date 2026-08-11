package me.xiaoeyun.createtransit.mixin;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;

import me.xiaoeyun.createtransit.content.route.FollowRouteInstruction;
import net.minecraft.world.item.ItemStack;

/**
 * Hands back a clean schedule when one is taken out of a train.
 *
 * <p>A route follower has to write two things onto its own entry while it runs:
 * how far into the route it has got, and the conditions of the stop it is
 * currently travelling to — the latter because {@code tickConditions} reads the
 * conditions of the entry it is on and nowhere else. Both belong to a train
 * running a route, not to the piece of paper describing one.
 *
 * <p>{@code returnSchedule} writes the whole schedule into the item, so without
 * this the player would get their route back wearing one arbitrary stop's wait
 * condition — a field they never set, that they can edit, and that the next
 * stop would silently overwrite. Saving the train is deliberately left alone:
 * there the progress is exactly what should be remembered.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = com.simibubi.create.content.trains.schedule.ScheduleRuntime.class, remap = false)
public class ScheduleRuntimeMixin {

    @Shadow
    public Schedule schedule;

    @Inject(method = "returnSchedule", at = @At("HEAD"))
    private void createTransit$forgetRouteProgress(CallbackInfoReturnable<ItemStack> cir) {
        if (schedule == null)
            return;
        for (ScheduleEntry entry : schedule.entries) {
            if (!(entry.instruction instanceof FollowRouteInstruction follow))
                continue;
            follow.clearProgress();
            // Empty is a follower's true resting state: it owns no conditions,
            // it borrows whichever stop's it is heading for.
            entry.conditions = new ArrayList<>();
        }
    }

}
