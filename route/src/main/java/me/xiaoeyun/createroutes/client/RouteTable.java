package me.xiaoeyun.createroutes.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;

import net.createmod.catnip.gui.UIRenderHelper;
import net.createmod.catnip.gui.element.GuiGameElement;

import me.xiaoeyun.createroutes.content.route.RouteReference;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A schedule's stops as one line each, in place of Create's cards. */
public class RouteTable {

    /** Heading text, a clear pixel, then the rule under it. */
    public static final int HEADING = 13;

    /** The gap between a row's text and the buttons that act on it, which is also where the override star stands. */
    public static final int MARK = 8;

    /** A row's icon column: a pixel of relief, the 16 square icon, and the gap before the text. */
    public static final int ICON_PAD = 2;

    public static final int ICON = ICON_PAD + 16 + 2;

    /** One condition field and the gap under it, spaced the way Create spaces its own. */
    public static final int CONDITION_ROW = 18;

    /** The gap between two alternatives, Create's own. */
    private static final int COLUMN_GAP = 10;

    /** The narrowest a condition field is drawn, and the widest it may grow to. */
    private static final int CHIP_MIN = 32;

    private static final int CHIP_MAX = 150;

    /** What Create writes on one, which is not quite white. */
    private static final int CHIP_TEXT = 0xFFF2F2EE;

    /** Above the schedule's item icons at z 150 and below tooltips at z 400; items are batched by render type, so a later fill would not otherwise beat an earlier one. */
    private static final int OVER = 200;

    /** Create's card header, which holds what the stop does. */
    private static final int HEADER = 22;

    /** Where a card's content starts, past the spine down its left edge. */
    private static final int CONTENT = 26;

    /** Where that spine is, measured from the card's left edge. */
    private static final int SPINE = 8;

    /** Create's own card width; every measurement in {@code ScheduleScreen} is relative to it. */
    private static final int CARD_WIDTH = 195;

    /** Where a card sits on {@code AllGuiTextures.SCHEDULE}, taken from {@code ScheduleScreen}. */
    private static final int CARD_AT = 25;

    /** Where the spine down its side sits, also taken from {@code ScheduleScreen}. */
    private static final int SPINE_AT = 33;

    /** How many buttons a stop's row ends with: its conditions, a copy of it, and its removal. */
    public static final int SLOTS = 3;

    /** The edge fades, over the fields and under the tooltips; relative, the popup already being drawn {@link #OVER} everything. */
    private static final int FADE = 10;

    /** Create's own shading, for the card, whose arrows stand clear of it. */
    private static final int FADE_CARD = 0x44000000;

    /** And the route window's, which is darker — its band has no gutter, so its arrows lie on the fields themselves. */
    private static final int FADE_BAND = 0x88000000;

    /** How far the route window holds its content off its own edges: the name chip either side, and the band's content at both ends. */
    private static final int INSET = 4;

    /** Create's scroll arrows; the two are the same size, so either measures both. */
    private static final AllGuiTextures ARROW = AllGuiTextures.SCHEDULE_SCROLL_LEFT;

    /** One more condition this column must also wait for, and one more column. */
    private static final AllGuiTextures APPEND = AllGuiTextures.SCHEDULE_CONDITION_APPEND;

    private static final AllGuiTextures NEW = AllGuiTextures.SCHEDULE_CONDITION_NEW;

    /** Where 8 pixels of text sit in a row taller than they are. */
    public static final int TEXT = (CtSkin.ROW_HEIGHT - 8) / 2;

    /** And where a 16 pixel icon sits in the same row. */
    public static final int ICON_INSET = (CtSkin.ROW_HEIGHT - 16) / 2;

    /** A stop answering for its own conditions instead of taking the route's. */
    private static final String OVERRIDDEN = "*";

    /** Stands where a stop's index would, for the route's own conditions — negative because -1 already means "none". */
    public static final int DEFAULTS = -2;

    /** What the route's own card carries instead of an instruction. */
    private static final ItemStack ROUTE_ICON = AllItems.SCHEDULE.asStack();

    /** What a click on a band of conditions means, which belongs to whoever owns the conditions rather than to whoever drew them. */
    public interface Conditions {

        Action condition(int column, int row);

        /** One more condition in a column the train must also wait for. */
        Action add(int column);

        /** One more column: a second way for the train to be let go. */
        Action alternative();

        /** @param target where the band should scroll to, in pixels */
        Action scroll(int target);
    }

    /** Where a band's two scroll arrows sit, and how far down. */
    private record Arrows(int left, int right, int y) {}

    /**
     * What a click on a row of stops means, which belongs to whoever owns the
     * data rather than to whoever drew it.
     *
     * @param slot which of the row's buttons, or -1 for the row itself
     */
    public interface Stops {

        Action at(int index, int slot);

        /** A press on the row's icon, which is what carries it somewhere else. */
        Action grip(int index, ScrollTable rows);

        /** Said as a row is drawn under the cursor, so that the map can mark the stations it means. */
        void over(int index);

        /** Whether the map's cursor is on a station this stop means. */
        boolean lit(int index);
    }

    /** One drawn rectangle and what answers for it. */
    public record Line(Box at, Action action) {

        Line(int x, int y, int width, int height, Action action) {
            this(new Box(x, y, width, height), action);
        }
    }

    private RouteTable() {}

    /** Draws the table and returns every rectangle that can be clicked. */
    public static ScrollTable render(GuiGraphics graphics, Font font, List<ScheduleEntry> entries, int x,
        int y, int width, int bottom, double mouseX, double mouseY, Scroll scroll, Stops stops) {
        // Where the text of a row may run: past the icon, short of the buttons and their mark.
        int textX = x + ICON;
        int textEnd = x + width - Strip.width(SLOTS) - MARK;
        int actions = actionColumn(font, entries, textEnd - textX);

        graphics.drawString(font, heading("create_routes.route.column.action"), textX, y + 2,
            CtSkin.FIELD_TEXT, false);
        graphics.drawString(font, heading("create_routes.route.column.stop"), textX + actions, y + 2,
            CtSkin.FIELD_TEXT, false);
        CtSkin.rule(graphics, x, y + HEADING - 1, width);

        int top = y + HEADING;
        int buttons = textEnd + MARK;

        // One row past the stops for the line that adds another.
        ScrollTable window = new ScrollTable(CtSkin.ROW_HEIGHT, new ScrollTable.Row() {

            @Override
            public void paint(GuiGraphics graphics, Font font, int index, Box at, boolean hovered,
                double mouseX, double mouseY) {
                if (index == entries.size()) {
                    graphics.drawString(font, Component.translatable("create_routes.route.add_stop"),
                        textX, at.y() + TEXT, CtSkin.MUTED_TEXT, false);
                    AllIcons.I_ADD.render(graphics, at.x() + ICON_PAD, at.y() + ICON_INSET);
                    return;
                }

                if (hovered)
                    stops.over(index);

                ScheduleEntry entry = entries.get(index);
                graphics.renderItem(entry.instruction.getSummary()
                    .getFirst(), at.x() + ICON_PAD, at.y() + ICON_INSET);
                CtSkin.clipped(graphics, font, action(entry), textX, at.y() + TEXT, actions - 4,
                    CtSkin.MUTED_TEXT);
                CtSkin.clipped(graphics, font, entry.instruction.getSummary()
                    .getSecond(), textX + actions, at.y() + TEXT, textEnd - textX - actions,
                    CtSkin.FIELD_TEXT);

                buttons(graphics, font, entry, buttons, at.y());
            }

            @Override
            public Action hit(ScrollTable rows, int index, Box at, double hitX, double hitY) {
                if (hitX < textX)
                    return stops.grip(index, rows);
                return stops.at(index, Strip.slotAt(buttons, SLOTS, hitX));
            }
        }, scroll).rows(entries.size() + 1)
            .lit(stops::lit);

        window.arrange(new Box(x, top, width, bottom - top));
        window.paint(graphics, font, mouseX, mouseY);
        return window;
    }

    /** The three things a row can have done to it, drawn as Create's own card glyphs since these are Create's own operations. */
    private static void buttons(GuiGraphics graphics, Font font, ScheduleEntry entry, int x, int y) {
        Strip strip = Strip.startingAt(graphics, x, y);

        // A nested route has no conditions of its own, so its slot opens the
        // route instead; an instruction with none leaves the slot blank so the
        // buttons after it do not shuffle.
        boolean nested = RouteReference.of(entry.instruction) != null;
        if (nested)
            strip.button(AllIcons.I_VIEW_SCHEDULE);
        else if (entry.instruction.supportsConditions()) {
            strip.button(AllIcons.I_CONFIG_OPEN);
            if (!entry.conditions.isEmpty())
                graphics.drawString(font, OVERRIDDEN, x - 6, y + TEXT, CtSkin.FIELD_TEXT, false);
        } else
            strip.blank();

        // I_FX_BLEND_OFF and I_DISABLE stand in for copy and delete; the atlas has neither.
        strip.button(AllIcons.I_FX_BLEND_OFF);
        strip.button(AllIcons.I_DISABLE);
    }

    /**
     * One stop's wait conditions, over everything else — columns are
     * alternatives (any one satisfied lets the train go) and within a column
     * every condition must hold.
     */
    public static List<Line> conditions(GuiGraphics graphics, Font font, ScheduleEntry entry,
        Conditions of, Action close, int screenWidth, int screenHeight, double mouseX,
        double mouseY, Scroll down, Scroll across) {
        List<List<ScheduleWaitCondition>> columns = entry.conditions;

        int cardWidth = CARD_WIDTH;
        // A floor Create's own card never needs: alone on a panel, one row reads as cramped.
        int cardHeight = Math.max(HEADER + 32, HEADER + 24 + rowsIn(columns) * CONDITION_ROW);
        // Create's list area is what the card scrolls inside; the card starts
        // nine pixels into it and the content extends that much past its end, so
        // the last row clears the bottom by the same margin it clears the top.
        int inset = CARD_AT - CtSkin.LIST_AT;
        int offset = down.at(inset + cardHeight + inset, CtSkin.LIST_HEIGHT);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, OVER);

        // Dimmed the way Create dims the screen behind its own editor, so the
        // two read as the same kind of thing.
        graphics.fill(0, 0, screenWidth, screenHeight, 0xB0000000);
        Box panel = CtSkin.schedulePanel(graphics, font,
            Component.translatable("create_routes.route.window.conditions"), screenWidth, screenHeight,
            mouseX, mouseY);
        int x = panel.x();
        int y = panel.y();

        int cardX = x + CARD_AT;
        int cardY = y + CARD_AT - offset;

        graphics.enableScissor(x + CtSkin.LIST_AT, y + CtSkin.LIST_AT, x + CtSkin.LIST_AT + CtSkin.LIST_WIDTH,
            y + CtSkin.LIST_AT + CtSkin.LIST_HEIGHT);
        card(graphics, cardX, cardY, cardWidth, cardHeight);

        // Drawn after the plate, not before — Create draws this inside
        // {@code renderScheduleEntry} once the plate's fills are down, and
        // drawing it first would bury it under the card.
        UIRenderHelper.drawStretched(graphics, cardX + SPINE, cardY, 3, cardHeight + 10, 0,
            AllGuiTextures.SCHEDULE_STRIP_LIGHT);
        AllGuiTextures.SCHEDULE_STRIP_TRAVEL.render(graphics, cardX + 4, cardY + 6);
        AllGuiTextures.SCHEDULE_STRIP_WAIT.render(graphics, cardX + 4, cardY + 28);

        // The darker second line runs the length of the list rather than of a
        // card, and is drawn above them all.
        UIRenderHelper.drawStretched(graphics, x + SPINE_AT, y + CtSkin.LIST_AT, 3, CtSkin.LIST_HEIGHT, FADE,
            AllGuiTextures.SCHEDULE_STRIP_DARK);

        // 100 is Create's own minimum for this field; the card's whole width
        // instead pinned every summary at the 150 cap.
        chip(graphics, font, entry.instruction.getSummary(), cardX + CONTENT, cardY + 5, false, 100,
            CHIP_MAX);
        entry.instruction.renderSpecialIcon(graphics, cardX + CONTENT + 4, cardY + 5);

        List<Line> lines = new ArrayList<>();
        // The fade and arrows are centred on the card's field rather than the
        // conditions in it, so a tall column scrolling cannot carry them out of view.
        int inner = cardWidth - CONTENT - 16;
        int clipped = cardX + CONTENT - 3;
        band(graphics, font, columns, of, cardX + CONTENT, cardY + 29, inner, cardHeight - 29, 0,
            new Arrows(clipped - 5 - ARROW.getWidth(), clipped + 3 + inner,
                cardY + (HEADER + cardHeight - ARROW.getHeight()) / 2),
            new Box(x + CtSkin.LIST_AT, y + CtSkin.LIST_AT, CtSkin.LIST_WIDTH, CtSkin.LIST_HEIGHT), across,
            lines);
        fades(graphics, cardX + CONTENT, inner, cardY + HEADER + 2, cardY + cardHeight - 2, FADE_CARD,
            across);

        graphics.disableScissor();

        // The confirm plate is already painted into the sheet; this only adds
        // the hit target for it.
        lines.add(new Line(CtSkin.confirm(x, y), close));

        pose.popPose();
        return lines;
    }

    /**
     * The route's own conditions, in its window instead of over the screen —
     * the conditions every stop that declares none of its own falls back to.
     */
    public static List<Line> defaults(GuiGraphics graphics, Font font, String name,
        List<List<ScheduleWaitCondition>> columns, Conditions of, int x, int y, int width, int bottom,
        Scroll down, Scroll across) {
        // A row for the name, then one line per condition and one more for the
        // button that adds another.
        int tall = (rowsIn(columns) + 1) * CONDITION_ROW;
        int top = y - down.at(CONDITION_ROW + 4 + tall, bottom - y);

        // The whole width minus the three pixels a plate hangs its notch into;
        // no gutter for the arrows, which lie on the fields over the fades instead.
        int bandX = x + 3;
        int band = width - 3;

        graphics.enableScissor(x, y, x + width, bottom);

        // Floor and ceiling the same, so the name fills the band exactly; Create's
        // plates grow to fit and cap at 150, which is wider than this band.
        chip(graphics, font, Pair.of(ROUTE_ICON, Component.literal(name)), bandX + INSET, top + 2,
            false, band - INSET * 2, band - INSET * 2);

        List<Line> lines = new ArrayList<>();

        // The fade and arrows are measured off the panel's whole height, name
        // row included, since that row is content here (not chrome) and scrolls
        // with the rest.
        int rowTop = top + CONDITION_ROW + 4;
        band(graphics, font, columns, of, bandX, rowTop, band, tall, INSET,
            new Arrows(x, x + width - ARROW.getWidth(), (y + bottom - ARROW.getHeight()) / 2),
            new Box(x, y, width, bottom - y), across, lines);
        fades(graphics, bandX, band, y, bottom, FADE_BAND, across);

        graphics.disableScissor();
        return lines;
    }

    /** How many conditions the longest column holds. */
    private static int rowsIn(List<List<ScheduleWaitCondition>> columns) {
        int rows = 0;
        for (List<ScheduleWaitCondition> column : columns)
            rows = Math.max(rows, column.size());
        return rows;
    }

    /** Every alternative on a card, the buttons that add one, and the arrows that reach the ones off the edge. */
    private static void band(GuiGraphics graphics, Font font, List<List<ScheduleWaitCondition>> columns,
        Conditions of, int bandX, int rowTop, int band, int tall, int pad, Arrows arrows, Box view,
        Scroll across, List<Line> lines) {
        // What is drawn is cut twice — by the caller's window and by the band's
        // own scissor — so what is recorded must be cut by both.
        Box cut = new Box(bandX - 3, rowTop, band + 3, tall).within(view);

        // The last slot is the button that starts another column, laid out as if
        // it were one so that scrolling has no special case to make.
        int slots = columns.size() + 1;

        // A column is as wide as its widest condition, and they run sideways the
        // way Create's own do — the rest scrolled in with the arrows.
        int[] widths = new int[columns.size()];
        int[] starts = new int[columns.size()];
        int spread = NEW.getWidth();
        for (int column = 0; column < columns.size(); column++) {
            int wide = CHIP_MIN;
            for (ScheduleWaitCondition condition : columns.get(column))
                wide = Math.max(wide, fieldWidth(font, condition.getSummary(), CHIP_MIN, CHIP_MAX));
            widths[column] = wide;
            starts[column] = spread - NEW.getWidth();
            spread += wide + COLUMN_GAP;
        }

        // Inside the scrolling, not around it — added to what there is to
        // scroll rather than taken off the room, so the band keeps its whole
        // width.
        spread += pad * 2;

        int sideways = across.at(spread, band);

        // Three pixels of slack on the left, because a plate hangs its notch at
        // x - 3 and a scissor starting at x would shave it off only the first column.
        graphics.enableScissor(bandX - 3, rowTop, bandX + band, rowTop + tall);
        int columnX = bandX + pad - sideways;
        for (int column = 0; column < slots; column++) {
            int wide = column < columns.size() ? widths[column] : NEW.getWidth();
            // Scrolled out of the band: not drawn, and — because this is the
            // same pass — not clickable either.
            if (columnX + wide <= bandX || columnX >= bandX + band) {
                columnX += wide + COLUMN_GAP;
                continue;
            }

            if (column == columns.size()) {
                NEW.render(graphics, columnX - 3, rowTop);
                record(lines, cut, columnX, rowTop, wide, of.alternative());
                break;
            }

            List<ScheduleWaitCondition> conditions = columns.get(column);
            for (int row = 0; row < conditions.size(); row++) {
                ScheduleWaitCondition condition = conditions.get(row);
                int rowY = rowTop + row * CONDITION_ROW;
                // clean == row != 0: within a column every condition must hold,
                // and Create marks that by dropping the notch off the rows that
                // continue the one above.
                chip(graphics, font, condition.getSummary(), columnX, rowY, row != 0, wide, CHIP_MAX);
                condition.renderSpecialIcon(graphics, columnX + 4, rowY);
                record(lines, cut, columnX, rowY, wide, of.condition(column, row));
            }

            int appendY = rowTop + conditions.size() * CONDITION_ROW;
            APPEND.render(graphics, columnX + (wide - APPEND.getWidth()) / 2, appendY);
            // Clickable across the whole column, not just the glyph: nothing
            // else is on that line, and a 10 pixel target is a 10 pixel miss.
            record(lines, cut, columnX, appendY, wide, of.add(column));
            columnX += wide + COLUMN_GAP;
        }
        graphics.disableScissor();

        // Above the fades in z, not merely after them in draw order — a fade
        // fills at z FADE and a glyph blits at z 0, so either draw order would
        // otherwise bury the arrow beneath the shading.
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, FADE + 1);
        if (across.before())
            arrow(graphics, lines, true, of.scroll(before(starts, sideways)),
                AllGuiTextures.SCHEDULE_SCROLL_LEFT, arrows.left(), arrows.y());
        if (across.after())
            arrow(graphics, lines, false, of.scroll(after(starts, sideways, spread - band)),
                AllGuiTextures.SCHEDULE_SCROLL_RIGHT, arrows.right(), arrows.y());
        pose.popPose();
    }

    /** One condition-sized rectangle, cut to what is on screen, so nothing is recorded where nothing was drawn. */
    private static void record(List<Line> lines, Box cut, int x, int y, int width,
        Action action) {
        if (cut == null)
            return;
        Box box = new Box(x, y, width, CONDITION_ROW).within(cut);
        if (box != null)
            lines.add(new Line(box, action));
    }

    /**
     * The shading down either edge of a band, saying which way there is more.
     *
     * <p>Drawn sideways because {@code fillGradient} only interpolates down the
     * screen, so the pose is turned a quarter turn first.
     */
    private static void fades(GuiGraphics graphics, int bandX, int band, int top, int bottom, int shade,
        Scroll across) {
        if (!across.before() && !across.after())
            return;
        int tall = bottom - top;
        int clear = shade & 0x00FFFFFF;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(bandX, top, 0);
        TransformStack.of(pose)
            .rotateZDegrees(-90);
        if (across.before())
            graphics.fillGradient(-tall, -8, 0, 2, FADE, shade, clear);
        if (across.after())
            graphics.fillGradient(-tall, band - 10, 0, band, FADE, clear, shade);
        pose.popPose();
    }

    /**
     * Create's card plate: four stretched bands and no texture of its own, copied out of
     * {@code renderScheduleEntry}. The spine down the left edge runs the length of the window rather than
     * of the card, so the caller lays that under this.
     */
    private static void card(GuiGraphics graphics, int x, int y, int width, int height) {
        AllGuiTextures light = AllGuiTextures.SCHEDULE_CARD_LIGHT;
        UIRenderHelper.drawStretched(graphics, x, y + 1, width, height - 2, 0, light);
        UIRenderHelper.drawStretched(graphics, x + 1, y, width - 2, height, 0, light);
        UIRenderHelper.drawStretched(graphics, x + 1, y + 1, width - 2, height - 2, 0,
            AllGuiTextures.SCHEDULE_CARD_DARK);
        UIRenderHelper.drawStretched(graphics, x + 2, y + 2, width - 4, height - 4, 0,
            AllGuiTextures.SCHEDULE_CARD_MEDIUM);
        UIRenderHelper.drawStretched(graphics, x + 2, y + 2, width - 4, HEADER, 0, light);
    }

    /** How wide {@link #chip} draws a summary: Create's {@code getFieldSize} sum, which is private, with a ceiling added. */
    private static int fieldWidth(Font font, Pair<ItemStack, Component> summary, int minSize,
        int maxSize) {
        Component text = summary.getSecond();
        int wide = (text == null ? 0 : font.width(text)) + (summary.getFirst()
            .isEmpty() ? 0 : 20) + 16;
        return Math.min(Math.max(wide, minSize), maxSize);
    }

    /**
     * One of Create's condition plates, with two differences from Create's own: it cuts to the plate's
     * actual width rather than a flat 120 pixels, and it cuts with {@link CtSkin#clipped}'s mark
     * rather than bare.
     */
    public static void chip(GuiGraphics graphics, Font font, Pair<ItemStack, Component> summary, int x,
        int y, boolean clean, int minSize, int maxSize) {
        ItemStack stack = summary.getFirst();
        Component text = summary.getSecond();
        boolean hasItem = !stack.isEmpty();
        int width = fieldWidth(font, summary, minSize, maxSize);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);

        UIRenderHelper.drawStretched(graphics, 0, 0, width, 16, 0,
            AllGuiTextures.SCHEDULE_CONDITION_MIDDLE);
        // The notch hangs three pixels out to the left, and a row continuing the
        // one above is the flat cap that does not.
        if (clean)
            AllGuiTextures.SCHEDULE_CONDITION_LEFT_CLEAN.render(graphics, 0, 0);
        else
            AllGuiTextures.SCHEDULE_CONDITION_LEFT.render(graphics, -3, 0);
        AllGuiTextures.SCHEDULE_CONDITION_RIGHT.render(graphics, width - 2, 0);

        if (hasItem) {
            AllGuiTextures.SCHEDULE_CONDITION_ITEM.render(graphics, 3, 0);
            // Create's own stand-in for "any item", which has no model worth
            // drawing and would come out as a purple cube.
            if (stack.getItem() != Items.STRUCTURE_VOID)
                GuiGameElement.of(stack)
                    .at(4, 0)
                    .render(graphics);
        }

        int textX = hasItem ? 28 : 8;
        if (text != null)
            CtSkin.clipped(graphics, font, text, textX, 4, width - textX - 8, CHIP_TEXT);

        pose.popPose();
    }

    /** A scroll arrow and the target around it, padded outwards only sideways — there are no pixels to spare on the inside of the band. */
    private static void arrow(GuiGraphics graphics, List<Line> lines, boolean back, Action action,
        AllGuiTextures glyph, int x, int y) {
        glyph.render(graphics, x, y);
        // First in the list: the target may overlap the last pixel or two of
        // the condition area, and Create settles the same overlap by checking
        // its own arrows before the columns.
        int pad = 3;
        int from = back ? x - pad : x;
        lines.add(0, new Line(from, y - 4, glyph.getWidth() + pad, glyph.getHeight() + 8, action));
    }

    /** The nearest column edge behind where we are, or the very start. */
    private static int before(int[] starts, int sideways) {
        int target = 0;
        for (int start : starts)
            if (start < sideways)
                target = start;
        return target;
    }

    /** The nearest column edge ahead of where we are, or as far as it goes. */
    private static int after(int[] starts, int sideways, int end) {
        for (int start : starts)
            if (start > sideways)
                return start;
        return end;
    }

    /** Who answers for the cursor, or null if nothing drawn here does. */
    public static Action at(List<Line> lines, double mouseX, double mouseY) {
        for (Line line : lines)
            if (line.at()
                .holds(mouseX, mouseY))
                return line.action();
        return null;
    }

    /** The widest action name, so the stop names line up; floored by its own heading and capped at half the room. */
    private static int actionColumn(Font font, List<ScheduleEntry> entries, int room) {
        int widest = font.width(heading("create_routes.route.column.action"));
        for (ScheduleEntry entry : entries)
            widest = Math.max(widest, font.width(action(entry)));
        return Math.min(widest + 4, room / 2);
    }

    /** What the stop does, built from the instruction's id — the dropdown's wording, not {@code getTitleAs}, which an instruction may override into a whole sentence. */
    public static Component action(ScheduleEntry entry) {
        ResourceLocation id = entry.instruction.getId();
        return Component.translatable(id.getNamespace() + ".schedule.instruction." + id.getPath());
    }

    /** Takes a whole key: one assembled from a prefix is invisible to {@code scripts/lang_audit.py}. */
    private static Component heading(String key) {
        return Component.translatable(key)
            .withStyle(ChatFormatting.BOLD);
    }

}
