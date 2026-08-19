package me.xiaoeyun.createtransit.content.dispatch;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;

/** Enrolls a train by handing the timetable to its conductor; retiring is Create's own empty-hand tap. */
public class TimetableConductorInteraction {

    // Spelled out, not built from a prefix, so scripts/lang_audit.py can see these keys.
    private static final String LANG_BIND_FIRST = "create_transit.timetable.bind_first";
    private static final String LANG_ENROLLED = "create_transit.timetable.enrolled";

    public static void interactWithConductor(EntityInteractSpecific event) {
        Entity entity = event.getTarget();
        Player player = event.getEntity();
        if (player == null || entity == null || player.isSpectator())
            return;
        if (!(entity.getRootVehicle() instanceof CarriageContraptionEntity cce))
            return;

        Contraption contraption = cce.getContraption();
        if (!(contraption instanceof CarriageContraption cc))
            return;
        Train train = cce.getCarriage().train;
        if (train == null)
            return;

        ItemStack held = event.getItemStack();
        if (!(held.getItem() instanceof TransitTimetableItem))
            return;

        // Only a conductor's seat answers, the same gate Create's own schedule keeps.
        Integer seatIndex = contraption.getSeatMapping()
            .get(entity.getUUID());
        if (seatIndex == null)
            return;
        BlockPos seatPos = contraption.getSeats()
            .get(seatIndex);
        Couple<Boolean> directions = cc.conductorSeats.get(seatPos);
        if (directions == null)
            return;

        enroll(player, train, held, event.getLevel().isClientSide);
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** Applies the held timetable to the train, or says why it cannot; the gesture is spent either way. */
    public static void enroll(Player player, Train train, ItemStack held, boolean clientSide) {
        if (clientSide)
            return;

        String depot = TransitTimetableItem.depot(held);
        if (depot.isEmpty()) {
            denyUnbound(player);
            return;
        }
        if (train.runtime.getSchedule() != null) {
            deny(player, Component.translatable("create.schedule.remove_with_empty_hand"));
            return;
        }

        train.runtime.setSchedule(TransitTimetableInstruction.schedule(depot), false);
        held.shrink(1);

        AllSoundEvents.CONFIRM.playOnServer(player.level(), player.blockPosition(), 1, 1);
        player.displayClientMessage(
            Component.translatable(LANG_ENROLLED, Component.literal(depot)), true);
    }

    /** Shared so the blaze-burner conductor refuses an unbound timetable in the same words as the seated one. */
    public static void denyUnbound(Player player) {
        deny(player, Component.translatable(LANG_BIND_FIRST));
    }

    private static void deny(Player player, Component reason) {
        AllSoundEvents.DENY.playOnServer(player.level(), player.blockPosition(), 1, 1);
        player.displayClientMessage(reason, true);
    }

}
