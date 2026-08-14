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

/**
 * Editing a route by lending it Create's own schedule screen.
 *
 * <p>A route is a schedule with a name, so the screen that edits a schedule can
 * edit a route — and it is the only screen that can edit one <em>completely</em>.
 * Every widget an instruction or condition puts in that editor is written
 * against {@code ScheduleScreen}: Create Railways Navigator opens its settings
 * windows only after checking {@code Minecraft.getInstance().screen instanceof
 * ScheduleScreen}, and item filters need the ghost slots that only its menu
 * has. Reimplementing the screen loses all of that quietly — the buttons still
 * draw, and do nothing. Borrowing it loses none of it, because nothing is being
 * imitated.
 *
 * <p>The screen is opened on a stack that exists only for the trip: a schedule
 * item that is never given to anybody, carrying the route's stops and a marker
 * naming the route. Nothing is handed out, so there is no editor item to drop,
 * store, or accidentally put in a train — where it would run as a stamped copy
 * and stop following the route, which is the one thing this system exists to
 * prevent.
 *
 * <p>One mixin makes that stack behave: {@code HeldItemGhostItemMenu.stillValid}
 * asks whether the menu's stack is still the one selected in the hotbar, which
 * a stack that was never in an inventory can never be; it is answered for ours.
 */
public class RouteEditSession {

    /** Points the borrowed stack at its route, and is how the menu is recognised. */
    private static final String NBT_MARKER = "CtRoute";

    /** What that route is currently called, which is what the editor shows and edits. */
    private static final String NBT_NAME = "CtRouteName";

    /**
     * The route's default conditions, riding out on the same stack.
     *
     * <p>They are not a {@code Schedule}'s to hold, so Create's own parse of the
     * stack ignores them and they come back in a message of their own. Sending
     * them out this way rather than in a second packet keeps the editor's whole
     * opening state in one object, which is also the one that decides whether
     * this is a route at all.
     */
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
                // No title, because the screen draws it on a plaque that the
                // route layout does not have. Suppressing the draw instead
                // would mean an injection into Create; not giving it anything
                // to draw costs nothing and cannot break. The field is read
                // nowhere else in ScheduleScreen.
                CommonComponents.EMPTY),
            buffer -> buffer.writeItem(stack));
    }

    /** Whether a menu's stack is one of ours rather than a schedule a player holds. */
    public static boolean isEditor(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_MARKER);
    }

    /**
     * Which route the editor was opened on, read off the stack by the client.
     *
     * <p>The client is told the name rather than trusted to remember it, and
     * says it back when it saves — so a message that arrives late, or after the
     * player has opened something else, still names what it means.
     */
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

    /**
     * Takes the edited stops, name and defaults back in one message, unless the
     * stops would make the route reach itself.
     *
     * <p>One call rather than a lookup and a session, because the server keeps
     * no record of who is editing what — the client says which route this is,
     * every time.
     */
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
        // Where the stops went is on every client's map now, so the clients are
        // told. A route that follows this one is drawn from what it says here.
        store.syncNames();
        tell(player, "create_transit.route.save.done", ChatFormatting.GREEN, route.name);

        // Refused rather than silently kept, because the name is how a reference
        // is authored: two routes called the same thing would make typing one
        // ambiguous. The route keeps the name it had.
        if (!route.name.equals(name) && !store.rename(id, name))
            tell(player, "create_transit.route.rename.taken", ChatFormatting.RED, name);
    }

    /**
     * Said in chat because the screen it would belong on has already closed.
     * A refused save is silent otherwise, and silence reads as success.
     */
    private static void tell(ServerPlayer player, String key, ChatFormatting style, Object... args) {
        player.displayClientMessage(Component.translatable(key, args)
            .withStyle(style), false);
    }

}
