package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;

import me.xiaoeyun.createtransit.content.dispatch.TransitTimetableInstruction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Deserializes the timetable instruction, which is deliberately unregistered — {@code fromTag}
 * would otherwise log a warning and hand back a {@code DestinationInstruction}.
 */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = ScheduleInstruction.class, remap = false)
public abstract class ScheduleInstructionMixin {

    @Inject(method = "fromTag(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)Lcom/simibubi/create/content/trains/schedule/destination/ScheduleInstruction;",
        at = @At("HEAD"), cancellable = true)
    private static void createTransit$readTimetable(HolderLookup.Provider registries, CompoundTag tag,
        CallbackInfoReturnable<ScheduleInstruction> cir) {
        if (TransitTimetableInstruction.ID_TAG.equals(tag.getString("Id")))
            cir.setReturnValue(TransitTimetableInstruction.read(registries, tag));
    }

}
