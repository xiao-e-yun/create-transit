package me.xiaoeyun.createtransit.content.dispatch;

import java.util.List;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Donates a train to the dispatcher: right-click a station to name its parking bay, then hand this to a conductor. */
public class TransitTimetableItem extends Item {

    private static final String NBT_STATION = "Station";

    // Spelled out, not built from a prefix, so scripts/lang_audit.py can see these keys.
    private static final String LANG_BOUND = "create_transit.timetable.bound";
    private static final String LANG_BIND_HINT = "create_transit.timetable.bind_hint";

    public TransitTimetableItem(Properties properties) {
        super(properties);
    }

    /** First, so binding wins over the station's own screen; a bay is named by its station, which survives the station being rebuilt. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof StationBlockEntity blockEntity))
            return InteractionResult.PASS;
        GlobalStation station = blockEntity.getStation();
        if (station == null)
            return InteractionResult.PASS;

        Player player = context.getPlayer();
        // A bound timetable enrolls the waiting train instead; sneak to rebind anyway.
        Train train = station.getPresentTrain();
        if (player != null && train != null && !depot(stack).isEmpty() && !player.isShiftKeyDown()) {
            TimetableConductorInteraction.enroll(player, train, stack, level.isClientSide);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide && player != null) {
            stack.getOrCreateTag()
                .putString(NBT_STATION, station.name);
            player.displayClientMessage(
                Component.translatable(LANG_BOUND, Component.literal(station.name)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The bound bay, or blank while unbound. */
    public static String depot(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(NBT_STATION);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        String depot = depot(stack);
        if (depot.isEmpty())
            tooltip.add(Component.translatable(LANG_BIND_HINT)
                .withStyle(ChatFormatting.GRAY));
        else
            tooltip.add(Component.translatable(LANG_BOUND, Component.literal(depot))
                .withStyle(ChatFormatting.GOLD));
    }

}
