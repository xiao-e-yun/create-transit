package me.xiaoeyun.createtransit.content.dispatch;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;

import me.xiaoeyun.createtransit.registry.CtItems;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific;

/**
 * Enrolls and retires trains at the conductor, mirroring Create's schedule interaction.
 *
 * Registered at HIGH priority: an enrolled train's empty-hand tap must return the
 * timetable before Create's handler turns it into a schedule item.
 */
public class TimetableConductorInteraction {

    // Spelled out, not built from a prefix, so scripts/lang_audit.py can see these keys.
    private static final String LANG_BIND_FIRST = "create_transit.timetable.bind_first";
    private static final String LANG_ENROLLED = "create_transit.timetable.enrolled";
    private static final String LANG_RETIRED = "create_transit.timetable.retired";

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

        ItemStack held = event.getItemStack();
        if (held.getItem() instanceof TransitTimetableItem) {
            enroll(event, player, train, held);
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND || !held.isEmpty())
            return;
        // Manual controls pause the runtime, and this tap is Create's only cure — stand aside for it.
        if (train.runtime.paused && !train.runtime.completed)
            return;
        if (enrolledByItem(train))
            retire(event, player, train);
    }

    /** Whether the schedule came from a timetable item, which is what makes handing one back not an item dupe. */
    private static boolean enrolledByItem(Train train) {
        var schedule = train.runtime.getSchedule();
        if (schedule == null)
            return false;
        for (ScheduleEntry entry : schedule.entries)
            if (entry.instruction instanceof TransitTimetableInstruction timetable)
                return timetable.getData()
                    .getBoolean("FromItem");
        return false;
    }

    private static void enroll(EntityInteractSpecific event, Player player, Train train, ItemStack held) {
        boolean onServer = !event.getLevel().isClientSide;
        String depot = TransitTimetableItem.depot(held);

        Component denial = null;
        if (depot.isEmpty())
            denial = Component.translatable(LANG_BIND_FIRST);
        else if (train.runtime.getSchedule() != null)
            denial = Component.translatable("create.schedule.remove_with_empty_hand");

        if (denial != null) {
            if (onServer) {
                AllSoundEvents.DENY.playOnServer(player.level(), player.blockPosition(), 1, 1);
                player.displayClientMessage(denial, true);
            }
            cancel(event);
            return;
        }

        if (onServer) {
            Schedule schedule = new Schedule();
            TransitTimetableInstruction instruction = new TransitTimetableInstruction();
            instruction.getData()
                .putString("Text", depot);
            instruction.getData()
                .putBoolean("FromItem", true);
            ScheduleEntry entry = new ScheduleEntry();
            entry.instruction = instruction;
            schedule.entries.add(entry);
            train.runtime.setSchedule(schedule, false);
            held.shrink(1);

            AllSoundEvents.CONFIRM.playOnServer(player.level(), player.blockPosition(), 1, 1);
            player.displayClientMessage(
                Component.translatable(LANG_ENROLLED, Component.literal(depot)), true);
        }
        cancel(event);
    }

    private static void retire(EntityInteractSpecific event, Player player, Train train) {
        if (!event.getLevel().isClientSide) {
            String depot = TransitTimetableInstruction.depotOf(train.runtime.getSchedule());
            train.runtime.discardSchedule();

            ItemStack timetable = CtItems.TRANSIT_TIMETABLE.asStack();
            if (depot != null && !depot.isEmpty())
                timetable.getOrCreateTag()
                    .putString("Station", depot);
            player.getInventory()
                .placeItemBackInInventory(timetable);

            AllSoundEvents.playItemPickup(player);
            player.displayClientMessage(Component.translatable(LANG_RETIRED), true);
        }
        cancel(event);
    }

    private static void cancel(EntityInteractSpecific event) {
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

}
