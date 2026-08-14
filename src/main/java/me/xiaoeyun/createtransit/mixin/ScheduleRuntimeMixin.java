package me.xiaoeyun.createtransit.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;

import me.xiaoeyun.createtransit.content.schedule.Repeats;
import net.minecraft.world.item.ItemStack;

/**
 * Two things Create's runtime has no room to ask.
 *
 * <p>Hands back a clean schedule when one is taken out of a train: a route
 * follower has to write two things onto its own entry while it runs — how far
 * into the route it has got, and the conditions of the stop it is currently
 * travelling to, the latter because {@code tickConditions} reads the conditions
 * of the entry it is on and nowhere else. Both belong to a train running a
 * route, not to the piece of paper describing one. {@code returnSchedule} writes
 * the whole schedule into the item, so without this the player would get their
 * route back wearing one arbitrary stop's wait condition — a field they never
 * set, that they can edit, and that the next stop would silently overwrite.
 * Saving the train is deliberately left alone: there the progress is exactly
 * what should be remembered.
 *
 * <p>And lets an entry say it is not finished. See {@link Repeats}.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = ScheduleRuntime.class, remap = false)
public class ScheduleRuntimeMixin {

    @Shadow
    public Schedule schedule;

    @Shadow
    public int currentEntry;

    @Inject(method = "returnSchedule", at = @At("HEAD"))
    private void createTransit$forgetProgress(CallbackInfoReturnable<ItemStack> cir) {
        if (schedule == null)
            return;
        for (ScheduleEntry entry : schedule.entries)
            if (entry.instruction instanceof Repeats repeats)
                repeats.clearTransient(entry);
    }

    /**
     * Keeps the entry when its instruction says it has more to do.
     *
     * <p>{@code tickConditions} advances the moment a stop's conditions are met,
     * and does it by writing the field directly in two places — the branch for
     * an instruction that never waits, and the branch where a column of
     * conditions has run out. Both mean the same thing, so both are asked the
     * same question and the answer is the same: an instruction standing for a
     * whole sweep is not done because one of its stations is.
     *
     * <p>Not cancelling the tick and not touching {@code state}, which is
     * already {@code PRE_TRANSIT} by the time this runs. Leaving the index alone
     * is the entire change: the next tick starts the same entry again, and the
     * instruction chooses its next station exactly as it chose the first.
     */
    @Redirect(method = "tickConditions",
        at = @At(value = "FIELD",
            target = "Lcom/simibubi/create/content/trains/schedule/ScheduleRuntime;currentEntry:I",
            opcode = Opcodes.PUTFIELD))
    private void createTransit$stayWhileUnfinished(ScheduleRuntime runtime, int next) {
        if (currentEntry < schedule.entries.size()
            && schedule.entries.get(currentEntry).instruction instanceof Repeats repeats
            && repeats.again())
            return;
        currentEntry = next;
    }

}
