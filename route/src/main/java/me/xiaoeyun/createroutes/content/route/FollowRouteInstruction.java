package me.xiaoeyun.createroutes.content.route;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.entity.TrainStatus;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime.State;
import com.simibubi.create.content.trains.schedule.destination.TextScheduleInstruction;

import me.xiaoeyun.createroutes.CreateRoutes;
import me.xiaoeyun.createroutes.client.CtSkin;
import me.xiaoeyun.createroutes.client.RouteListScreen;
import me.xiaoeyun.createroutes.client.RouteScreen;
import me.xiaoeyun.createroutes.client.RouteTrail;
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
 * One schedule entry naming a whole {@link Route}, and the header of the stops {@link RouteExpansion} inserts
 * after it. Not a destination: a header that counted as a stop would reach every station board as one more,
 * nameless, place to go.
 */
public class FollowRouteInstruction extends TextScheduleInstruction {

    /** Inherited from {@code TextScheduleInstruction}; holds the route's display name. */
    public static final String NBT_TEXT = "Text";

    /** The route this entry follows, as an id; blank tells a reference that never resolved apart from one whose route was deleted. */
    public static final String NBT_ROUTE = "Route";

    /** Screen position of the picker, not a route id — rewritten from {@link #NBT_ROUTE} whenever the editor opens. */
    static final String NBT_INDEX = "RouteIndex";
    public static final String NBT_REVERSED = "Reversed";
    public static final String NBT_SKIP_TERMINUS = "SkipTerminus";

    /** The route hash this entry's stops were expanded against; {@link RouteExpansion} owns it. */
    public static final String NBT_HASH = "Hash";

    // Spelled out, not built from a prefix, so scripts/lang_audit.py can see these keys.
    private static final String LANG_SUMMARY =
        "create_routes.schedule.instruction.follow_route.summary";
    private static final String LANG_CONFIGURE =
        "create_routes.schedule.instruction.follow_route.configure";
    private static final String LANG_MARK_REVERSED =
        "create_routes.schedule.instruction.follow_route.mark.reversed";
    private static final String LANG_MARK_SKIPPED =
        "create_routes.schedule.instruction.follow_route.mark.skipped";
    private static final String LANG_MISSING =
        "create_routes.schedule.instruction.follow_route.missing";
    private static final String LANG_STATUS_INVALID =
        "create_routes.train.status.invalid_route";
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

    /** The stops carry their own waits, borrowed from the route's defaults where they declare none. */
    @Override
    public boolean supportsConditions() {
        return false;
    }

    /** The route's name as it was picked, marked when this reference is reversed or skips its terminus. */
    @Override
    public Pair<ItemStack, Component> getSummary() {
        MutableComponent text = label();
        if (flag(NBT_REVERSED))
            text.append(mark(LANG_MARK_REVERSED));
        if (flag(NBT_SKIP_TERMINUS))
            text.append(mark(LANG_MARK_SKIPPED));
        return Pair.of(AllBlocks.TRACK_STATION.asStack(), text);
    }

    /** The name picked, or a standing-in one — a deleted route's id never comes back, so its old name is a lie. */
    private MutableComponent label() {
        return deleted() ? Component.translatable(LANG_MISSING)
            .withStyle(ChatFormatting.ITALIC) : Component.literal(getLabelText());
    }

    /**
     * True when this points at a route the world no longer has.
     *
     * <p>The name shown is the one picked, so without this a deleted route reads as a working entry. Only a
     * client is told which routes exist; one that knows of none cannot tell, and says nothing.
     */
    private boolean deleted() {
        RouteReference reference = reference();
        return reference != null && !ClientRoutes.all()
            .isEmpty() && ClientRoutes.nameOf(reference.route()) == null;
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

    /** {@link #toggle} writes a boolean; {@code getInt} reads it back because it accepts any numeric tag. */
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
        // "Dummy" is the key ModularGuiLine skips when it draws its field plates.
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

    /** Written straight to the entry's data: {@code ModularGuiLine.saveValues} only saves a text box or a scroll input. */
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

    /** Steps aside so the stops behind it can run. */
    @Override
    @Nullable
    public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        // Held rather than skipped: a train quietly running a shortened schedule hides whatever emptied the route.
        if (stuck(runtime, level)) {
            report(runtime.train.status);
            runtime.startCooldown();
            return null;
        }
        runtime.state = State.PRE_TRANSIT;
        runtime.currentEntry++;
        return null;
    }

    /**
     * True when this entry can send a train nowhere: the route it names is gone, or expansion left no stops
     * behind it.
     *
     * <p>A block's stops sit immediately behind its header, so the entry after this one answers the second
     * without flattening the route again.
     */
    private boolean stuck(ScheduleRuntime runtime, Level level) {
        RouteReference reference = reference();
        MinecraftServer server = level.getServer();
        if (reference != null && server != null && RouteStore.get(server)
            .get(reference.route()) == null)
            return true;

        int next = runtime.currentEntry + 1;
        return next >= runtime.schedule.entries.size()
            || !RouteExpansion.isStop(runtime.schedule.entries.get(next));
    }

    /** Create's own no_match names a station filter, which a route is not; the latch is the one it uses too. */
    private static void report(TrainStatus status) {
        if (status.navigation)
            return;
        status.addMessage(new TrainStatus.StatusMessage(Component.literal(" - ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.translatable(LANG_STATUS_INVALID)
                .withStyle(style -> style.withColor(0xFFD3B4)))));
        status.navigation = true;
    }

    /** Overridden because {@code TextScheduleInstruction} builds its key with {@code CreateLang}, under Create's namespace rather than ours. */
    @Override
    public List<Component> getTitleAs(String type) {
        // type is ignored deliberately — this instruction is only ever asked for its title as an instruction.
        return ImmutableList.of(Component.translatable(LANG_SUMMARY)
            .withStyle(ChatFormatting.GOLD),
            deleted() ? label() : Component.translatable("create.generic.in_quotes", label()));
    }

}
