package me.xiaoeyun.createtransit.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;


import me.xiaoeyun.createtransit.content.route.ClientRoutes;
import me.xiaoeyun.createtransit.content.route.Route;
import me.xiaoeyun.createtransit.network.CtPackets;
import me.xiaoeyun.createtransit.network.RouteEditPacket;
import me.xiaoeyun.createtransit.network.RouteManagePacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Every route in the world, and the four things you can do to the set of them.
 *
 * <p>Ours rather than borrowed, unlike the editor: a list of names needs none of
 * {@code ScheduleScreen} — no instruction editor, no condition editor, no ghost
 * slots — so there is nothing to borrow and nothing an addon would want to hang
 * off it. Which also means every widget here is an ordinary widget, added the
 * ordinary way.
 *
 * <p>One field, moved to whichever row is being typed into, rather than a field
 * per row or a field in a footer. Per row it would have to be rebound every time
 * the list scrolls, and scrolling while typing would hand what is being typed to
 * a different route. In a footer it would need a button and a chosen row to say
 * which route it meant. Moved, it is always over the name it is editing, and the
 * empty row at the end makes a route for the same reason a blank name is not one
 * that exists yet.
 *
 * <p>Reads {@link ClientRoutes} afresh every frame instead of taking a copy.
 * Creating and deleting are round trips, so the list changes underneath this
 * screen when the server answers, and a copy would need telling.
 */
public class RouteListScreen extends Screen {

    /**
     * Create's own schedule panel, whole. The title plaque, the checkered field
     * and the footer with its confirm tick are painted into it already, and this
     * screen wants all three — so the picture is the layout, and every offset
     * below is one {@code ScheduleScreen} already uses.
     */
    private static final AllGuiTextures PANEL = AllGuiTextures.SCHEDULE;

    /** Stands for the empty row at the end, which belongs to no route yet. */
    private static final UUID NEW = new UUID(0, 0);

    private final Scroll scroll = new Scroll();

    /** Which route's name is being typed, {@link #NEW} for the last row, or null. */
    private UUID editing;

    private EditBox field;

    /** Which route's delete has been pressed once. */
    private UUID confirming;

    /** How many buttons a route's row ends with: open it, and remove it. */
    private static final int SLOTS = 2;

    /**
     * What the cursor is over, or null. Held from the drawing to the end of the
     * frame because a tooltip has to be the last thing on screen, and the rows
     * are drawn inside a scissor that a tooltip must not be clipped by.
     */
    private String hovered;

    /**
     * The routes as they were last drawn, in the order they were drawn in. A
     * click is answered against this rather than against a fresh map, so that
     * what was hit is what was on screen.
     */
    private final List<Map.Entry<UUID, String>> shown = new ArrayList<>();

    /** Which route was being renamed when this click arrived, if any. */
    private UUID committed;

    /**
     * Where the rows are, which is the only record of it. Kept between frames
     * because a click arrives between them, and the boxes in here are where the
     * last frame actually put things — including how far the easing had got.
     */
    private final ScrollTable list = new ScrollTable(CtSkin.ROW_HEIGHT, new Routes(), scroll);

    public RouteListScreen() {
        super(Component.translatable("create_transit.route.list.title"));
    }

    private int left() {
        return (width - PANEL.getWidth()) / 2;
    }

    private int top() {
        return (height - PANEL.getHeight()) / 2;
    }

    /** The list area, in screen coordinates: where Create scissors its cards to. */
    private int listX() {
        return left() + RouteTable.LIST_AT;
    }

    private int listY() {
        return top() + RouteTable.LIST_AT;
    }

    @Override
    protected void init() {
        // Made once and moved, never rebuilt: init runs again on a resize, and a
        // fresh field would drop what was half typed.
        if (field == null) {
            // Not zero, though {@link #place} sets the real one before it is ever
            // drawn. EditBox works out how far a value is scrolled the moment it
            // is set, against whatever width it has at that moment — at zero the
            // inner width is negative, the whole name counts as scrolled past,
            // and the first name a player opens comes up blank.
            field = new EditBox(font, 0, 0, RouteTable.LIST_WIDTH, 10, CommonComponents.EMPTY);
            field.setBordered(false);
            field.setMaxLength(Route.MAX_NAME_LENGTH);
            field.setTextColor(CtSkin.FIELD_TEXT);
            field.setHint(Component.translatable("create_transit.route.list.hint"));
        }
        field.visible = editing != null;
        addRenderableWidget(field);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(graphics);

        int x = left();
        int y = top();
        PANEL.render(graphics, x, y);
        graphics.drawString(font, title, x + (PANEL.getWidth() - 8) / 2 - font.width(title) / 2, y + 4,
            CtSkin.PLAQUE_TEXT, false);

        // The socket at the far end of the footer holds Create's cyclic button,
        // which a list of routes has no use for. It is painted on, so it has to
        // be filled back in with the face around it.
        graphics.fill(x + RouteTable.CYCLIC_X, y + RouteTable.CYCLIC_Y,
            x + RouteTable.CYCLIC_X + RouteTable.BUTTON_SIZE,
            y + RouteTable.CYCLIC_Y + RouteTable.BUTTON_SIZE, CtSkin.PLAQUE);

        list(graphics, listX(), listY(), RouteTable.LIST_WIDTH, listY() + RouteTable.LIST_HEIGHT, mouseX,
            mouseY);

        // Over the tick the sheet already has painted on it. Painted, it could
        // not light up under the cursor — and a button that never answers reads
        // as decoration until it is tried.
        Strip.plate(graphics, x + RouteTable.CONFIRM_X, y + RouteTable.CONFIRM_Y, AllIcons.I_CONFIRM,
            over(mouseX, mouseY, x + RouteTable.CONFIRM_X, y + RouteTable.CONFIRM_Y));

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (hovered != null)
            graphics.renderTooltip(font, Component.translatable(hovered), mouseX, mouseY);
    }

    private void list(GuiGraphics graphics, int x, int y, int width, int bottom, int mouseX, int mouseY) {
        hovered = null;
        shown.clear();
        shown.addAll(ClientRoutes.all()
            .entrySet());

        // One past the routes for the row that makes another, which is how the
        // stops table spells the same thing.
        list.rows(shown.size() + 1);
        list.arrange(new Box(x, y, width, bottom - y));
        list.paint(graphics, font, mouseX, mouseY);
    }

    /** Which route a row stands for; the one past the end makes another. */
    private UUID route(int index) {
        return index < shown.size() ? shown.get(index)
            .getKey() : NEW;
    }

    /** Where a row's buttons begin, which is where its name has to stop. */
    private static int strip(Box row) {
        return row.right() - Strip.width(SLOTS);
    }

    /**
     * One route per row, and one more row that makes another.
     *
     * <p>Drawing and clicking are the two halves of the same shape here, which
     * is why they are one class: the slot the cursor is in decides the tooltip,
     * the red behind an armed delete, and what a press does, and all three used
     * to work it out for themselves.
     */
    private class Routes implements ScrollTable.Row {

        @Override
        public void paint(GuiGraphics graphics, Font font, int index, Box at, boolean hovered,
            double mouseX, double mouseY) {
            UUID route = route(index);
            int strip = strip(at);
            int name = strip - at.x() - 10;

            // The field is drawn over the row instead, and drawing both would put
            // the old name under what is being typed.
            if (route.equals(editing))
                place(at.x() + 8, at.y(), name);
            else if (NEW.equals(route))
                graphics.drawString(font, Component.translatable("create_transit.route.list.new"),
                    at.x() + 8, at.y() + RouteTable.TEXT, CtSkin.MUTED_TEXT, false);
            else
                CtSkin.clipped(graphics, font, Component.literal(shown.get(index)
                    .getValue()), at.x() + 8, at.y() + RouteTable.TEXT, name, CtSkin.FIELD_TEXT);

            if (NEW.equals(route))
                return;

            Strip buttons = Strip.endingAt(graphics, at.right(), at.y(), SLOTS);

            int slot = hovered ? Strip.slotAt(strip, SLOTS, mouseX) : -1;
            boolean armed = route.equals(confirming);
            if (slot >= 0)
                RouteListScreen.this.hovered = slot == 0 ? "create_transit.route.list.open"
                    : armed ? "create_transit.route.list.delete.confirm"
                        : "create_transit.route.list.delete";

            // The first press only arms it; moving off the button puts it back.
            if (armed && slot != 1)
                confirming = null;
            else if (armed)
                graphics.fill(strip + Strip.SLOT, at.y(), strip + Strip.SLOT * 2, at.bottom(),
                    0x80D03030);

            buttons.button(AllIcons.I_VIEW_SCHEDULE)
                .button(AllIcons.I_DISABLE);
        }

        @Override
        public Action hit(ScrollTable rows, int index, Box at, double x, double y) {
            UUID route = route(index);
            if (NEW.equals(route))
                return (graphics, mouseX, mouseY, click) -> {
                    start(route, "");
                    return true;
                };

            return switch (Strip.slotAt(strip(at), SLOTS, x)) {
                // Leaves for the editor, which the server hands out as a menu — so
                // this screen goes away when that arrives rather than here.
                case 0 -> (graphics, mouseX, mouseY, click) -> {
                    // The trail is left as it is: this route takes the place of
                    // whichever one was open when the list was reached, and the
                    // way back out of it is the way that one came.
                    CtPackets.CHANNEL.sendToServer(new RouteEditPacket(route));
                    return true;
                };
                case 1 -> (graphics, mouseX, mouseY, click) -> {
                    if (!route.equals(confirming)) {
                        confirming = route;
                        return true;
                    }
                    CtPackets.CHANNEL.sendToServer(RouteManagePacket.delete(route));
                    confirming = null;
                    return true;
                };
                // Clicking the name a second time is what starts editing it; the
                // click that closed the last one has already been spent.
                default -> (graphics, mouseX, mouseY, click) -> {
                    if (!route.equals(committed))
                        start(route, ClientRoutes.all()
                            .getOrDefault(route, ""));
                    return true;
                };
            };
        }
    }

    /** Whether the cursor is on one of the sheet's painted button sockets. */
    private static boolean over(double mouseX, double mouseY, int x, int y) {
        return new Box(x, y, RouteTable.BUTTON_SIZE, RouteTable.BUTTON_SIZE).holds(mouseX, mouseY);
    }

    /** Puts the field over the row it belongs to, which is the whole of its layout. */
    private void place(int x, int y, int width) {
        field.setX(x);
        field.setY(y + RouteTable.TEXT);
        field.setWidth(width);
        field.visible = true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clicking anywhere that is not the field commits what is in it, the way
        // leaving a field commits it everywhere else.
        UUID was = editing;
        if (was != null && !field.isMouseOver(mouseX, mouseY))
            commit();

        if (super.mouseClicked(mouseX, mouseY, button))
            return true;
        if (button != 0)
            return false;

        // The tick is painted into the sheet, so only the target around it is
        // ours. Closing is all it does — nothing here is held until confirmed.
        if (over(mouseX, mouseY, left() + RouteTable.CONFIRM_X, top() + RouteTable.CONFIRM_Y)) {
            onClose();
            return true;
        }

        // Against the boxes the last frame drew, so a click lands on what it
        // looks like it hit — including part way through the easing.
        committed = was;
        Action action = list.hit(mouseX, mouseY);
        return action != null && action.act(null, mouseX, mouseY, button);
    }

    private void start(UUID route, String value) {
        editing = route;
        field.setValue(value);
        field.moveCursorToEnd();
        field.visible = true;
        setFocused(field);
        field.setFocused(true);
    }

    /**
     * Sends what was typed, if it says anything and says something new.
     *
     * <p>A blank name is not a refusal to be handled, it is the row as it was
     * found — and renaming a route to what it is already called is a packet that
     * would change nothing.
     */
    private void commit() {
        UUID route = editing;
        String wanted = field.getValue()
            .trim();
        editing = null;
        field.visible = false;
        setFocused(null);
        if (route == null || wanted.isEmpty())
            return;

        if (NEW.equals(route))
            CtPackets.CHANNEL.sendToServer(RouteManagePacket.create(wanted));
        else if (!wanted.equals(ClientRoutes.all()
            .get(route)))
            CtPackets.CHANNEL.sendToServer(RouteManagePacket.rename(route, wanted));
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (editing != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commit();
                return true;
            }
            // Escape abandons the edit before it abandons the screen, which is
            // the only way back out of a name half changed.
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                editing = null;
                field.visible = false;
                setFocused(null);
                return true;
            }
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return scroll.wheel(delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * Back to whatever led here, if anything did.
     *
     * <p>The tick and Escape both come through here, and both mean the same
     * thing: done with the list. Done with it is not done altogether when a
     * route or a schedule is waiting underneath — this screen is a step in that
     * trip, not the end of it.
     */
    @Override
    public void onClose() {
        if (!RouteTrail.leave())
            super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
