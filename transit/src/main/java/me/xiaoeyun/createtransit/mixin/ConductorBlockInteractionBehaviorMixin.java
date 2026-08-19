package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.api.behaviour.interaction.ConductorBlockInteractionBehavior;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.tterrag.registrate.util.entry.ItemEntry;

import me.xiaoeyun.createtransit.content.dispatch.TimetableConductorInteraction;
import me.xiaoeyun.createtransit.content.dispatch.TransitTimetableInstruction;
import me.xiaoeyun.createtransit.content.dispatch.TransitTimetableItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Lets a timetable enroll a train wherever a schedule can: it passes the item check that would
 * refuse it and the read that turns it into a schedule, and an unbound one is cancelled at that
 * same read. Everything after — the advancement, the sound, the shrink — is Create's.
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

        return TransitTimetableInstruction.schedule(TransitTimetableItem.depot(held));
    }

    /**
     * An unbound timetable still reads as a one-entry schedule, so without this the train would
     * enroll on a blank depot. Denied at the read: every gate has passed, the level is already
     * known to be the server's, and nothing is spent yet.
     */
    @Inject(method = "handlePlayerInteraction",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/schedule/ScheduleItem;"
                + "getSchedule(Lnet/minecraft/world/item/ItemStack;)"
                + "Lcom/simibubi/create/content/trains/schedule/Schedule;"),
        cancellable = true)
    private void createTransit$denyUnboundTimetable(Player player, InteractionHand activeHand, BlockPos localPos,
        AbstractContraptionEntity contraptionEntity, CallbackInfoReturnable<Boolean> cir) {
        ItemStack held = player.getItemInHand(activeHand);
        if (!(held.getItem() instanceof TransitTimetableItem) || !TransitTimetableItem.depot(held)
            .isEmpty())
            return;

        TimetableConductorInteraction.denyUnbound(player);
        cir.setReturnValue(true);
    }

}
