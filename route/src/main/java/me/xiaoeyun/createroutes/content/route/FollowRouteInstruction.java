package me.xiaoeyun.createroutes.content.route;

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
import com.simibubi.create.content.trains.station.GlobalStation;

import me.xiaoeyun.createroutes.CreateRoutes;
import me.xiaoeyun.createroutes.client.CtSkin;
import me.xiaoeyun.createroutes.client.RouteListScreen;
import me.xiaoeyun.createroutes.client.RouteScreen;
import me.xiaoeyun.createroutes.client.RouteTrail;
import me.xiaoeyun.createroutes.schedule.Repeats;
import me.xiaoeyun.createroutes.mixin.client.ModularGuiLineBuilderAccessor;
import me.xiaoeyun.createroutes.mixin.client.ScheduleScreenAccessor;
import me.xiaoeyun.createroutes.network.CrPackets;
import me.xiaoeyun.createroutes.network.RouteEditPacket;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
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
 * One schedule entry standing for a whole {@link Route}; extends {@link DestinationInstruction} because
 * {@code ScheduleRuntime.predictForEntry} ignores anything else, and resolves one stop at a time so an
 * edited route takes effect on the next stop rather than needing a re-stamped copy.
 */
public class FollowRouteInstruction extends DestinationInstruction implements Repeats {

    /** Inherited from {@code TextScheduleInstruction}; holds the route's display name, refreshed from the route each time this entry starts. */
    public static final String NBT_TEXT = "Text";

    /** The route this entry follows, as an id; blank tells a reference that never resolved apart from one whose route was deleted. */
    public static final String NBT_ROUTE = "Route";

    /** Screen position of the picker, not a route id — rewritten from {@link #NBT_ROUTE} whenever the editor opens. */
    private static final String NBT_INDEX = "RouteIndex";
    public static final String NBT_REVERSED = "Reversed";
    public static final String NBT_SKIP_TERMINUS = "SkipTerminus";

    /** How far through the flattened route this train has gotten; advances only once standing at the sent station — Create gives no arrival hook. */
    public static final String NBT_PROGRESS = "Progress";

    /** The station last sent to, while the train is still travelling there. Transient. */
    private static final String NBT_SENT = "Sent";

    /** The station the current stop resolved to, so displays have a name. Transient. */
    public static final String NBT_RESOLVED = "Resolved";

    // Spelled out, not built from a prefix, so scripts/lang_audit.py can see these keys.
    private static final String LANG_SUMMARY =
        "create_routes.schedule.instruction.follow_route.summary";
    private static final String LANG_CONFIGURE =
        "create_routes.schedule.instruction.follow_route.configure";
    private static final String LANG_MARK_REVERSED =
        "create_routes.schedule.instruction.follow_route.mark.reversed";
    private static final String LANG_MARK_SKIPPED =
        "create_routes.schedule.instruction.follow_route.mark.skipped";
    private static final String LANG_REVERSED =
        "create_routes.schedule.instruction.follow_route.reversed";
    private static final String LANG_SKIPPED =
        "create_routes.schedule.instruction.follow_route.skipped";

    /** How wide the route's name may be drawn, before its buttons. */
    private static final int PICKER = 81;

    @Override
    public ResourceLocation getId() {
        return CreateRoutes.asResource("follow_route");
    }

    @Override
    public boolean supportsConditions() {
        return true;
    }

    /** The station this entry is currently heading for, not the route's name. */
    @Override
    public String getFilter() {
        return getData().getString(NBT_RESOLVED);
    }

    /** The route's name, marked when this reference is reversed or skips its terminus. */
    @Override
    public Pair<ItemStack, Component> getSummary() {
        MutableComponent text = Component.literal(getLabelText());
        if (flag(NBT_REVERSED))
            text.append(mark(LANG_MARK_REVERSED));
        if (flag(NBT_SKIP_TERMINUS))
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
            return new RouteReference(UUID.fromString(route), flag(NBT_REVERSED), flag(NBT_SKIP_TERMINUS));
        } catch (IllegalArgumentException malformed) {
            // Hand-written NBT; treated as no reference since this is read mid-decision, not thrown.
            return null;
        }
    }

    /** Stored as ints, not booleans, because {@code ModularGuiLine.saveValues} only knows how to write an int or a string. */
    private boolean flag(String key) {
        return getData().getInt(key) != 0;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(ModularGuiLineBuilder builder) {
        Screen open = Minecraft.getInstance().screen;
        List<UUID> ids = ClientRoutes
            .referenceable(open instanceof RouteScreen route ? route.editingRoute() : null);
        List<Component> names = new ArrayList<>(ids.size() + 1);
        for (UUID id : ids)
            names.add(Component.literal(ClientRoutes.nameOf(id)));

        // A scroll input needs at least one option to have a state; the placeholder selects nothing.
        if (ids.isEmpty())
            names.add(Component.translatable("create_routes.route.select.none")
                .withStyle(ChatFormatting.GRAY));

        // Written before the widgets build, since loadValues reads this tag when constructing them.
        RouteReference current = reference();
        int at = Math.max(ids.indexOf(current == null ? null : current.route()), 0);
        getData().putInt(NBT_INDEX, at);
        select(ids, at);

        ModularGuiLineBuilderAccessor accessor = (ModularGuiLineBuilderAccessor) builder;
        IconButton configure = new IconButton(accessor.createTransit$getX() + 147,
            accessor.createTransit$getY() - 27, AllIcons.I_VIEW_SCHEDULE);
        configure.setToolTip(Component.translatable(LANG_CONFIGURE));
        // Added before the picker so the picker's dropdown draws over it, not under it.
        // "Dummy" keeps ModularGuiLine from drawing a field plate behind it or reading a value out of it.
        accessor.createTransit$getTarget()
            .add(Pair.of(configure, "Dummy"));
        toggle(accessor, 83, AllIcons.I_FLIP, NBT_REVERSED, LANG_REVERSED);
        toggle(accessor, 101, AllIcons.I_SKIP_MISSING, NBT_SKIP_TERMINUS, LANG_SKIPPED);

        // Read at press time, not captured, since the picker doesn't exist until after this button is added.
        SelectionScrollInput[] picker = new SelectionScrollInput[1];
        configure.withCallback(() -> open(picker[0] == null || picker[0].getState() >= ids.size()));

        builder.addSelectionScrollInput(0, PICKER, (input, label) -> {
            picker[0] = input;
            input.forOptions(names)
                // .format must come after forOptions, which resets it to the option's own text.
                .format(index -> CtSkin.clip(Minecraft.getInstance().font, names.get(index), PICKER - 12))
                .titled(Component.translatable(LANG_SUMMARY))
                .calling(index -> {
                    if (index < ids.size())
                        select(ids, index);
                });
        }, NBT_INDEX);
    }

    /** Written directly to the entry's data rather than through the line's save system, which only knows a text box and a scroll input. */
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

    /** Leaves for the route the picker points at, or the route list when past the end; commits via callback then {@code stopEditing} — both required, Create's own confirm order. */
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
            Minecraft.getInstance()
                .setScreen(new RouteListScreen());
            return;
        }

        UUID from = screen instanceof RouteScreen route ? route.editingRoute() : null;
        if (from != null)
            RouteTrail.push(from);
        else
            RouteTrail.fromSchedule();
        CrPackets.CHANNEL.sendToServer(new RouteEditPacket(reference.route()));
    }

    /** Points this entry at one of the picker's routes and labels it with that route's name; out of range clears both. */
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
            runtime.train.status.failedNavigationNoTarget(getLabelText());
            runtime.startCooldown();
            return null;
        }

        getData().putString(NBT_TEXT, route.name);

        List<ScheduleEntry> stops = route.flatten(store::get, reference.reversed(), reference.skipTerminus());
        int progress = getData().getInt(NBT_PROGRESS);

        // Create re-runs the live entry mid-journey and on assembly, not just on departure — answered for the stop already under way.
        if (runtime.train.navigation.destination != null) {
            if (progress >= stops.size())
                return null;
            int held = runtime.currentEntry;
            DiscoveredPath again = stops.get(progress).instruction.start(runtime, level);
            if (again == null)
                runtime.currentEntry = held;
            return again;
        }

        // Standing at the sent station is the only evidence of arrival — Create gives no arrival callback.
        String sent = getData().getString(NBT_SENT);
        GlobalStation here = runtime.train.getCurrentStation();
        if (!sent.isEmpty() && here != null && here.name.equals(sent)) {
            progress++;
            getData().putInt(NBT_PROGRESS, progress);
            getData().remove(NBT_SENT);
        }

        while (progress < stops.size()) {
            ScheduleEntry stop = stops.get(progress);

            // Instructions signal "nothing for me here" by advancing currentEntry themselves, so it must be read before the stop runs.
            int before = runtime.currentEntry;
            DiscoveredPath path = stop.instruction.start(runtime, level);

            if (path != null) {
                // Progress isn't counted here — only on arrival; the runtime still decides whether the train actually goes.
                getData().putInt(NBT_PROGRESS, progress);
                getData().putString(NBT_SENT, path.destination.name);
                getData().putString(NBT_RESOLVED, path.destination.name);
                adopt(runtime, stop);
                return path;
            }

            // currentEntry unchanged means the stop wants another try; retried on the runtime's own cooldown.
            if (runtime.currentEntry == before)
                return null;

            // currentEntry moved means the stop gave up on itself; restored here so only this route's own progress advances,
            // not the runtime past this whole entry.
            runtime.currentEntry = before;
            progress++;
            getData().putInt(NBT_PROGRESS, progress);
        }

        return skip(runtime);
    }

    /** Copies the stop's conditions onto this entry, since the runtime only waits on the conditions of the entry it is sitting on. */
    private void adopt(ScheduleRuntime runtime, ScheduleEntry stop) {
        if (runtime.schedule == null || runtime.currentEntry >= runtime.schedule.entries.size())
            return;
        runtime.schedule.entries.get(runtime.currentEntry).conditions = stop.conditions;
    }

    /** True while a route is part way through, so Create doesn't step the runtime past this entry after every stop. */
    @Override
    public boolean again() {
        return getData().getInt(NBT_PROGRESS) > 0 || getData().contains(NBT_SENT);
    }

    /** Leaves the route and advances the schedule; a cyclic schedule comes straight back to this entry with progress reset. */
    @Nullable
    private DiscoveredPath skip(ScheduleRuntime runtime) {
        getData().putInt(NBT_PROGRESS, 0);
        getData().remove(NBT_SENT);
        runtime.state = State.PRE_TRANSIT;
        runtime.currentEntry++;
        runtime.startCooldown();
        return null;
    }

    /** Drops what only makes sense while a particular train is running this route. */
    public void clearProgress() {
        getData().remove(NBT_PROGRESS);
        getData().remove(NBT_SENT);
        getData().remove(NBT_RESOLVED);
    }

    @Override
    public void clearTransient(ScheduleEntry entry) {
        clearProgress();
        // Empty is this entry's resting state — it borrows whichever stop's conditions it's heading for.
        entry.conditions = new ArrayList<>();
    }

    /** Overridden because the inherited summary looks up a key under Create's own namespace, not ours. */
    @Override
    public List<Component> getTitleAs(String type) {
        // type is ignored deliberately — this instruction is only ever asked for its title as an instruction.
        return ImmutableList.of(Component.translatable(LANG_SUMMARY)
            .withStyle(ChatFormatting.GOLD),
            Component.translatable("create.generic.in_quotes", Component.literal(getLabelText())));
    }

}
