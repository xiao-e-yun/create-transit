package me.xiaoeyun.createtransit.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.compat.trainmap.TrainMapManager;
import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.CreateLang;

import me.xiaoeyun.createtransit.content.route.Route;
import me.xiaoeyun.createtransit.content.route.RouteEditSession;
import me.xiaoeyun.createtransit.content.route.RouteReference;
import me.xiaoeyun.createtransit.network.CtPackets;
import me.xiaoeyun.createtransit.network.RouteEditPacket;
import me.xiaoeyun.createtransit.network.RouteSavePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The route editor's main interface: three windows across the whole screen, in
 * place of Create's card list.
 *
 * <p>Owned by the mixin that puts it on Create's screen, and reaching Create
 * only through {@link RouteHost}. That is the point of the split: this class is
 * ordinary client code that can be read, moved and changed without knowing
 * anything about mixin, and every line that a Create update could invalidate is
 * on the other side of that interface.
 *
 * <p>Created once per screen and deliberately not replaced. {@code init()} runs
 * again on a resize <em>and</em> after every added or deleted stop, so building
 * a fresh view there would send the map back to the player and clear the chosen
 * row each time a stop is removed.
 */
public class RouteView {

    /** The gap around and between the windows. */
    public static final int PAD = 6;

    /** An IconButton's square. */
    private static final int BUTTON = 18;

    /**
     * How many stops are on screen at once. Everything else is measured from it,
     * because it is the only one of these numbers a player would notice.
     */
    private static final int ROWS = 11;

    /** Where a table's first row starts, below the plaque and the headings. */
    private static final int LIST_AT = CtSkin.BODY_TOP + RouteTable.HEADING;

    /**
     * The two columns, sized to hold whole rows rather than cut to them.
     *
     * <p>Asked for as the room the content needs rather than worked out as a
     * total: the rim is the frame's business, and the last time it was counted
     * here the table quietly showed ten rows instead of eleven.
     */
    private static final int BODY =
        CtSkin.windowHeight(RouteTable.HEADING + ROWS * CtSkin.ROW_HEIGHT);

    /**
     * The map and the route's own conditions, which is the narrower column.
     *
     * <p>118 of it reaches the conditions and 90 of that is the field they are
     * drawn in, once the two scroll arrows have their eleven pixels either side.
     * A "wait 5s" is about 60, so one column of them fits without scrolling —
     * which is the whole requirement, because the alternatives past the first
     * are what the arrows are for.
     */
    private static final int LEFT = 120;

    private static final int MAP = 100;

    /**
     * How near the cursor has to be to a station to mean it: four pixels, or the
     * sprite itself, whichever is the larger.
     *
     * <p>Neither alone is enough. Create measures three blocks, and the station
     * sprite is drawn in blocks too, so the two grow together — but pulled back
     * far enough to see a route, three blocks is a fraction of a pixel and the
     * station cannot be hit at all. A fixed four pixels fixes that end and breaks
     * the other: zoomed right in, the sprite is twenty pixels across and the
     * target sits in the middle of it, so the thing on screen is mostly not
     * clickable.
     */
    private static final int REACH = 4;

    /** Create's own, which is what the sprite it draws is measured in. */
    private static final int SPRITE = 3;

    /**
     * How far back the map may be pulled, in pixels per block.
     *
     * <p>The floor rather than the arrow: every mark on here is measured in
     * blocks, so pulled back far enough they all go under a pixel together, and
     * a map where nothing can be seen is not a view worth being able to reach.
     */
    private static final float FLOOR = 1 / 8f;

    private static final int MARKER = 0xFFFFFFFF;

    /**
     * A station this route stops at, ringed in this colour.
     *
     * <p>The ring is Create's own — the one it puts round a station the cursor
     * is on — because the station cannot be recoloured and a mark of our own
     * drawing would be a second kind of station on a map that has one.
     */
    private static final int ON_ROUTE = 0xFF4C8CFF;

    /** The middle of Create's five by five station sprite, which its ring turns about. */
    private static final float SPRITE_MIDDLE = 2.5F;

    /** How wide a station is ringed when a row names it, in pixels. */
    private static final int LINK = 4;

    /**
     * The ground under the map, dark enough that a rail is the brightest thing
     * on it. Create draws track and nothing else — no terrain — so without this
     * the whole window is the checkered field showing through.
     */
    private static final int GROUND = 0xB3000000;

    /** Vanilla's map markers: sprite zero of sixteen, eight square on a 128 sheet. */
    private static final ResourceLocation MAP_ICONS =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/map/map_icons.png");

    private static final int MAP_ICON = 8;

    private static final int MAP_SHEET = 128;

    /**
     * The stops, and the only thing on this screen a wider one can spend itself
     * on. Every height above is a constant and stays one.
     *
     * <p>The floor is what fits in 320 beside the column on the left. Minecraft
     * picks the largest GUI scale at which the screen is still at least 320 by
     * 240, so that is the narrowest any player can be given, and the layout is
     * sized to it before it is allowed to grow at all.
     *
     * <p>The ceiling is the widest row there is anything to put in: the mark,
     * the icon, the action column, a station name at its 32 character limit, and
     * the three buttons. Past that the extra pixels are gap between a name and
     * the buttons that act on it — further to reach, with no more to read.
     */
    private static final int RIGHT_MIN = 182;

    private static final int RIGHT_MAX = 310;

    private static int right(int width) {
        return Mth.clamp(width - PAD * 3 - LEFT, RIGHT_MIN, RIGHT_MAX);
    }

    private static final int HEIGHT = BODY + PAD * 2;

    /** The screen is only asked where the window sits on it, and how wide. */
    private static int originX(int width) {
        return (width - (LEFT + right(width) + PAD * 3)) / 2;
    }

    private static int originY(int height) {
        return (height - HEIGHT) / 2;
    }

    /**
     * The strip along the route window's foot that its buttons sit on.
     *
     * <p>The conditions were already being held this far off the bottom so that
     * a tall column would not pass under the buttons, and a strip costs the same
     * pixels the holding-off already did. What it buys is that the conditions
     * now stop at an edge: unpainted, the last row was sliced in mid-air, which
     * reads as a drawing fault rather than as a list that carries on.
     */
    private static final int FOOTER = BUTTON + 4;

    /** How far into the footer a button sits, clear of the plate's own bevel. */
    private static final int FOOTER_INSET = 3;

    private final RouteHost host;

    /** Which route this is, said back to the server when the envelope is sent. */
    private final UUID route;

    /** What it is called, which is a label the player may change. */
    private String name;

    /**
     * The conditions a stop borrows when it declares none of its own.
     *
     * <p>Edited here and sent back on close, because they are the one part of a
     * route that is not a schedule and so cannot ride in Create's own packet.
     */
    private final List<List<ScheduleWaitCondition>> defaults;

    /** What the table drew last frame, which is also what it is hit-tested against. */
    private List<RouteTable.Line> lines = List.of();

    /** Which stop's conditions are open, or -1 for none. */
    private int conditionsFor = -1;

    /** The stops, down their window. */
    private final Scroll stops = new Scroll();

    /** The open stop's conditions: down the card, and across its alternatives. */
    private final Scroll overrides = new Scroll();

    private final Scroll alternatives = new Scroll();

    /**
     * The route's own, which are their own pair rather than the popup's.
     *
     * <p>Both cards are drawn every frame — the popup over the window, not
     * instead of it — so a shared pair would be clamped against two different
     * sizes and land wherever the second one asked.
     */
    private final Scroll defaultsDown = new Scroll();

    private final Scroll defaultsAcross = new Scroll();

    /** Where the route window's body is, for the same reason. */
    private Box routeBox;

    /** Where the stops window is, so the wheel knows whether it is over it. */
    private Box tableBox;

    /** Where the map is, which is what a fit has to be a fit to. */
    private Box mapBox;

    /** The stops table, kept so a click can be asked of the boxes it drew. */
    private ScrollTable stopList;

    /** How the stops table asks what a press on one of its rows means. */
    private final RouteTable.Stops presses = new RouteTable.Stops() {

        @Override
        public Action at(int index, int slot) {
            return stop(index, slot);
        }

        @Override
        public Action grip(int index, ScrollTable rows) {
            return RouteView.this.grip(index, rows);
        }

        @Override
        public void over(int index) {
            hoveredStop = index;
        }

        @Override
        public boolean lit(int index) {
            return station != null && index < filters.size() && filters.get(index)
                .test(station.name());
        }
    };

    /**
     * What a held press belongs to. Captured on the press rather than found
     * again on each move: the cursor leaves what it grabbed almost at once, and
     * whatever this took hold of is still true wherever the cursor goes.
     */
    private Action dragging;

    /** Where the map is centred, in blocks. NaN until the player's position seeds it. */
    /** What the map says is under the cursor, held until the frame's last word. */
    private List<FormattedText> mapTip;

    /**
     * The station under the cursor, which is ours to work out rather than
     * Create's to tell us.
     *
     * <p>{@code renderAndPick} answers with a line of text and not the station,
     * and it measures the cursor in blocks — three of them — so at the zoom that
     * fits a route on screen its target is a fraction of a pixel. Ours is
     * measured in pixels and stays the same size at every zoom, which is what a
     * thing you are meant to click has to do.
     */
    private Stations.At station;

    /**
     * The stops' filters and every station in the world, both as they were this
     * frame.
     *
     * <p>Read once and shared by the four questions the frame asks of them —
     * what is under the cursor, which stations the route touches, which of them
     * one row means, and which rows one station answers to. A filter is a
     * compiled pattern; asking each row for its own would compile the lot of
     * them again for every row of every frame.
     */
    private List<Predicate<String>> filters = List.of();

    private List<Stations.At> stations = List.of();

    /** Which stop the cursor was on when the table drew, or -1. */
    private int hoveredStop = -1;

    private double mapX = Double.NaN;

    private double mapZ;

    /** Screen pixels per block. */
    private float scale = 1;

    private boolean panning;

    private double grabX;

    private double grabY;

    public RouteView(RouteHost host, ItemStack stack) {
        this.host = host;
        this.route = RouteEditSession.routeOf(stack);
        this.name = RouteEditSession.nameOf(stack);
        this.defaults = RouteEditSession.defaultsOf(stack);
    }

    /** Which route this is, which is what a reference must not be allowed to reach. */
    public UUID route() {
        return route;
    }

    /**
     * Saves the route: its stops, its name, and its default conditions, in
     * place of the schedule-edit packet Create's screen would otherwise send.
     *
     * <p>Called from a redirect on {@code removed()} that never lets that
     * packet go out for a route, so this is the only save it gets.
     */
    public void close() {
        Schedule schedule = new Schedule();
        schedule.entries = host.entries();
        CtPackets.CHANNEL.sendToServer(
            new RouteSavePacket(route, schedule.write(), name, Route.writeConditions(defaults)));
    }

    /**
     * Three windows: where the route runs, what the route is, and what it does.
     *
     * <p>Proportional rather than fixed, because a player at GUI scale 4 has
     * barely half the room of one at 3 — and the editor that opens over this is
     * a fixed 256 by 190, so the layout has to stay wider than that at any
     * scale the game will allow.
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        carry(mouseX, mouseY);
        hoveredStop = -1;
        Screen screen = host.screen();
        Font font = Minecraft.getInstance().font;
        int ox = originX(screen.width);
        int oy = originY(screen.height);

        int x = ox + PAD;
        int y = oy + PAD;
        int rightX = x + LEFT + PAD;

        mapBox = CtSkin.frame(graphics, font, x, y, LEFT, MAP,
            Component.translatable("create_transit.route.window.map"), 0);
        map(graphics, mapBox.x(), mapBox.y(), mapBox.width(), mapBox.height(), mouseX, mouseY,
            partialTicks);

        // The route's own envelope: the conditions its stops borrow when they
        // declare none. They are not a Schedule's to hold, so this window is the
        // only place they can be edited — and the card that edits them is
        // Create's own, at this window's width rather than Create's 195.
        // The footer is the frame's, not a strip laid over it afterwards: the
        // conditions were already being held this far off the bottom, and asking
        // for the room is what keeps a tall column from passing under the buttons
        // standing on it.
        routeBox = CtSkin.frame(graphics, font, x, y + MAP + PAD, LEFT, BODY - MAP - PAD,
            Component.translatable("create_transit.route.window.route"), FOOTER);
        List<RouteTable.Line> defaultLines = new ArrayList<>(RouteTable.defaults(graphics, font, name,
            defaults, editing(RouteTable.DEFAULTS), routeBox.x(), routeBox.y(), routeBox.width(),
            routeBox.bottom(), defaultsDown, defaultsAcross));

        // Beside the tick and on the same strip: the two of them are the ways out
        // of this screen, and one of them carrying Create's plaque while the other
        // stood bare on the field read as two different kinds of thing.
        //
        // Both are ours now, Create's confirm included — it is one line of its
        // own, {@code closeContainer}. On Create's button plate, because that is
        // what the tick was already wearing.
        //
        // Together at the right end, which is not where they were: standing at
        // opposite ends read better, and the left one stood in the corner JEI
        // pins its own buttons to. JEI answers for a click there before the
        // screen is asked at all — so the button you could see was ours and the
        // one that acted was its. Taking the click back first is possible and
        // was tried; it is a fight with another mod's input over a corner we
        // chose, and moving two buttons eighteen pixels is not.
        int footerY = y + BODY - FOOTER + (FOOTER - BUTTON) / 2;
        Box done = new Box(x + LEFT - FOOTER_INSET - BUTTON, footerY, BUTTON, BUTTON);
        Box back = new Box(done.x() - BUTTON, footerY, BUTTON, BUTTON);
        Strip.plate(graphics, back.x(), back.y(), AllIcons.I_CONFIG_BACK, back.holds(mouseX, mouseY));
        Strip.plate(graphics, done.x(), done.y(), AllIcons.I_CONFIRM, done.holds(mouseX, mouseY));
        defaultLines.add(0, new RouteTable.Line(back, this::manage));
        defaultLines.add(0, new RouteTable.Line(done, this::leave));

        int wide = right(screen.width);
        tableBox = CtSkin.frame(graphics, font, rightX, y, wide, BODY,
            Component.translatable("create_transit.route.window.stops"), 0);
        List<ScheduleEntry> entries = host.entries();
        lines = defaultLines;
        stopList = RouteTable.render(graphics, font, entries, tableBox.x(), tableBox.y(),
            tableBox.width(), tableBox.bottom(), mouseX, mouseY, stops, presses);

        // After the table, because half of what it draws is the table's answer:
        // the row under the cursor is only known once the rows have been drawn,
        // and a marker a frame behind the row that asked for it is a marker
        // pointing at where the cursor used to be.
        marks(graphics, mapBox);

        // Over everything, and replacing the list's own hit targets rather than
        // adding to them: while it is up, nothing behind it is clickable.
        //
        // Not drawn at all while Create's editor is up, because that draws
        // itself at the same z as this — and at equal z the winner is whoever
        // draws last, which for the chips' text is after the editor's plates.
        // The stop is remembered, so confirming a condition comes back here.
        if (conditionsFor >= entries.size())
            conditionsFor = -1;
        else if (conditionsFor >= 0 && !host.editorOpen())
            lines = RouteTable.conditions(graphics, font, entries.get(conditionsFor),
                editing(conditionsFor), this::close, screen.width, screen.height, mouseX, mouseY,
                overrides, alternatives);

        // Last, and only while the map is the topmost thing: a station's name is
        // what ties it to the stop that means it, and this is the only place it
        // is said. Held from the drawing because the map is scissored and a
        // tooltip must not be.
        if (mapTip != null && conditionsFor < 0 && !host.editorOpen())
            graphics.renderTooltip(font, mapTip.stream()
                .map(Language.getInstance()::getVisualOrder)
                .toList(), mouseX, mouseY);
    }

    /**
     * What a click on one band of conditions means: the route's own if
     * {@code stop} is {@link RouteTable#DEFAULTS}, and a stop's override
     * otherwise.
     *
     * <p>Which list and which scroll are decided once, here, rather than being
     * carried as a stop number through four layers of drawing so that one
     * comparison at the far end could ask.
     */
    private RouteTable.Conditions editing(int stop) {
        List<List<ScheduleWaitCondition>> columns = columnsOf(stop);
        boolean route = stop == RouteTable.DEFAULTS;
        Scroll across = route ? defaultsAcross : alternatives;

        return new RouteTable.Conditions() {

            @Override
            public Action condition(int column, int row) {
                return (graphics, mouseX, mouseY, click) -> {
                    RouteView.this.condition(graphics, mouseX, mouseY, click, columns, column, row,
                        route);
                    return true;
                };
            }

            @Override
            public Action add(int column) {
                return (graphics, mouseX, mouseY, click) -> {
                    if (click == 0)
                        host.startEditing(new ScheduledDelay(), confirmed -> {
                            if (confirmed)
                                columns.get(column)
                                    .add(host.editedCondition());
                        }, true);
                    else if (click != 1)
                        tip(graphics, mouseX, mouseY, "gui.schedule.add_condition");
                    return true;
                };
            }

            @Override
            public Action alternative() {
                return (graphics, mouseX, mouseY, click) -> {
                    if (click == 0)
                        host.startEditing(new ScheduledDelay(), confirmed -> {
                            if (!confirmed)
                                return;
                            List<ScheduleWaitCondition> column = new ArrayList<>();
                            column.add(host.editedCondition());
                            columns.add(column);
                        }, true);
                    else if (click != 1)
                        tip(graphics, mouseX, mouseY, "gui.schedule.alternative_condition");
                    return true;
                };
            }

            // One alternative at a time, the way Create's arrows move. Where to
            // land came with the arrow, from where the columns were drawn.
            @Override
            public Action scroll(int target) {
                return (graphics, mouseX, mouseY, click) -> {
                    if (click == 0)
                        across.to(target);
                    return true;
                };
            }
        };
    }

    /**
     * What a click on a row of stops means.
     *
     * @param slot 0 opens its conditions, 1 copies it, 2 removes it, and -1 is
     *             the row itself
     */
    private Action stop(int index, int slot) {
        List<ScheduleEntry> entries = host.entries();
        if (index >= entries.size())
            return this::addStop;

        ScheduleEntry entry = entries.get(index);
        RouteReference nested = RouteReference.of(entry.instruction);
        return switch (slot) {
            case 0 -> nested == null ? conditionsOf(entry, index) : open(nested.route());
            case 1 -> (graphics, mouseX, mouseY, click) -> {
                if (click == 0) {
                    entries.add(index + 1, entry.clone());
                    host.rebuild();
                } else if (click != 1)
                    tip(graphics, mouseX, mouseY, "gui.schedule.duplicate");
                return true;
            };
            case 2 -> (graphics, mouseX, mouseY, click) -> {
                if (click == 0) {
                    entries.remove(index);
                    host.rebuild();
                } else if (click != 1)
                    tip(graphics, mouseX, mouseY, "gui.schedule.remove_entry");
                return true;
            };
            default -> (graphics, mouseX, mouseY, click) -> {
                stop(graphics, mouseX, mouseY, click, entry);
                return true;
            };
        };
    }

    /**
     * Carrying a stop to somewhere else in the list.
     *
     * <p>The list is reordered as the cursor passes each row rather than once at
     * the end. It costs nothing — the rows are drawn from the list every frame
     * anyway — and it is better feedback than any ghost row could be, because
     * what moves under the cursor is the thing itself.
     */
    private Action grip(int index, ScrollTable rows) {
        List<ScheduleEntry> entries = host.entries();
        if (index >= entries.size())
            return null;

        return new Action() {

            /** Where it is now, which is not where it was picked up from. */
            private int at = index;

            /**
             * No tooltip. It is the row's text that explains the icon — a hint
             * where a player is already reading — and a panel that follows the
             * cursor while something is being carried under it is in the way of
             * the one thing they are trying to see.
             */
            @Override
            public boolean act(GuiGraphics graphics, double mouseX, double mouseY, int click) {
                return true;
            }

            @Override
            public void drag(double mouseX, double mouseY) {
                int to = rows.rowAt(mouseY);
                if (to < 0 || to >= entries.size() || to == at)
                    return;
                entries.add(to, entries.remove(at));
                at = to;
            }
        };
    }

    /**
     * Keeps a held press going, since there is nowhere else to learn of one.
     *
     * <p>{@code ScheduleScreen} declares no {@code mouseDragged} and a mixin
     * cannot inject into a method its target does not declare, so the button is
     * asked of the window directly — the same way the map's own panning is.
     */
    private void carry(double mouseX, double mouseY) {
        if (dragging == null)
            return;
        long window = Minecraft.getInstance()
            .getWindow()
            .getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            dragging = null;
            return;
        }
        dragging.drag(mouseX, mouseY);
    }

    /**
     * The button onto a stop's own conditions, or nothing where there are none
     * to have.
     *
     * <p>A nested route has none — its stops each answer for themselves — and
     * nor has an instruction that never waits. The slot is still drawn empty so
     * that the two beside it do not shuffle along, and an empty slot answers to
     * nobody.
     */
    private Action conditionsOf(ScheduleEntry entry, int index) {
        if (!entry.instruction.supportsConditions() || RouteReference.of(entry.instruction) != null)
            return null;

        return (graphics, mouseX, mouseY, click) -> {
            if (click == 0) {
                conditionsFor = index;
                // A different stop's conditions, so where the last one was
                // scrolled to means nothing here.
                overrides.reset();
                alternatives.reset();
            } else if (click != 1) {
                // Named after the window it opens rather than with Create's "add
                // condition", which is what the button inside that window does.
                // The second line is the only place the star is explained: it is
                // drawn beside this button and belongs to no rectangle of its
                // own, so this one answers for it.
                if (entry.conditions.isEmpty())
                    tipOf(graphics, mouseX, mouseY, "create_transit.route.window.conditions");
                else
                    tips(graphics, mouseX, mouseY,
                        Component.translatable("create_transit.route.window.conditions"),
                        Component.translatable("create_transit.route.overridden")
                            .withStyle(ChatFormatting.GOLD));
            }
            return true;
        };
    }

    private boolean addStop(GuiGraphics graphics, double mouseX, double mouseY, int click) {
        if (click == 0)
            host.startEditing(new DestinationInstruction(), confirmed -> {
                if (!confirmed)
                    return;
                ScheduleEntry added = new ScheduleEntry();
                added.instruction = host.editedInstruction();
                host.entries()
                    .add(added);
            }, true);
        else if (click != 1)
            tip(graphics, mouseX, mouseY, "gui.schedule.add_entry");
        return true;
    }

    /** No tooltip: a tick on a footer is not a thing anyone needs told. */
    private boolean close(GuiGraphics graphics, double mouseX, double mouseY, int click) {
        if (click == 0)
            conditionsFor = -1;
        return true;
    }

    /**
     * Done with the route, which is what Create's own tick did: closing is what
     * saves, so there is nothing here to hold back until it.
     *
     * <p>No tooltip, the same as Create's. Where it goes is where you came from,
     * and a player who opened this route from another one is the only player it
     * behaves differently for — they know, because they did it.
     */
    private boolean leave(GuiGraphics graphics, double mouseX, double mouseY, int click) {
        if (click == 0)
            back();
        return true;
    }

    /**
     * Out of this route: to the one that opened it, or out of the editor at the
     * top.
     *
     * <p>Both ways out do the same thing, because both mean the same thing. A
     * route opened from inside another is finished with when its own tick is
     * pressed, and Escape is that press for anyone who does not reach for the
     * mouse. The route is saved either way — closing is what saves it, and
     * opening the next one closes this one.
     */
    private void back() {
        if (!RouteTrail.leave())
            Minecraft.getInstance().player.closeContainer();
    }

    /**
     * Over to the list of routes, to open a different one in this one's place.
     *
     * <p>Leaving saves, because this screen's own close is what saves it — the
     * same exit every other way out of here takes, including the one that opens
     * the next route.
     */
    private boolean manage(GuiGraphics graphics, double mouseX, double mouseY, int click) {
        // Sideways, not out. The trail is left alone: whichever route is picked
        // over there takes this one's place, and the way back out of it is still
        // the way this one came.
        if (click == 0)
            Minecraft.getInstance()
                .setScreen(new RouteListScreen());
        else if (click != 1)
            tipOf(graphics, mouseX, mouseY, "create_transit.route.window.manage");
        return true;
    }

    /**
     * Opens the route a stop follows, remembering this one to come back to.
     *
     * <p>The same packet the list sends. Where the player was is not something
     * the server has to be told — it hands out a menu either way — so the trail
     * stays here.
     */
    private Action open(UUID nested) {
        return (graphics, mouseX, mouseY, click) -> {
            if (click == 0) {
                RouteTrail.push(route);
                CtPackets.CHANNEL.sendToServer(new RouteEditPacket(nested));
            } else if (click != 1)
                tipOf(graphics, mouseX, mouseY, "create_transit.route.list.open");
            return true;
        };
    }

    /**
     * Create's own railway map, drawn into our window.
     *
     * <p>None of this is ours: {@code renderAndPick} draws the track network,
     * the stations and the live trains, and answers what the cursor is over. It
     * takes the visible area in <em>blocks</em> — the sections it culls against
     * are positioned at {@code section * 128} in the same space the pose puts
     * them — so the rectangle below is world coordinates, not screen ones.
     *
     * <p>Ticked here because {@code TrainMapEvents} only ticks for FTB Chunks,
     * JourneyMap and Xaero; with none of them installed the data would never be
     * rebuilt and the map would be empty.
     */
    private void map(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY,
        float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || width <= 0 || height <= 0)
            return;
        TrainMapManager.tick(minecraft.level.dimension());
        mapTip = null;
        filters = Stations.each(host.entries());
        stations = Stations.all();
        // Before the drag, which would otherwise be measured against a centre
        // that is not a number yet.
        if (Double.isNaN(mapX))
            frame(minecraft);
        drag(mouseX, mouseY);

        graphics.enableScissor(x, y, x + width, y + height);
        graphics.fill(x, y, x + width, y + height, GROUND);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + width / 2.0, y + height / 2.0, 0);
        pose.scale(scale, scale, 1);
        pose.translate(-mapX, -mapZ, 0);

        double halfWidth = width / 2.0 / scale;
        double halfHeight = height / 2.0 / scale;
        // In blocks, like the bounds: renderAndPick tests the cursor against a
        // station's own position — {@code |mouseX - position.x()| < 3} — which is
        // world space, not the screen space the pose above is drawing in. Given
        // pixels it answers for whatever station happens to stand where the
        // world coordinates read the same as the screen ones, which is nowhere
        // in particular.
        boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        double worldX = mapX + (mouseX - (x + width / 2.0)) / scale;
        double worldZ = mapZ + (mouseY - (y + height / 2.0)) / scale;
        station = over ? Stations.near(stations, worldX, worldZ, reach()) : null;

        // Nothing for Create to pick: a cursor it can never reach. Its own
        // highlight is drawn at its own stricter reach, so left on it appears as
        // a second ring inside ours whenever the map is zoomed in far enough —
        // two marks for one station, one of which is not the one a click means.
        //
        // Nearest-neighbour, because everything on this map is a pixel drawing:
        // linear filtering is what turns a one pixel rail into a smear.
        int away = Integer.MIN_VALUE / 2;
        TrainMapManager.renderAndPick(graphics, away, away, partialTicks, false,
            new Rect2i((int) (mapX - halfWidth), (int) (mapZ - halfHeight), (int) (halfWidth * 2),
                (int) (halfHeight * 2)));

        pose.popPose();
        graphics.disableScissor();
    }

    /**
     * Everything on the map that is ours: the route, the player, and the two
     * ends of whatever the cursor has hold of.
     *
     * <p>All of it in screen pixels rather than under the map's own pose, so
     * that a station is the same size to reach for at every zoom — which is the
     * same reason the cursor's own reach is measured in pixels.
     */
    private void marks(GuiGraphics graphics, Box at) {
        Minecraft minecraft = Minecraft.getInstance();
        if (Double.isNaN(mapX) || minecraft.player == null)
            return;
        graphics.enableScissor(at.x(), at.y(), at.right(), at.bottom());

        // A row lights its stations and a station lights its rows, and one stop
        // may mean four platforms: the filter is the whole of the relation, so
        // there is nothing here to pair up or keep in step.
        Predicate<String> linked =
            hoveredStop >= 0 && hoveredStop < filters.size() ? filters.get(hoveredStop) : name -> false;

        // In the map's own space: everything drawn on the map is measured in
        // blocks and grows with the zoom, and a mark that alone stayed the same
        // size would be the one thing on here that is not lying on the ground.
        // Above it too — Create leaves the depth test on and puts its stations
        // five deep.
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(at.x() + at.width() / 2.0, at.y() + at.height() / 2.0, 10);
        pose.scale(scale, scale, 1);
        pose.translate(-mapX, -mapZ, 0);
        graphics.setColor(FastColor.ARGB32.red(ON_ROUTE) / 255F,
            FastColor.ARGB32.green(ON_ROUTE) / 255F, FastColor.ARGB32.blue(ON_ROUTE) / 255F, 1);
        for (Stations.At on : stations)
            if (filters.stream()
                .anyMatch(filter -> filter.test(on.name())))
                station(graphics, on);
        graphics.setColor(1, 1, 1, 1);

        // Panned away from, the map is a network with no you in it.
        here(graphics, minecraft.player.getX(), minecraft.player.getZ(),
            minecraft.player.getYRot());
        pose.popPose();

        // The two rings are the exception, and on purpose: they outline what a
        // click will act on, and that reach has a floor in pixels because a
        // station three blocks across is not reachable at the zoom that fits a
        // whole route on screen.
        for (Stations.At on : stations)
            if (linked.test(on.name()))
                marker(graphics, screenX(at, on.x()), screenY(at, on.z()), LINK);

        if (station != null) {
            marker(graphics, screenX(at, station.x()), screenY(at, station.z()),
                (int) Math.ceil(reach() * scale));
            mapTip = List.of(Component.literal(station.name()), CommonComponents.EMPTY,
                hint("create_transit.route.map.add"), hint("create_transit.route.map.copy"));
        }
        graphics.disableScissor();
    }

    /** Where a block stands on screen, which every mark here is placed by. */
    private int screenX(Box at, double worldX) {
        return (int) (at.x() + at.width() / 2.0 + (worldX - mapX) * scale);
    }

    private int screenY(Box at, double worldZ) {
        return (int) (at.y() + at.height() / 2.0 + (worldZ - mapZ) * scale);
    }

    /** How near the cursor has to be to a station to mean it, in blocks. */
    private double reach() {
        return Math.max(REACH / scale, SPRITE);
    }

    /**
     * The player, as vanilla's own map draws them.
     *
     * <p>Sprite zero of {@code map_icons}, which is the marker every player has
     * already learnt to look for — and it points, which a dot cannot.
     *
     * <p>Eight blocks across, which is what it is worth on a vanilla map too:
     * eight pixels of a map that draws a block to the pixel.
     *
     * <p>Turned by the facing plus half a turn: the sprite points up the sheet,
     * up the map is north, and a yaw of zero is south. Vanilla's own map does
     * the same sum on the way in, where it packs the facing into sixteenths.
     */
    private static void here(GuiGraphics graphics, double x, double z, float facing) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, z, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(facing + 180));
        graphics.blit(MAP_ICONS, -MAP_ICON / 2, -MAP_ICON / 2, MAP_ICON, MAP_ICON, 0, 0, MAP_ICON,
            MAP_ICON, MAP_SHEET, MAP_SHEET);
        pose.popPose();
    }

    /**
     * Create's own highlight ring, in whatever colour is set.
     *
     * <p>The station itself cannot be recoloured. Tinting multiplies, and its
     * sprite is cream on brown — every colour asked of it comes back a darker
     * yellow, which is how a blue station came out the colour of mud. The ring
     * is seven by seven of pure white, and white multiplied is the colour
     * itself.
     *
     * <p>Turned like the station it goes round: the sprite is drawn to the track
     * and there are two of them, one squared to the world and one at forty five
     * degrees, so a ring drawn straight sits crooked on half the stations there
     * are. The sum that chooses is Create's, copied where the station is read.
     */
    private static void station(GuiGraphics graphics, Stations.At station) {
        AllGuiTextures ring =
            station.rotation() % 2 == 0 ? AllGuiTextures.TRAINMAP_STATION_ORTHO_HIGHLIGHT
                : AllGuiTextures.TRAINMAP_STATION_DIAGO_HIGHLIGHT;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(station.x() - 2, station.z() - 2, 0);
        pose.translate(SPRITE_MIDDLE, SPRITE_MIDDLE, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(90 * (station.rotation() / 2)));
        pose.translate(-SPRITE_MIDDLE, -SPRITE_MIDDLE, 0);
        ring.render(graphics, -1, -1);
        pose.popPose();
    }

    /**
     * A ring around the station a click would mean.
     *
     * <p>Drawn in screen space and at exactly the reach that found it, so that
     * the ring is not a decoration near the station but the outline of what a
     * click will act on.
     */
    private static void marker(GuiGraphics graphics, int x, int y, int reach) {
        graphics.fill(x - reach, y - reach, x + reach + 1, y - reach + 1, MARKER);
        graphics.fill(x - reach, y + reach, x + reach + 1, y + reach + 1, MARKER);
        graphics.fill(x - reach, y - reach, x - reach + 1, y + reach + 1, MARKER);
        graphics.fill(x + reach, y - reach, x + reach + 1, y + reach + 1, MARKER);
    }

    /**
     * The box every station a filter means stands in, in blocks, or null when
     * it means none of them.
     *
     * <p>Zero sized for a single station, which is a box all the same — what
     * asks for this wants a middle, and a route of one stop has one.
     */
    private Box extent(Predicate<String> meant) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Stations.At station : stations) {
            if (!meant.test(station.name()))
                continue;
            minX = Math.min(minX, station.x());
            maxX = Math.max(maxX, station.x());
            minZ = Math.min(minZ, station.z());
            maxZ = Math.max(maxZ, station.z());
        }
        return minX > maxX ? null : new Box(minX, minZ, maxX - minX, maxZ - minZ);
    }

    /**
     * Puts every station a filter means on screen at once, centred and at the
     * zoom that fits them.
     *
     * <p>The zoom moves too. A view the player panned to is worth less than it
     * sounds — the window is a hundred pixels tall and a route is thousands of
     * blocks, so at any zoom that shows a station there is no route on screen to
     * have kept your place in.
     */
    private void look(Predicate<String> meant) {
        Box over = extent(meant);
        if (over == null || mapBox == null)
            return;
        mapX = over.x() + over.width() / 2.0;
        mapZ = over.y() + over.height() / 2.0;
        // A fifth wider than it has to be, so the outermost station is not
        // standing on the frame.
        float across = Math.max(over.width(), 32) * 1.2f;
        float down = Math.max(over.height(), 32) * 1.2f;
        scale = Mth.clamp(Math.min(mapBox.width() / across, mapBox.height() / down), FLOOR, 1);
    }

    /**
     * Opens on the route rather than on wherever the player happens to be
     * standing.
     *
     * <p>A hundred pixels at scale one is a hundred blocks, and a route is
     * thousands — so centred on the player it showed the ground under their feet
     * and none of the line they came here to edit. Framed on the stations the
     * stops mean, the first thing the window says is what the route looks like.
     *
     * <p>Falls back to the player when nothing matches, which is a route with no
     * stops yet or one whose stations are all somewhere else. Zoom is capped at
     * one either way: a single stop has no extent to fit, and filling the window
     * with one station would be a lie about how much there is.
     */
    private void frame(Minecraft minecraft) {
        mapX = minecraft.player.getX();
        mapZ = minecraft.player.getZ();
        look(name -> filters.stream()
            .anyMatch(filter -> filter.test(name)));
    }

    /**
     * Pans while the button is held.
     *
     * <p>{@code ScheduleScreen} declares no {@code mouseDragged} and a mixin
     * cannot inject into a method its target does not declare, so the drag is
     * measured while drawing instead — the button is asked of the window
     * directly.
     */
    private void drag(double mouseX, double mouseY) {
        if (!panning)
            return;
        long window = Minecraft.getInstance()
            .getWindow()
            .getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            panning = false;
            return;
        }
        mapX -= (mouseX - grabX) / scale;
        mapZ -= (mouseY - grabY) / scale;
        grabX = mouseX;
        grabY = mouseY;
    }

    /**
     * What a click on the table does, in place of what a click on a card did.
     *
     * <p>Create calls this twice for every position: once from
     * {@code renderForeground} with {@code click == -1}, which only wants a
     * tooltip, and once from {@code mouseClicked} with the button. Both go
     * through here so that what a place says it does and what it does cannot
     * drift apart.
     *
     * <p>Choosing a row is a side effect of touching it rather than a mode of
     * its own — there is nothing a player would want to choose a stop for
     * without then doing something to it, and a separate click to select is a
     * click that achieves nothing.
     */
    public boolean action(GuiGraphics graphics, double mouseX, double mouseY, int click) {
        if (host.editorOpen())
            return false;

        // The stops table answers from its own boxes rather than from a list
        // built while drawing, so nothing scrolled out of it can be hit. While
        // the conditions are up they replace the layout entirely, table and all.
        if (conditionsFor < 0 && stopList != null) {
            Action stop = stopList.hit(mouseX, mouseY);
            if (stop != null) {
                if (click == 0)
                    dragging = stop;
                return stop.act(graphics, mouseX, mouseY, click);
            }
        }

        Action action = RouteTable.at(lines, mouseX, mouseY);
        if (action == null) {
            // While the conditions are up the click is swallowed and nothing
            // else happens. Closing on a stray click made sense while there was
            // no other way out; there is a button on the footer now, and a
            // window that also closes by being missed is one that closes by
            // accident.
            if (conditionsFor >= 0)
                return true;
            // A click on our layout that hit nothing is still a click on our
            // layout. Letting it through reaches the container behind, which
            // reads a click on no slot as a click outside itself.
            //
            // The footer is deliberately not ours to swallow: the confirm
            // button is a widget, and Create offers widgets the click only
            // after this returns false.
            return onLayout(mouseX, mouseY);
        }
        return action.act(graphics, mouseX, mouseY, click);
    }

    /** A stop opens its own instruction, the way Create's destination field does. */
    private void stop(GuiGraphics graphics, double mouseX, double mouseY, int click, ScheduleEntry entry) {
        if (click == 0) {
            // And says where on the map it is, which is the answer the ringing
            // could only give for a stop that was already on screen.
            look(Stations.meant(entry.instruction));
            host.startEditing(entry.instruction, confirmed -> {
                if (confirmed)
                    entry.instruction = host.editedInstruction();
            }, false);
        } else if (click != 1)
            titled(graphics, mouseX, mouseY, entry.instruction, "instruction", false, true);
    }

    /**
     * Which list a drawn condition came out of: the route's own, or a stop's.
     *
     * <p>The route's stands where an index would rather than being a second
     * argument threaded through the table — a condition is a condition, and the
     * only thing that differs is whose it is.
     */
    private List<List<ScheduleWaitCondition>> columnsOf(int stop) {
        return stop == RouteTable.DEFAULTS ? defaults : host.entries()
            .get(stop).conditions;
    }

    /**
     * A condition, edited or removed the way Create's own card does it — with
     * the one rule of Create's that does not survive being about a route.
     *
     * <p>Create refuses to remove the last condition of the last column, because
     * a schedule entry with none is one the train never leaves. That holds for
     * the route's own — they are what a stop declaring nothing falls back on, so
     * there has to be something there — and not for a stop's, where declaring
     * nothing is the whole point: an empty override is a stop back on the
     * route's defaults, which is where every stop starts.
     */
    private void condition(GuiGraphics graphics, double mouseX, double mouseY, int click,
        List<List<ScheduleWaitCondition>> columns, int column, int row, boolean keepLast) {
        List<ScheduleWaitCondition> conditions = columns.get(column);
        boolean removable = !keepLast || conditions.size() > 1 || columns.size() > 1;

        if (click == 1) {
            if (removable) {
                conditions.remove(row);
                if (conditions.isEmpty())
                    columns.remove(conditions);
            }
            return;
        }
        if (click != 0) {
            titled(graphics, mouseX, mouseY, conditions.get(row), "condition", removable, false);
            return;
        }

        host.startEditing(conditions.get(row), confirmed -> {
            conditions.remove(row);
            if (confirmed) {
                conditions.add(row, host.editedCondition());
                return;
            }
            // A column with nothing in it is not an alternative, it is a way of
            // saying the train may always leave.
            if (conditions.isEmpty())
                columns.remove(conditions);
        }, removable);
    }

    /**
     * What a stop or a condition is, and what clicking it does.
     *
     * <p>The shape is Create's: the field names itself over as many lines as it
     * likes, a blank, then the buttons in italic grey. Assembled here rather
     * than called, because {@code renderActionTooltip} and the two ready-made
     * lines it uses are private to the screen — but they are three components,
     * and copying three components is cheaper than a second accessor.
     */
    private void titled(GuiGraphics graphics, double mouseX, double mouseY, IScheduleInput field, String as,
        boolean deletable, boolean draggable) {
        if (graphics == null)
            return;
        List<Component> tooltip = new ArrayList<>(field.getTitleAs(as));
        tooltip.add(CommonComponents.EMPTY);
        tooltip.add(createHint("gui.schedule.lmb_edit"));
        // Only a stop is carried anywhere. A condition sits in the column that
        // holds it and has no order of its own to change.
        if (draggable)
            tooltip.add(hint("create_transit.route.reorder"));
        if (deletable)
            tooltip.add(createHint("gui.schedule.rmb_remove"));
        graphics.renderTooltip(Minecraft.getInstance().font, tooltip, Optional.empty(), (int) mouseX,
            (int) mouseY);
    }

    /** Ours, in the same voice as Create's own hints. */
    private static Component hint(String key) {
        return Component.translatable(key)
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    private static Component createHint(String key) {
        return CreateLang.translateDirect(key)
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    /** Ours, for the one button Create has no wording for. */
    private void tipOf(GuiGraphics graphics, double mouseX, double mouseY, String key) {
        tips(graphics, mouseX, mouseY, Component.translatable(key));
    }

    /** Create's own wording, so the table explains itself the way the cards did. */
    private void tip(GuiGraphics graphics, double mouseX, double mouseY, String key) {
        tips(graphics, mouseX, mouseY, CreateLang.translateDirect(key));
    }

    private void tips(GuiGraphics graphics, double mouseX, double mouseY, Component... lines) {
        if (graphics == null)
            return;
        graphics.renderTooltip(Minecraft.getInstance().font, List.of(lines), Optional.empty(),
            (int) mouseX, (int) mouseY);
    }

    /**
     * A station if the cursor is on one, and the map itself otherwise.
     *
     * <p>No threshold and no delay: a station is a target four pixels across and
     * everything around it is somewhere to take hold of. The two never want the
     * same press, so neither has to wait to find out what the other meant.
     */
    public boolean grab(double mouseX, double mouseY, int button) {
        if (!idleOverMap(mouseX, mouseY))
            return false;

        if (station != null) {
            if (button == 0) {
                // Its exact name, so a second station called the same is a second
                // answer and not a mistake — which is what the filter means, and
                // what the navigation does with it.
                ScheduleEntry added = new ScheduleEntry();
                DestinationInstruction destination = new DestinationInstruction();
                destination.getData()
                    .putString("Text", station.name());
                added.instruction = destination;
                host.entries()
                    .add(added);
                host.rebuild();
            } else if (button == 1)
                Minecraft.getInstance().keyboardHandler.setClipboard(station.name());
            return true;
        }

        if (button != 0)
            return false;
        // Truncated, because the drag that follows is measured against the whole
        // pixels {@code render} is given while a press arrives with the fraction
        // still on it. Kept as it came, the first frame of every press moved the
        // map by that fraction — always the same way, since dropping the tail of
        // a positive number always makes the difference negative.
        panning = true;
        grabX = (int) mouseX;
        grabY = (int) mouseY;
        return true;
    }

    /**
     * The wheel, over whichever of the three things is under it.
     *
     * <p>The conditions come first and take it wherever the cursor is: while
     * they are up nothing behind them is reachable, and a wheel that scrolled
     * the list underneath would be scrolling something the player cannot see.
     */
    public boolean wheel(double mouseX, double mouseY, double delta) {
        if (host.editorOpen())
            return false;
        if (conditionsFor >= 0)
            return overrides.wheel(delta);
        if (idleOverMap(mouseX, mouseY)) {
            // A block per pixel at 1; the lower bound is what it takes to see a
            // line that spans a few thousand blocks at once. The upper one is
            // four because the map is a pixel a block and nothing past 1:1 adds
            // anything to look at — only Create's station sprite, which is drawn
            // in blocks, grows with it.
            scale = Mth.clamp(scale * (delta > 0 ? 1.5f : 1 / 1.5f), FLOOR, 4f);
            return true;
        }
        if (tableBox != null && tableBox.holds(mouseX, mouseY))
            return stops.wheel(delta);
        // Across the whole window rather than only its body: the wheel is aimed
        // at a column of conditions, and the rim is not somewhere to have to miss.
        int x = originX(host.screen().width) + PAD;
        if (routeBox != null && mouseX >= x && mouseX < x + LEFT && mouseY >= routeBox.y()
            && mouseY < routeBox.bottom())
            return defaultsDown.wheel(delta);
        return false;
    }

    /**
     * Escape closes the conditions before it closes the route, and the route
     * before the editor — one layer per press, outermost last.
     */
    public boolean escape(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE || host.editorOpen())
            return false;
        if (conditionsFor >= 0) {
            conditionsFor = -1;
            return true;
        }
        // At the top it is left alone: closing the screen is what Escape does
        // there, and doing it ourselves would only be doing it sooner. The top
        // is where there is nothing to go back to at all — which is not the same
        // as no route above this one, because a schedule may be down there.
        if (!RouteTrail.leadsBack())
            return false;
        back();
        return true;
    }

    /**
     * Tells JEI where our windows are, so it stops drawing its item list over
     * them. Create reports the small area beside its panel; ours is the screen.
     */
    public List<Rect2i> areas() {
        Screen screen = host.screen();
        int x = originX(screen.width) + PAD;
        int y = originY(screen.height) + PAD;
        return List.of(new Rect2i(x, y, LEFT, BODY),
            new Rect2i(x + LEFT + PAD, y, right(screen.width), BODY));
    }

    /**
     * Anywhere the layout draws, minus the confirm plaque.
     *
     * <p>That corner is left alone on purpose: the button on it is a widget, and
     * Create only offers widgets the click once this has declined it.
     */
    private boolean onLayout(double mouseX, double mouseY) {
        Screen screen = host.screen();
        int x = originX(screen.width) + PAD;
        int y = originY(screen.height) + PAD;
        return mouseX >= x && mouseX < x + LEFT + PAD + right(screen.width) && mouseY >= y
            && mouseY < y + BODY;
    }

    /** On the map, and with nothing over it that the click belongs to instead. */
    private boolean idleOverMap(double mouseX, double mouseY) {
        if (host.editorOpen() || conditionsFor >= 0)
            return false;
        Screen screen = host.screen();
        int x = originX(screen.width) + PAD;
        int y = originY(screen.height) + PAD;
        return mouseX >= x && mouseX < x + LEFT && mouseY >= y && mouseY < y + MAP;
    }

}
