package me.xiaoeyun.createtransit.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;

import me.xiaoeyun.createtransit.content.route.FollowRouteInstruction;

/**
 * Keeps a route follower's borrowed conditions out of the schedule editor.
 *
 * <p>A follower has to write the current stop's conditions onto its own entry,
 * because the runtime waits on the conditions of the entry it is sitting on and
 * nowhere else. Those conditions belong to the route, not to this piece of
 * paper: the player never set them, editing them achieves nothing, and the next
 * stop overwrites whatever they typed. An editable control that silently
 * reverts is worse than no control.
 *
 * <p>{@code supportsConditions} is the single fact the whole card is laid out
 * from — its height, its header texture, which strip icon it wears, whether the
 * wait row is drawn, and the height the click test walks past. Answering it
 * once, here, turns a follower into an action card exactly like a throttle
 * change, with every one of those consequences following on their own.
 *
 * <p>Only the screen is redirected. The instruction still reports true to
 * everything else, because there the answer is genuinely yes — it does wait,
 * and its conditions must still be written to disk.
 *
 * <p>There was a second injection here, replacing Create's station autocomplete
 * with a list of routes. It never ran: those suggestions are attached to an
 * {@code EditBox} found among the editor's sub-widgets, and a follower builds a
 * picker and a button instead — the route is chosen, not typed. Which routes may
 * be chosen is decided where that picker is built.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = ScheduleScreen.class, remap = false)
public class ScheduleScreenMixin {

    @Redirect(method = { "renderSchedule", "renderScheduleEntry", "action" },
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/schedule/destination/ScheduleInstruction;supportsConditions()Z"))
    private boolean createTransit$hideBorrowedConditions(ScheduleInstruction instruction) {
        return instruction.supportsConditions() && !(instruction instanceof FollowRouteInstruction);
    }

}
