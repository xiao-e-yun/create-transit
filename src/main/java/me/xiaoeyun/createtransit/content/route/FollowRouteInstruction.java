package me.xiaoeyun.createtransit.content.route;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime.State;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createtransit.client.RouteListScreen;
import me.xiaoeyun.createtransit.client.RouteScreen;
import me.xiaoeyun.createtransit.client.RouteTrail;
import me.xiaoeyun.createtransit.mixin.client.ModularGuiLineBuilderAccessor;
import me.xiaoeyun.createtransit.mixin.client.ScheduleScreenAccessor;
import me.xiaoeyun.createtransit.network.CtPackets;
import me.xiaoeyun.createtransit.network.RouteEditPacket;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One schedule entry standing for a whole {@link Route}.
 *
 * <p>Extending {@link DestinationInstruction} is load-bearing rather than
 * convenient. {@code ScheduleRuntime.predictForEntry} ignores every instruction
 * that is not one, so a follower that inherited from anything else would be
 * invisible to platform displays and to anything reading their predictions. The
 * inherited text field carries the route's name, and {@link #getFilter} is
 * overridden to answer the station currently resolved, which is what those
 * displays actually want to show.
 *
 * <p>The route is resolved a stop at a time rather than expanded into entries.
 * That keeps one live link instead of a stamped copy — editing a route takes
 * effect on the next stop for every train following it, with no version to
 * compare and nothing to re-stamp. The cost is that only the next stop is
 * visible to vanilla predictions; the rest of the line needs an addon that
 * asks us for it.
 *
 * <p>Waiting is delegated by writing the resolved stop's conditions onto this
 * very entry, because {@code tickConditions} reads the conditions of the entry
 * it is on and nowhere else. Those writes are transient — see the mixin that
 * strips them when a schedule is taken back out of a train.
 */
public class FollowRouteInstruction extends DestinationInstruction {

    /**
     * Inherited from {@code TextScheduleInstruction}; holds the route's name.
     *
     * <p>A label, not the link. It is what the player typed and what the card
     * shows, and it is kept up to date from the route itself every time a train
     * starts this entry — so a renamed route reads correctly without anything
     * having to go and find its references.
     */
    public static final String NBT_TEXT = "Text";

    /**
     * The route this entry actually follows.
     *
     * <p>Written by the editor as the player types, from the name they typed:
     * the client knows every route's id, so resolving happens where the typing
     * does and the server is handed a link rather than a string to look up.
     * Blank when the name matches nothing, which is how a reference that has
     * never resolved is told apart from one whose route was deleted.
     */
    public static final String NBT_ROUTE = "Route";

    /**
     * Which entry of the picker is showing. A screen position, not a link.
     *
     * <p>{@code ModularGuiLine} only knows how to save a scroll input as an int,
     * and an int into a list that gains and loses routes means something else
     * tomorrow. So it is rewritten from {@link #NBT_ROUTE} every time the editor
     * opens, and never read as an answer to which route this is.
     */
    private static final String NBT_INDEX = "RouteIndex";
    public static final String NBT_REVERSED = "Reversed";
    public static final String NBT_SKIP_FIRST = "SkipFirst";

    /** How far into the flattened route this train has got. Transient. */
    public static final String NBT_PROGRESS = "Progress";

    /** The station the current stop resolved to, so displays have a name. Transient. */
    public static final String NBT_RESOLVED = "Resolved";

    // Spelled out rather than built from a prefix so scripts/lang_audit.py can
    // see them. A key assembled at runtime is invisible to it, which turns the
    // one check that catches an unused or misspelt translation into a liar.
    private static final String LANG_SUMMARY =
        "create_transit.schedule.instruction.follow_route.summary";
    private static final String LANG_CONFIGURE =
        "create_transit.schedule.instruction.follow_route.configure";
    private static final String LANG_MARK_REVERSED =
        "create_transit.schedule.instruction.follow_route.mark.reversed";
    private static final String LANG_MARK_SKIPPED =
        "create_transit.schedule.instruction.follow_route.mark.skipped";
    private static final String LANG_REVERSED =
        "create_transit.schedule.instruction.follow_route.reversed";
    private static final String LANG_SKIPPED =
        "create_transit.schedule.instruction.follow_route.skipped";

    /** How wide the route's name may be drawn, before its buttons. */
    private static final int PICKER = 81;

    /** What a cut name ends with, the same mark the tables use. */
    private static final String ELLIPSIS = "…";

    @Override
    public ResourceLocation getId() {
        return CreateTransit.asResource("follow_route");
    }

    @Override
    public boolean supportsConditions() {
        return true;
    }

    /**
     * The station this entry is currently heading for, not the route's name.
     *
     * <p>Everything that reads a destination for display goes through here, so
     * a board shows the stop rather than the line. The name of the line is
     * still {@code getLabelText()}, which is what the editor edits.
     */
    @Override
    public String getFilter() {
        return getData().getString(NBT_RESOLVED);
    }

    /**
     * The route's name, marked where this reference is not the plain case.
     *
     * <p>The card face is the only place these flags can be read without
     * opening anything, and "which way round is this one" is exactly what a
     * card should answer at a glance. Marking only the non-default keeps an
     * ordinary reference looking ordinary.
     */
    @Override
    public Pair<ItemStack, Component> getSummary() {
        MutableComponent text = Component.literal(getLabelText());
        if (flag(NBT_REVERSED))
            text.append(mark(LANG_MARK_REVERSED));
        if (flag(NBT_SKIP_FIRST))
            text.append(mark(LANG_MARK_SKIPPED));
        return Pair.of(AllBlocks.TRACK_STATION.asStack(), text);
    }

    private static MutableComponent mark(String key) {
        return Component.literal(" ")
            .append(Component.translatable(key)
                .withStyle(ChatFormatting.GOLD));
    }

    /** The route this entry points at, or null when it has never resolved to one. */
    @Nullable
    public RouteReference reference() {
        String route = getData().getString(NBT_ROUTE);
        if (route.isEmpty())
            return null;
        try {
            return new RouteReference(UUID.fromString(route), flag(NBT_REVERSED), flag(NBT_SKIP_FIRST));
        } catch (IllegalArgumentException malformed) {
            // Hand-written NBT. Treated as no reference rather than thrown,
            // because this is read while a train is deciding where to go.
            return null;
        }
    }

    /**
     * The two joins are stored as ints because that is what a selection scroll
     * input writes — {@code ModularGuiLine.saveValues} only knows how to put an
     * int or a string, so a boolean would never survive the editor.
     */
    private boolean flag(String key) {
        return getData().getInt(key) != 0;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        // 121px, and not by convention: the row starts at guiLeft + 77 and the
        // screen's background ends just past it. The route takes 0..81 and the
        // two flags the rest, at 83 and 101.
        //
        // Direction and first stop were two scroll inputs here once and came to
        // 122 — one pixel over — so they were taken out, and for a while existed
        // nowhere at all: the card drew their marks and nothing set them. As
        // buttons they cost 36 where words cost 50.
        //
        // The way into the route is not on this row at all. It is stood over the
        // editor's own tick instead, where the space was already empty and where
        // the other button that leaves this editor is — which is what it does.
        // The panel is {@code schedule_2}, 256 by 89 at {@code topPos + 40}: its
        // field runs from 16 to 74 and the tick sits at 47, so there are 31
        // pixels above it and a button needs eighteen. Four of the rest are the
        // gap between the two, which is what tells a pair apart from a column.
        //
        // A second row of the line is not an option. {@code renderWidgetBG}
        // draws every plate at y zero, so a widget below would be a widget with
        // its plate on the row above it.
        // Picked, not typed. A name that matches no route was only ever a way to
        // author a reference that could not resolve, and the client already
        // knows every route there is — so the list is the field.
        // Minus whatever would lead back to the route this is being written
        // into, which the screen is asked because only it knows which route that
        // is. A train's own schedule answers null and gets the lot.
        Screen open = Minecraft.getInstance().screen;
        List<UUID> ids = new ArrayList<>(ClientRoutes
            .referenceable(open instanceof RouteScreen route ? route.editingRoute() : null));
        List<Component> names = new ArrayList<>(ids.size() + 1);
        for (UUID id : ids)
            names.add(Component.literal(ClientRoutes.nameOf(id)));

        // One past the routes, and always there: it is where the button below
        // goes to the list instead of to a route, and it is also the only option
        // a world with no routes yet has — an input with an empty option list
        // has no state to clamp to.
        names.add(Component.translatable("create_transit.route.select.new")
            .withStyle(ChatFormatting.GOLD));

        // Written before the widgets load rather than after they are built,
        // because loadValues reads this tag and would otherwise put the picker
        // on whatever position was saved under a different set of routes.
        RouteReference current = reference();
        int at = Math.max(ids.indexOf(current == null ? null : current.route()), 0);
        getData().putInt(NBT_INDEX, at);
        // A fresh reference shows the first route, so store what is on screen —
        // a picker that looks decided while the entry is blank is a trap.
        select(ids, at);

        ModularGuiLineBuilderAccessor accessor = (ModularGuiLineBuilderAccessor) builder;
        IconButton configure = new IconButton(accessor.createTransit$getX() + 147,
            accessor.createTransit$getY() - 27, AllIcons.I_VIEW_SCHEDULE);
        configure.setToolTip(Component.translatable(LANG_CONFIGURE));
        // Added before the picker so the picker's open list is drawn over it.
        // Widgets are drawn in the order the line holds them, and a button on
        // top of an expanded dropdown covers the option under the cursor.
        //
        // "Dummy" keeps the field plate from being drawn behind it, and stops
        // ModularGuiLine trying to read a value out of a button.
        accessor.createTransit$getTarget()
            .add(Pair.of(configure, "Dummy"));
        toggle(accessor, 83, AllIcons.I_FLIP, NBT_REVERSED, LANG_REVERSED);
        toggle(accessor, 101, AllIcons.I_SKIP_MISSING, NBT_SKIP_FIRST, LANG_SKIPPED);

        // Read at press time rather than captured, because the picker does not
        // exist yet — the button had to be added first.
        SelectionScrollInput[] picker = new SelectionScrollInput[1];
        configure.withCallback(() -> open(picker[0] == null || picker[0].getState() >= ids.size()));

        builder.addSelectionScrollInput(0, PICKER, (input, label) -> {
            picker[0] = input;
            input.forOptions(names)
                // After forOptions, which sets this to the option itself. The
                // field is 81 pixels and a name may be 32 characters, and an
                // unclipped one runs out of the plate, across the buttons and
                // off the edge of the screen. Only what the field shows is cut:
                // the list the tooltip drops down is as wide as it needs to be,
                // and that is where a name is actually read.
                .format(index -> clipped(names.get(index), PICKER - 12))
                .titled(Component.translatable(LANG_SUMMARY))
                // Landing on the last option changes nothing. It is reached by
                // scrolling, and scrolling quickly to the end of a list is not a
                // way anybody means to clear the route they had.
                .calling(index -> {
                    if (index < ids.size())
                        select(ids, index);
                });
        }, NBT_INDEX);
    }

    /**
     * The same text, cut to a width with a mark where it was cut.
     *
     * <p>Its own copy rather than {@code CtSkin.clipped}: that one draws, and
     * what is wanted here is a component to hand to a widget that will draw it
     * later. The style is carried over so the one option that is not a route —
     * the gold row that makes another — is still gold when it is cut.
     */
    @OnlyIn(Dist.CLIENT)
    private static Component clipped(Component text, int width) {
        Font font = Minecraft.getInstance().font;
        String value = text.getString();
        if (font.width(value) <= width)
            return text;
        return Component.literal(font.plainSubstrByWidth(value, width - font.width(ELLIPSIS)) + ELLIPSIS)
            .withStyle(text.getStyle());
    }

    /**
     * One of the two flags, as a button that lights up while it is set.
     *
     * <p>Not a widget the line saves. {@code ModularGuiLine} knows how to store
     * a text box and a scroll input and nothing else, so the value is written
     * where it is read from — the entry's own data — exactly as the chosen route
     * is. Green is Create's own way of saying a button is on, and it costs the
     * row no width at all.
     */
    @OnlyIn(Dist.CLIENT)
    private void toggle(ModularGuiLineBuilderAccessor accessor, int x, ScreenElement icon, String key,
        String tip) {
        IconButton button = new IconButton(accessor.createTransit$getX() + x,
            accessor.createTransit$getY() - 4, icon);
        button.setToolTip(Component.translatable(tip));
        button.green = flag(key);
        button.withCallback(() -> {
            boolean set = !flag(key);
            getData().putBoolean(key, set);
            button.green = set;
        });
        accessor.createTransit$getTarget()
            .add(Pair.of(button, "Dummy"));
    }

    /**
     * Leaves for whichever the picker is pointing at: the route it has chosen,
     * or the list, when it is sitting on the option past the end.
     *
     * <p>The row is confirmed on the way out, and the two calls are Create's own
     * confirm sequence in its order. Both are needed: the callback commits this
     * instruction to its entry, and {@code stopEditing} is what copies the
     * widgets into its data. Only the former, and what is read afterwards is the
     * value from before this edit.
     *
     * <p>Setting the screen closes the schedule behind it, which saves it — the
     * same as every other way out of that screen. A route is opened by asking
     * the server instead, because it arrives as a menu and only the server hands
     * those out; this screen goes away when that one comes.
     */
    @OnlyIn(Dist.CLIENT)
    private void open(boolean list) {
        if (!(Minecraft.getInstance().screen instanceof ScheduleScreen screen))
            return;
        ScheduleScreenAccessor accessor = (ScheduleScreenAccessor) screen;
        accessor.createTransit$getOnEditorClose()
            .accept(true);
        accessor.createTransit$stopEditing();

        RouteReference reference = reference();
        if (list || reference == null) {
            // The trail is left alone: the list is where a different route is
            // picked to stand in this one's place, not a way out of the trip.
            Minecraft.getInstance()
                .setScreen(new RouteListScreen());
            return;
        }

        // The same trail the table's own open button leaves, because this is the
        // same trip. A route reached through the instruction editor is still a
        // route opened from inside another one, and the way back out of it is
        // the way back out of any of them.
        //
        // Or the start of one: this row may be in a train's own schedule, which
        // is the one thing on the trail that is not a route.
        UUID from = screen instanceof RouteScreen route ? route.editingRoute() : null;
        if (from != null)
            RouteTrail.push(from);
        else
            RouteTrail.fromSchedule();
        CtPackets.CHANNEL.sendToServer(new RouteEditPacket(reference.route()));
    }

    /**
     * Points this entry at one of the routes the picker was built from, and
     * labels it with what that route is called.
     *
     * <p>Both, because the id is the link and the name is what everything that
     * displays this entry reads. Out of range — which is what an empty list
     * shows — clears the pair rather than leaving half of it.
     */
    @OnlyIn(Dist.CLIENT)
    private void select(List<UUID> ids, int index) {
        boolean real = index >= 0 && index < ids.size();
        getData().putString(NBT_ROUTE, real ? ids.get(index)
            .toString() : "");
        getData().putString(NBT_TEXT, real ? ClientRoutes.nameOf(ids.get(index)) : "");
    }

    @Override
    @Nullable
    public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        MinecraftServer server = level.getServer();
        RouteReference reference = reference();
        if (server == null || reference == null)
            return skip(runtime);

        RouteStore store = RouteStore.get(server);
        Route route = store.get(reference.route());
        if (route == null) {
            // The same complaint vanilla makes for a destination filter that
            // matches no station, and by the name rather than the id: the label
            // is the last thing the player saw this entry called.
            runtime.train.status.failedNavigationNoTarget(getLabelText());
            runtime.startCooldown();
            return null;
        }

        // The moment the route is in hand is the cheapest place to notice it has
        // been renamed, and the only one that needs no bookkeeping: nothing goes
        // looking for references, they correct themselves as they are used.
        getData().putString(NBT_TEXT, route.name);

        List<ScheduleEntry> stops = route.flatten(store::get, reference.reversed(), reference.skipFirst());
        int progress = getData().getInt(NBT_PROGRESS);
        if (stops.isEmpty() || progress >= stops.size())
            return skip(runtime);

        ScheduleEntry stop = stops.get(progress);
        DiscoveredPath path = stop.instruction.start(runtime, level);
        if (path == null)
            return null;

        // Only now that a path exists is anything committed, so a stop that
        // could not be reached is retried rather than silently passed over.
        getData().putInt(NBT_PROGRESS, progress + 1);
        getData().putString(NBT_RESOLVED, path.destination.name);
        adopt(runtime, stop);
        return path;
    }

    /**
     * Hands this entry the conditions of the stop being travelled to, since the
     * runtime only ever waits on the conditions of the entry it is sitting on.
     */
    private void adopt(ScheduleRuntime runtime, ScheduleEntry stop) {
        if (runtime.schedule == null || runtime.currentEntry >= runtime.schedule.entries.size())
            return;
        runtime.schedule.entries.get(runtime.currentEntry).conditions = stop.conditions;
    }

    /**
     * Leaves the route and lets the schedule move on, which is also how a
     * completed route restarts: a cyclic schedule comes straight back round to
     * this entry with the progress already reset.
     */
    @Nullable
    private DiscoveredPath skip(ScheduleRuntime runtime) {
        getData().putInt(NBT_PROGRESS, 0);
        runtime.state = State.PRE_TRANSIT;
        runtime.currentEntry++;
        runtime.startCooldown();
        return null;
    }

    /** Drops what only makes sense while a particular train is running this route. */
    public void clearProgress() {
        getData().remove(NBT_PROGRESS);
        getData().remove(NBT_RESOLVED);
    }

    /**
     * Overridden because the inherited summary asks Create's language file for
     * a key under Create's namespace, which will never hold ours.
     */
    @Override
    public List<Component> getTitleAs(String type) {
        // The literal ignores `type` deliberately: an instruction is only ever
        // asked for its title as an instruction, and a key built from the
        // argument would be one no tool could find.
        return ImmutableList.of(Component.translatable(LANG_SUMMARY)
            .withStyle(ChatFormatting.GOLD),
            Component.translatable("create.generic.in_quotes", Component.literal(getLabelText())));
    }

}
