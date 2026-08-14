package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.schedule.Schedule;

import me.xiaoeyun.createtransit.content.freight.TransitTrain;
import net.minecraft.nbt.CompoundTag;

/** Gives a schedule the one bit that says which post it runs; read/written by hand since {@code Schedule.write}/{@code fromTag} only round-trip three named keys. */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = Schedule.class, remap = false)
public class ScheduleMixin implements TransitTrain {

    /** Ours, and named so that nothing else's key can ever be mistaken for it. */
    @Unique
    private static final String CREATE_TRANSIT$KEY = "CreateTransitTrain";

    @Unique
    private boolean createTransit$transitTrain;

    @Override
    public boolean createTransit$isTransitTrain() {
        return createTransit$transitTrain;
    }

    @Override
    public void createTransit$setTransitTrain(boolean transit) {
        createTransit$transitTrain = transit;
    }

    // Only when set, so an ordinary schedule's NBT is byte for byte what Create would have written.
    @Inject(method = "write", at = @At("RETURN"))
    private void createTransit$writeTransitTrain(CallbackInfoReturnable<CompoundTag> cir) {
        if (createTransit$transitTrain)
            cir.getReturnValue()
                .putBoolean(CREATE_TRANSIT$KEY, true);
    }

    @Inject(method = "fromTag", at = @At("RETURN"))
    private static void createTransit$readTransitTrain(CompoundTag tag,
        CallbackInfoReturnable<Schedule> cir) {
        ((TransitTrain) cir.getReturnValue()).createTransit$setTransitTrain(tag.getBoolean(CREATE_TRANSIT$KEY));
    }

}
