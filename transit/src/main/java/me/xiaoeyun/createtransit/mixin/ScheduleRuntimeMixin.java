package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;

import me.xiaoeyun.createtransit.content.dispatch.TransitTimetableInstruction;
import me.xiaoeyun.createtransit.content.dispatch.TransitTimetableItem;
import net.minecraft.world.item.ItemStack;

/** Hands back the timetable instead of a schedule item, so every way Create removes a schedule also retires the train. */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = ScheduleRuntime.class, remap = false)
public abstract class ScheduleRuntimeMixin {

    @Shadow
    public Schedule schedule;

    @Shadow
    public boolean isAutoSchedule;

    @Shadow
    public abstract void discardSchedule();

    @Inject(method = "returnSchedule", at = @At("HEAD"), cancellable = true)
    private void createTransit$returnTimetable(CallbackInfoReturnable<ItemStack> cir) {
        // An auto schedule was never paid for with an item, so minting one would be a dupe.
        if (isAutoSchedule)
            return;

        String depot = TransitTimetableInstruction.depotOf(schedule);
        if (depot == null)
            return;

        discardSchedule();
        cir.setReturnValue(TransitTimetableItem.of(depot));
    }

}
