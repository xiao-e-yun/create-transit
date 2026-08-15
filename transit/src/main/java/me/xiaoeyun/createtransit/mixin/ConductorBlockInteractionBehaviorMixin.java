package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.tterrag.registrate.util.entry.ItemEntry;

import me.xiaoeyun.createtransit.content.dispatch.TransitTimetableInstruction;
import me.xiaoeyun.createtransit.content.dispatch.TransitTimetableItem;
import net.minecraft.world.item.ItemStack;

/**
 * Lets a timetable enroll a train wherever a schedule can, by passing Create's own
 * two gates: the item check that would refuse it, and the read that turns it into a
 * schedule. Everything after — the advancement, the sound, the shrink — is Create's.
 */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = ConductorBlockInteractionBehavior.class, remap = false)
public abstract class ConductorBlockInteractionBehaviorMixin {

    @WrapOperation(method = "handlePlayerInteraction",
        at = @At(value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/ItemEntry;isIn(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean createTransit$timetableCountsToo(ItemEntry<?> queried, ItemStack held,
        Operation<Boolean> original) {
        return original.call(queried, held) || held.getItem() instanceof TransitTimetableItem;
    }

    @WrapOperation(method = "handlePlayerInteraction",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/schedule/ScheduleItem;"
                + "getSchedule(Lnet/minecraft/world/item/ItemStack;)"
                + "Lcom/simibubi/create/content/trains/schedule/Schedule;"))
    private Schedule createTransit$readTimetable(ItemStack held, Operation<Schedule> original) {
        if (!(held.getItem() instanceof TransitTimetableItem))
            return original.call(held);

        String depot = TransitTimetableItem.depot(held);
        // Empty rather than null for an unbound one: Create's own no_stops denial says so and spends nothing.
        return depot.isEmpty() ? new Schedule() : TransitTimetableInstruction.schedule(depot);
    }

}
