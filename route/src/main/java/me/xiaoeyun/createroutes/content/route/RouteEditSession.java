package me.xiaoeyun.createroutes.content.route;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllMenuTypes;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;

import me.xiaoeyun.createroutes.registry.CrDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

/** Edits a route by lending it Create's own schedule screen, on a stack that exists only for the trip and is never handed to a player. */
public class RouteEditSession {

    /** Points the borrowed stack at its route. Presence of the component itself is how the menu is recognised. */
    private static final String NBT_MARKER = "Route";

    /** What that route is currently called, which is what the editor shows and edits. */
    private static final String NBT_NAME = "Name";

    /** The route's default conditions, riding out on the same stack since a {@code Schedule} has nowhere to hold them. */
    private static final String NBT_DEFAULTS = "Defaults";

    private RouteEditSession() {}

    public static void open(ServerPlayer player, Route route) {
        HolderLookup.Provider registries = player.registryAccess();
        ItemStack stack = AllItems.SCHEDULE.asStack();

        Schedule schedule = new Schedule();
        schedule.entries = route.entries;
        stack.set(AllDataComponents.TRAIN_SCHEDULE, schedule.write(registries));

        CompoundTag session = new CompoundTag();
        session.putUUID(NBT_MARKER, route.id);
        session.putString(NBT_NAME, route.name);
        session.put(NBT_DEFAULTS, Route.writeConditions(registries, route.defaults));
        stack.set(CrDataComponents.ROUTE_EDITOR.get(), session);

        player.openMenu(
            new SimpleMenuProvider(
                (id, inventory, p) -> new ScheduleMenu(AllMenuTypes.SCHEDULE.get(), id, inventory, stack),
                // No title: the screen draws it on a plaque the route layout doesn't have.
                CommonComponents.EMPTY),
            buffer -> ItemStack.STREAM_CODEC.encode(buffer, stack));
    }

    /** Whether a menu's stack is one of ours rather than a schedule a player holds. */
    public static boolean isEditor(ItemStack stack) {
        return stack.has(CrDataComponents.ROUTE_EDITOR.get());
    }

    /** Which route the editor was opened on, read off the stack by the client. */
    public static UUID routeOf(ItemStack stack) {
        return session(stack).getUUID(NBT_MARKER);
    }

    public static String nameOf(ItemStack stack) {
        return session(stack).getString(NBT_NAME);
    }

    public static List<List<ScheduleWaitCondition>> defaultsOf(HolderLookup.Provider registries, ItemStack stack) {
        if (!isEditor(stack))
            return new ArrayList<>();
        return Route.readConditions(registries, session(stack).getList(NBT_DEFAULTS, Tag.TAG_LIST));
    }

    /** Empty rather than null for a stack that is not an editor's, so every reader above is a plain get. */
    private static CompoundTag session(ItemStack stack) {
        return stack.getOrDefault(CrDataComponents.ROUTE_EDITOR.get(), new CompoundTag());
    }

    /** Takes the edited stops, name and defaults back in one message, unless the stops would make the route reach itself. */
    public static void save(ServerPlayer player, UUID id, Schedule edited, String name, ListTag defaults) {
        RouteStore store = RouteStore.get(player.server);
        Route route = store.get(id);
        if (route == null) {
            tell(player, "create_routes.route.save.missing", ChatFormatting.RED);
            return;
        }

        List<ScheduleEntry> before = route.entries;
        route.entries = edited.entries;
        if (route.containsCycle(store::get)) {
            route.entries = before;
            tell(player, "create_routes.route.save.cycle", ChatFormatting.RED, route.name);
            return;
        }

        route.defaults = Route.readConditions(player.registryAccess(), defaults);
        store.setDirty();

        if (!route.name.equals(name) && !store.rename(id, name))
            tell(player, "create_routes.route.rename.taken", ChatFormatting.RED, name);

        store.syncNames();
        tell(player, "create_routes.route.save.done", ChatFormatting.GREEN, route.name);
    }

    /** Said in chat because the screen it would belong on has already closed. */
    private static void tell(ServerPlayer player, String key, ChatFormatting style, Object... args) {
        player.displayClientMessage(Component.translatable(key, args)
            .withStyle(style), false);
    }

}
