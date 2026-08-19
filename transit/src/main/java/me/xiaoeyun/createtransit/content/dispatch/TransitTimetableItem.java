package me.xiaoeyun.createtransit.content.dispatch;

import java.util.List;

import com.simibubi.create.AllKeys;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;

import me.xiaoeyun.createtransit.registry.CtDataComponents;
import me.xiaoeyun.createtransit.registry.CtItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
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
        if (!level.isClientSide && player != null) {
            stack.set(CtDataComponents.TIMETABLE_DEPOT.get(), station.name);
            player.displayClientMessage(
                Component.translatable(LANG_BOUND, Component.literal(station.name)), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A timetable already bound to a bay, so the key stays spelled in one place. */
    public static ItemStack of(String depot) {
        ItemStack stack = CtItems.TRANSIT_TIMETABLE.asStack();
        stack.set(CtDataComponents.TIMETABLE_DEPOT.get(), depot);
        return stack;
    }

    /** The bound bay, or blank while unbound. */
    public static String depot(ItemStack stack) {
        return stack.getOrDefault(CtDataComponents.TIMETABLE_DEPOT.get(), "");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
        TooltipFlag flag) {
        // Create's description block owns the Shift view.
        if (AllKeys.shiftDown())
            return;
        tooltip.add(CommonComponents.SPACE);
        String depot = depot(stack);
        if (depot.isEmpty())
            tooltip.add(Component.translatable(LANG_BIND_HINT)
                .withStyle(ChatFormatting.GRAY));
        else
            tooltip.add(Component.translatable(LANG_BOUND, Component.literal(depot))
                .withStyle(ChatFormatting.GOLD));
    }

}
