package me.xiaoeyun.createroutes.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.lwjgl.glfw.GLFW;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;


import me.xiaoeyun.createroutes.content.route.ClientRoutes;
import me.xiaoeyun.createroutes.content.route.Route;
import me.xiaoeyun.createroutes.network.CrPackets;
import me.xiaoeyun.createroutes.network.RouteEditPacket;
import me.xiaoeyun.createroutes.network.RouteManagePacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Every route in the world, and the four things you can do to the set of them. */
public class RouteListScreen extends Screen {

    /** Create's own schedule panel, whole — every offset below is one {@code ScheduleScreen} already uses. */
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

    /** What the cursor is over, or null — held until the frame's end because the rows are drawn inside a scissor the tooltip must not be clipped by. */
    private String hovered;

    /** The routes as they were last drawn, in that order — a click is answered against this rather than a fresh map. */
    private final List<Map.Entry<UUID, String>> shown = new ArrayList<>();

    /** Which route was being renamed when this click arrived, if any. */
    private UUID committed;

    /** Where the rows are, which is the only record of it — kept between frames since a click arrives between them. */
    private final ScrollTable list = new ScrollTable(CtSkin.ROW_HEIGHT, new Routes(), scroll);

    public RouteListScreen() {
        super(Component.translatable("create_routes.route.list.title"));
    }

    private int left() {
        return (width - PANEL.getWidth()) / 2;
    }

    private int top() {
        return (height - PANEL.getHeight()) / 2;
    }

    /** The list area, in screen coordinates: where Create scissors its cards to. */
    private int listX() {
        return left() + CtSkin.LIST_AT;
    }

    private int listY() {
        return top() + CtSkin.LIST_AT;
    }

    @Override
    protected void init() {
        // Made once and moved, never rebuilt — init() reruns on a resize, and a
        // fresh field would drop what was half typed.
        if (field == null) {
            // Not zero: EditBox scrolls its value to fit the width it has when
            // set, and at zero width the name would come up blank.
            field = new EditBox(font, 0, 0, CtSkin.LIST_WIDTH, 10, CommonComponents.EMPTY);
            field.setBordered(false);
            field.setMaxLength(Route.MAX_NAME_LENGTH);
            field.setTextColor(CtSkin.FIELD_TEXT);
            field.setHint(Component.translatable("create_routes.route.list.hint"));
        }
        field.visible = editing != null;
        addRenderableWidget(field);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        renderBackground(graphics);

        CtSkin.schedulePanel(graphics, font, title, width, height, mouseX, mouseY);

        list(graphics, listX(), listY(), CtSkin.LIST_WIDTH, listY() + CtSkin.LIST_HEIGHT, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (hovered != null)
            graphics.renderTooltip(font, Component.translatable(hovered), mouseX, mouseY);
    }

    private void list(GuiGraphics graphics, int x, int y, int width, int bottom, int mouseX, int mouseY) {
        hovered = null;
        shown.clear();
        shown.addAll(ClientRoutes.all()
            .entrySet());

        // One past the routes, for the row that makes another.
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

    /** One route per row, and one more row that makes another. */
    private class Routes implements ScrollTable.Row {

        @Override
        public void paint(GuiGraphics graphics, Font font, int index, Box at, boolean hovered,
            double mouseX, double mouseY) {
            UUID route = route(index);
            int strip = strip(at);
            int name = strip - at.x() - 10;

            // The field is drawn over the row instead, so the old name isn't
            // drawn under what is being typed.
            if (route.equals(editing))
                place(at.x() + 8, at.y(), name);
            else if (NEW.equals(route))
                graphics.drawString(font, Component.translatable("create_routes.route.list.new"),
                    at.x() + 8, at.y() + RouteTable.TEXT, CtSkin.MUTED_TEXT, false);
            else
                CtSkin.clipped(graphics, font, Component.literal(shown.get(index)
                    .getValue()), at.x() + 8, at.y() + RouteTable.TEXT, name, CtSkin.FIELD_TEXT);

            if (NEW.equals(route))
                return;

            Strip buttons = Strip.startingAt(graphics, strip, at.y());

            int slot = hovered ? Strip.slotAt(strip, SLOTS, mouseX) : -1;
            boolean armed = route.equals(confirming);
            if (slot >= 0)
                RouteListScreen.this.hovered = slot == 0 ? "create_routes.route.list.open"
                    : armed ? "create_routes.route.list.delete.confirm"
                        : "create_routes.route.list.delete";

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
                    // whichever one was open when the list was reached.
                    CrPackets.CHANNEL.sendToServer(new RouteEditPacket(route));
                    return true;
                };
                case 1 -> (graphics, mouseX, mouseY, click) -> {
                    if (!route.equals(confirming)) {
                        confirming = route;
                        return true;
                    }
                    CrPackets.CHANNEL.sendToServer(RouteManagePacket.delete(route));
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

    /** Puts the field over the row it belongs to, which is the whole of its layout. */
    private void place(int x, int y, int width) {
        field.setX(x);
        field.setY(y + RouteTable.TEXT);
        field.setWidth(width);
        field.visible = true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Clicking anywhere but the field commits what's in it, same as leaving a field.
        UUID was = editing;
        if (was != null && !field.isMouseOver(mouseX, mouseY))
            commit();

        if (super.mouseClicked(mouseX, mouseY, button))
            return true;
        if (button != 0)
            return false;

        // The tick is painted into the sheet; only the target around it is ours.
        if (CtSkin.confirm(left(), top())
            .holds(mouseX, mouseY)) {
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

    /** Sends what was typed, if it says anything and says something new. */
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
            CrPackets.CHANNEL.sendToServer(RouteManagePacket.create(wanted));
        else if (!wanted.equals(ClientRoutes.all()
            .get(route)))
            CrPackets.CHANNEL.sendToServer(RouteManagePacket.rename(route, wanted));
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

    /** Back to whatever led here, if anything did. */
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
