package me.xiaoeyun.createtransit.content.route;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllMenuTypes;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

/** Edits a route by lending it Create's own schedule screen, on a stack that exists only for the trip and is never handed to a player. */
public class RouteEditSession {

    /** Points the borrowed stack at its route, and is how the menu is recognised. */
    private static final String NBT_MARKER = "CtRoute";

    /** What that route is currently called, which is what the editor shows and edits. */
    private static final String NBT_NAME = "CtRouteName";

    /** The route's default conditions, riding out on the same stack since a {@code Schedule} has nowhere to hold them. */
    private static final String NBT_DEFAULTS = "CtDefaults";

    private RouteEditSession() {}

    public static void open(ServerPlayer player, Route route) {
        ItemStack stack = AllItems.SCHEDULE.asStack();
        CompoundTag tag = stack.getOrCreateTag();

        Schedule schedule = new Schedule();
        schedule.entries = route.entries;
        tag.put("Schedule", schedule.write());
        tag.putUUID(NBT_MARKER, route.id);
        tag.putString(NBT_NAME, route.name);
        tag.put(NBT_DEFAULTS, Route.writeConditions(route.defaults));

        NetworkHooks.openScreen(player,
            new SimpleMenuProvider(
                (id, inventory, p) -> new ScheduleMenu(AllMenuTypes.SCHEDULE.get(), id, inventory, stack),
                // No title: the screen draws it on a plaque the route layout doesn't have.
                CommonComponents.EMPTY),
            buffer -> buffer.writeItem(stack));
    }

    /** Whether a menu's stack is one of ours rather than a schedule a player holds. */
    public static boolean isEditor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_MARKER);
    }

    /** Which route the editor was opened on, read off the stack by the client. */
    public static UUID routeOf(ItemStack stack) {
        return stack.getOrCreateTag()
            .getUUID(NBT_MARKER);
    }

    public static String nameOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(NBT_NAME);
    }

    public static List<List<ScheduleWaitCondition>> defaultsOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? new ArrayList<>() : Route.readConditions(tag.getList(NBT_DEFAULTS, Tag.TAG_LIST));
    }

    /** Takes the edited stops, name and defaults back in one message, unless the stops would make the route reach itself. */
    public static void save(ServerPlayer player, UUID id, Schedule edited, String name, ListTag defaults) {
        RouteStore store = RouteStore.get(player.server);
        Route route = store.get(id);
        if (route == null) {
            tell(player, "create_transit.route.save.missing", ChatFormatting.RED);
            return;
        }

        List<ScheduleEntry> before = route.entries;
        route.entries = edited.entries;
        if (route.containsCycle(store::get)) {
            route.entries = before;
            tell(player, "create_transit.route.save.cycle", ChatFormatting.RED, route.name);
            return;
        }

        route.defaults = Route.readConditions(defaults);
        store.setDirty();
        store.syncNames();
        tell(player, "create_transit.route.save.done", ChatFormatting.GREEN, route.name);

        // Refused rather than silently kept: two routes with the same name would make typing one ambiguous.
        if (!route.name.equals(name) && !store.rename(id, name))
            tell(player, "create_transit.route.rename.taken", ChatFormatting.RED, name);
    }

    /** Said in chat because the screen it would belong on has already closed. */
    private static void tell(ServerPlayer player, String key, ChatFormatting style, Object... args) {
        player.displayClientMessage(Component.translatable(key, args)
            .withStyle(style), false);
    }

}
