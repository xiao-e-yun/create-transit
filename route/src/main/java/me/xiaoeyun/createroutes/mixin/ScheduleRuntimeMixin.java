package me.xiaoeyun.createroutes.mixin;

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

import me.xiaoeyun.createroutes.schedule.Repeats;
import net.minecraft.world.item.ItemStack;

/**
 * Two things Create's runtime has no room to ask: clears a route follower's transient per-train state when a
 * schedule is taken back out of a train, and lets an entry say it isn't finished — see {@link Repeats}.
 */
// remap = false: a Create class, so its names are never obfuscated.
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

    /** Keeps the entry when its instruction says it has more to do; redirects the field write since {@code tickConditions} advances currentEntry via two separate PUTFIELDs, not a method call. */
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
