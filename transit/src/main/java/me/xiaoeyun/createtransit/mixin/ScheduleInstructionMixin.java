package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;

import me.xiaoeyun.createtransit.content.dispatch.TransitTimetableInstruction;
import net.minecraft.nbt.CompoundTag;

/**
 * Deserializes the timetable instruction without registering it: registration's only other
 * effect is a slot in the schedule editor's list, where a surrendered-control entry is a
 * category error among player-written steps.
 */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = ScheduleInstruction.class, remap = false)
public abstract class ScheduleInstructionMixin {

    @Inject(method = "fromTag", at = @At("HEAD"), cancellable = true)
    private static void createTransit$readTimetable(CompoundTag tag,
        CallbackInfoReturnable<ScheduleInstruction> cir) {
        if ("create_transit:timetable".equals(tag.getString("Id")))
            cir.setReturnValue(TransitTimetableInstruction.read(tag));
    }

}
