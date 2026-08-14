package me.xiaoeyun.createroutes.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.CreateLang;

import me.xiaoeyun.createroutes.content.route.Route;
import me.xiaoeyun.createroutes.content.route.RouteEditSession;
import me.xiaoeyun.createroutes.content.route.RouteReference;
import me.xiaoeyun.createroutes.network.CrPackets;
import me.xiaoeyun.createroutes.network.RouteEditPacket;
import me.xiaoeyun.createroutes.network.RouteSavePacket;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The route editor's main interface: three windows across the whole screen, in
 * place of Create's card list.
 *
 * <p>Created once per screen and deliberately not replaced — {@code init()}
 * reruns on a resize and after every added or deleted stop, and a fresh view
 * there would reset the map and the chosen row each time.
 */
public class RouteView {

    /** The gap around and between the windows. */
    public static final int PAD = 6;

    /** An IconButton's square. */
    private static final int BUTTON = 18;

    /** How many stops are on screen at once; everything else is measured from it. */
    private static final int ROWS = 11;

    /** Where a table's first row starts, below the plaque and the headings. */
    private static final int LIST_AT = CtSkin.BODY_TOP + RouteTable.HEADING;

    /** The two columns, sized to hold whole rows rather than cut to them. */
    private static final int BODY =
        CtSkin.windowHeight(RouteTable.HEADING + ROWS * CtSkin.ROW_HEIGHT);

    /** The map and the route's own conditions, which is the narrower column — 118 reaches the conditions, and a "wait 5s" chip needs about 60. */
    private static final int LEFT = 120;

    private static final int MAP = 100;

    /**
     * The stops, and the only thing on this screen a wider one can spend itself
     * on.
     *
     * <p>The floor fits beside the left column at Minecraft's smallest
     * guaranteed screen (320x240); the ceiling is the widest row there is
     * content for — the mark, icon, action column, a 32-character station
     * name, and the three buttons.
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
     * Where the three windows sit on a screen of a given size, worked out once.
     *
     * <p>Every box here is a <em>window</em> (the whole frame, rim included)
     * since hit testing cares about the whole frame; the content boxes
     * ({@link #mapBox}, {@link #routeBox}, {@link #tableBox}) are a frame-inset
     * narrower and kept separate for that reason.
     */
    private record Layout(Box mapWindow, Box routeWindow, Box tableWindow) {

        static Layout of(int width, int height) {
            int x = originX(width) + PAD;
            int y = originY(height) + PAD;
            int rightX = x + LEFT + PAD;
            return new Layout(new Box(x, y, LEFT, MAP),
                new Box(x, y + MAP + PAD, LEFT, BODY - MAP - PAD),
                new Box(rightX, y, right(width), BODY));
        }
    }

    /** This frame's layout, kept only until the screen's size changes. */
    private Layout layout;

    private int layoutWidth = -1;

    private int layoutHeight = -1;

    private Layout layout() {
        Screen screen = host.screen();
        if (layout == null || layoutWidth != screen.width || layoutHeight != screen.height) {
            layout = Layout.of(screen.width, screen.height);
            layoutWidth = screen.width;
            layoutHeight = screen.height;
        }
        return layout;
    }

    /** The strip along the route window's foot that its buttons sit on. */
    private static final int FOOTER = BUTTON + 4;

    /** How far into the footer a button sits, clear of the plate's own bevel. */
    private static final int FOOTER_INSET = 3;

    private final RouteHost host;

    /** Which route this is, said back to the server when the envelope is sent. */
    private final UUID route;

    /** What it is called, which is a label the player may change. */
    private String name;

    /** The conditions a stop borrows when it declares none of its own — edited here and sent back on close since they cannot ride in Create's own schedule packet. */
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

    /** The route's own, which are their own pair rather than the popup's — both cards are drawn every frame, so a shared pair would be clamped against two different sizes. */
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
            Stations.At station = map.station();
            return station != null && index < filters.size() && filters.get(index)
                .test(station.name());
        }
    };

    /** What a held press belongs to — captured on the press rather than found again on each move. */
    private Action dragging;

    /** The pan/zoom railway map, boxed off from the schedule editing this class does. */
    private final RouteMap map;

    /** The stops' filters, as they were this frame — read once and shared by the frame's questions of it, rather than recompiled per row. */
    private List<Predicate<String>> filters = List.of();

    /** Which stop the cursor was on when the table drew, or -1. */
    private int hoveredStop = -1;

    public RouteView(RouteHost host, ItemStack stack) {
        this.host = host;
        this.route = RouteEditSession.routeOf(stack);
        this.name = RouteEditSession.nameOf(stack);
        this.defaults = RouteEditSession.defaultsOf(stack);
        this.map = new RouteMap(host);
    }

    /** Which route this is, which is what a reference must not be allowed to reach. */
    public UUID route() {
        return route;
    }

    /**
     * Saves the route: its stops, its name, and its default conditions, in
     * place of the schedule-edit packet Create's screen would otherwise send.
     *
     * <p>Called from a redirect on {@code removed()} that never lets Create's
     * own save packet go out for a route.
     */
    public void close() {
        Schedule schedule = new Schedule();
        schedule.entries = host.entries();
        CrPackets.CHANNEL.sendToServer(
            new RouteSavePacket(route, schedule.write(), name, Route.writeConditions(defaults)));
    }

    /**
     * Three windows: where the route runs, what the route is, and what it does.
     *
     * <p>Proportional rather than fixed, since the editor that opens over this
     * is a fixed 256 by 190, and the layout must stay wider than that at any
     * GUI scale.
     */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        carry(mouseX, mouseY);
        hoveredStop = -1;
        Screen screen = host.screen();
        Font font = Minecraft.getInstance().font;
        Layout layout = layout();
        Box mapWindow = layout.mapWindow();
        filters = Stations.each(host.entries());

        mapBox = CtSkin.frame(graphics, font, mapWindow.x(), mapWindow.y(), mapWindow.width(),
            mapWindow.height(), Component.translatable("create_routes.route.window.map"), 0);
        map.render(graphics, mapBox, filters, mouseX, mouseY, partialTicks);

        // The route's own envelope: the conditions its stops borrow when they
        // declare none, editable only here since they are not a Schedule's to hold.
        Box routeWindow = layout.routeWindow();
        routeBox = CtSkin.frame(graphics, font, routeWindow.x(), routeWindow.y(), routeWindow.width(),
            routeWindow.height(), Component.translatable("create_routes.route.window.route"), FOOTER);
        List<RouteTable.Line> defaultLines = new ArrayList<>(RouteTable.defaults(graphics, font, name,
            defaults, editing(RouteTable.DEFAULTS), routeBox.x(), routeBox.y(), routeBox.width(),
            routeBox.bottom(), defaultsDown, defaultsAcross));

        // Together at the right end, not opposite corners: JEI answers clicks in
        // the left corner before this screen is ever asked, so a button drawn
        // there would be one you can see but never press.
        int footerY = mapWindow.y() + BODY - FOOTER + (FOOTER - BUTTON) / 2;
        Box done = new Box(mapWindow.x() + LEFT - FOOTER_INSET - BUTTON, footerY, BUTTON, BUTTON);
        Box back = new Box(done.x() - BUTTON, footerY, BUTTON, BUTTON);
        Strip.plate(graphics, back.x(), back.y(), AllIcons.I_CONFIG_BACK, back.holds(mouseX, mouseY));
        Strip.plate(graphics, done.x(), done.y(), AllIcons.I_CONFIRM, done.holds(mouseX, mouseY));
        defaultLines.add(0, new RouteTable.Line(back, this::manage));
        defaultLines.add(0, new RouteTable.Line(done, this::leave));

        Box tableWindow = layout.tableWindow();
        tableBox = CtSkin.frame(graphics, font, tableWindow.x(), tableWindow.y(), tableWindow.width(),
            tableWindow.height(), Component.translatable("create_routes.route.window.stops"), 0);
        List<ScheduleEntry> entries = host.entries();
        lines = defaultLines;
        stopList = RouteTable.render(graphics, font, entries, tableBox.x(), tableBox.y(),
            tableBox.width(), tableBox.bottom(), mouseX, mouseY, stops, presses);

        // After the table: the row under the cursor is only known once the rows
        // have been drawn.
        map.marks(graphics, mapBox, filters, hoveredStop);

        // Not drawn while Create's editor is up, since both draw at the same z
        // and equal-z ties go to whoever draws last; the stop stays remembered
        // so confirming a condition comes back here.
        if (conditionsFor >= entries.size())
            conditionsFor = -1;
        else if (conditionsFor >= 0 && !host.editorOpen())
            lines = RouteTable.conditions(graphics, font, entries.get(conditionsFor),
                editing(conditionsFor), this::close, screen.width, screen.height, mouseX, mouseY,
                overrides, alternatives);

        // Last, while the map is topmost: held from the drawing because the map
        // is scissored and a tooltip must not be.
        List<FormattedText> mapTip = map.tooltip();
        if (mapTip != null && conditionsFor < 0 && !host.editorOpen())
            graphics.renderTooltip(font, mapTip.stream()
                .map(Language.getInstance()::getVisualOrder)
                .toList(), mouseX, mouseY);
    }

    /**
     * What a click on one band of conditions means: the route's own if
     * {@code stop} is {@link RouteTable#DEFAULTS}, and a stop's override
     * otherwise.
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

            // One alternative at a time, the way Create's arrows move; where to
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

    /** Carrying a stop to somewhere else in the list; the list is reordered as the cursor passes each row rather than once at the end. */
    private Action grip(int index, ScrollTable rows) {
        List<ScheduleEntry> entries = host.entries();
        if (index >= entries.size())
            return null;

        return new Action() {

            /** Where it is now, which is not where it was picked up from. */
            private int at = index;

            /** No tooltip — a panel following the cursor would be in the way of the thing being carried. */
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

    /** Keeps a held press going, since {@code ScheduleScreen} declares no {@code mouseDragged} and a mixin cannot inject into a method its target does not declare. */
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

    /** The button onto a stop's own conditions, or nothing where there are none to have. */
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
                // Named after the window it opens rather than Create's "add
                // condition"; this is also the only place the override star gets
                // explained, since it belongs to no rectangle of its own.
                if (entry.conditions.isEmpty())
                    tipOf(graphics, mouseX, mouseY, "create_routes.route.window.conditions");
                else
                    tips(graphics, mouseX, mouseY,
                        Component.translatable("create_routes.route.window.conditions"),
                        Component.translatable("create_routes.route.overridden")
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

    /** Done with the route, which is what Create's own tick did: closing is what saves, so there is nothing here to hold back until it. */
    private boolean leave(GuiGraphics graphics, double mouseX, double mouseY, int click) {
        if (click == 0)
            back();
        return true;
    }

    /** Out of this route: to the one that opened it, or out of the editor at the top. */
    private void back() {
        if (!RouteTrail.leave())
            Minecraft.getInstance().player.closeContainer();
    }

    /** Over to the list of routes, to open a different one in this one's place. */
    private boolean manage(GuiGraphics graphics, double mouseX, double mouseY, int click) {
        // Sideways, not out: the trail is left alone, so the way back out is
        // still the way this one came.
        if (click == 0)
            Minecraft.getInstance()
                .setScreen(new RouteListScreen());
        else if (click != 1)
            tipOf(graphics, mouseX, mouseY, "create_routes.route.window.manage");
        return true;
    }

    /** Opens the route a stop follows, remembering this one to come back to. */
    private Action open(UUID nested) {
        return (graphics, mouseX, mouseY, click) -> {
            if (click == 0) {
                RouteTrail.push(route);
                CrPackets.CHANNEL.sendToServer(new RouteEditPacket(nested));
            } else if (click != 1)
                tipOf(graphics, mouseX, mouseY, "create_routes.route.list.open");
            return true;
        };
    }

    /**
     * What a click on the table does, in place of what a click on a card did.
     *
     * <p>Create calls this twice per position — once from
     * {@code renderForeground} with {@code click == -1} for a tooltip only,
     * and once from {@code mouseClicked} with the real button.
     */
    public boolean action(GuiGraphics graphics, double mouseX, double mouseY, int click) {
        if (host.editorOpen())
            return false;

        // The stops table answers from its own boxes, so nothing scrolled out
        // of it can be hit.
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
            // While the conditions are up, a miss simply swallows the click —
            // there is a footer button to close it now.
            if (conditionsFor >= 0)
                return true;
            // A click on our layout that hit nothing still reaches the
            // container behind, as a click on no slot; the footer is
            // deliberately not swallowed, since Create only offers the confirm
            // widget the click after this returns false.
            return onLayout(mouseX, mouseY);
        }
        return action.act(graphics, mouseX, mouseY, click);
    }

    /** A stop opens its own instruction, the way Create's destination field does. */
    private void stop(GuiGraphics graphics, double mouseX, double mouseY, int click, ScheduleEntry entry) {
        if (click == 0) {
            // Also centres the map on where it is.
            map.look(Stations.meant(entry.instruction));
            host.startEditing(entry.instruction, confirmed -> {
                if (confirmed)
                    entry.instruction = host.editedInstruction();
            }, false);
        } else if (click != 1)
            titled(graphics, mouseX, mouseY, entry.instruction, "instruction", false, true);
    }

    /** Which list a drawn condition came out of: the route's own, or a stop's. */
    private List<List<ScheduleWaitCondition>> columnsOf(int stop) {
        return stop == RouteTable.DEFAULTS ? defaults : host.entries()
            .get(stop).conditions;
    }

    /**
     * A condition, edited or removed the way Create's own card does it, except
     * that Create's rule against removing the last condition holds only for
     * the route's own defaults — for a stop's override, empty means back to
     * the route's defaults, which is the whole point of removing it.
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
     * <p>Assembled here rather than called, since {@code renderActionTooltip}
     * and its two ready-made lines are private to the screen.
     */
    private void titled(GuiGraphics graphics, double mouseX, double mouseY, IScheduleInput field, String as,
        boolean deletable, boolean draggable) {
        if (graphics == null)
            return;
        List<Component> tooltip = new ArrayList<>(field.getTitleAs(as));
        tooltip.add(CommonComponents.EMPTY);
        tooltip.add(createHint("gui.schedule.lmb_edit"));
        // Only a stop is carried anywhere; a condition sits in its column with
        // no order of its own to change.
        if (draggable)
            tooltip.add(hint("create_routes.route.reorder"));
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

    /** A station if the cursor is on one, and the map itself otherwise. */
    public boolean grab(double mouseX, double mouseY, int button) {
        if (!idleOverMap(mouseX, mouseY))
            return false;
        return map.grab(mouseX, mouseY, button);
    }

    /** The wheel, over whichever of the three things is under it. */
    public boolean wheel(double mouseX, double mouseY, double delta) {
        if (host.editorOpen())
            return false;
        if (conditionsFor >= 0)
            return overrides.wheel(delta);
        if (idleOverMap(mouseX, mouseY))
            return map.zoom(delta);
        if (tableBox != null && tableBox.holds(mouseX, mouseY))
            return stops.wheel(delta);
        // Across the whole window rather than only its body: the wheel is aimed
        // at a column of conditions, and the rim is not somewhere to have to miss.
        int x = layout().mapWindow().x();
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
        // At the top it is left alone — that's not the same as no route above
        // this one, since a schedule may be down there.
        if (!RouteTrail.leadsBack())
            return false;
        back();
        return true;
    }

    /** Tells JEI where our windows are, so it stops drawing its item list over them — Create reports the small area beside its panel, ours is the screen. */
    public List<Rect2i> areas() {
        Layout layout = layout();
        Box mapWindow = layout.mapWindow();
        Box tableWindow = layout.tableWindow();
        return List.of(new Rect2i(mapWindow.x(), mapWindow.y(), LEFT, BODY),
            new Rect2i(tableWindow.x(), tableWindow.y(), tableWindow.width(), tableWindow.height()));
    }

    /** Anywhere the layout draws, minus the confirm plaque. */
    private boolean onLayout(double mouseX, double mouseY) {
        Layout layout = layout();
        Box mapWindow = layout.mapWindow();
        return mouseX >= mapWindow.x() && mouseX < layout.tableWindow().right() && mouseY >= mapWindow.y()
            && mouseY < mapWindow.y() + BODY;
    }

    /** On the map, and with nothing over it that the click belongs to instead. */
    private boolean idleOverMap(double mouseX, double mouseY) {
        if (host.editorOpen() || conditionsFor >= 0)
            return false;
        return layout().mapWindow()
            .holds(mouseX, mouseY);
    }

}
