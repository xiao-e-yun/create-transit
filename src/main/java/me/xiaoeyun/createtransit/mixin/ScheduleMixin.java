package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.schedule.Schedule;

import me.xiaoeyun.createtransit.content.freight.TransitTrain;
import net.minecraft.nbt.CompoundTag;

/**
 * Gives a schedule the one bit that says which post it runs.
 *
 * <p>A field rather than a tag written by some instruction, because the flag is
 * a property of the whole schedule and has to be readable when no entry is
 * being run — {@code runMailTransfer} asks about a train that is merely stopped
 * somewhere.
 *
 * <p>Read and written by hand because {@code Schedule.write} builds a fresh
 * {@code CompoundTag} and {@code fromTag} reads three named keys: anything else
 * in the tag is dropped on the way through. So the round trip has to be
 * extended at both ends or the flag would survive exactly as long as the
 * screen that set it.
 *
 * <p>Absent means off, which is what an older save and every schedule written
 * before this should mean.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
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

    // Only when set, so an ordinary schedule's NBT is byte for byte what Create
    // would have written and nothing downstream sees a new key it must ignore.
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
