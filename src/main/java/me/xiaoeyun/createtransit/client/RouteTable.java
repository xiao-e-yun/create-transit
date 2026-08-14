package me.xiaoeyun.createtransit.client;

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

import me.xiaoeyun.createtransit.content.route.RouteReference;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A schedule's stops as one line each, in place of Create's cards.
 *
 * <p>A card is {@code CARD_HEADER + 24 + maxRows * 18} tall and carries ten more
 * pixels of gap, so a stop that waits on one condition costs 74 — five of them
 * overflow the 173 pixels the list is scissored to. The conditions are what
 * makes it that tall, and they are also the part a player reads least often:
 * which stops exist, and in what order, is the question the list is open for.
 * With them behind a button a stop is a 16 pixel line, and that is the whole of
 * the density fix; nothing here is a cleverer layout.
 *
 * <p>Every line and button is recorded as it is drawn and handed back, so hit
 * testing is a scan of what is actually on screen. There is no second copy of
 * the arithmetic to keep in agreement with the first.
 *
 * <p>Drawn only for a route, never for a schedule a player wrote. See the mixin
 * that gates it.
 */
public class RouteTable {

    /** Heading text, a clear pixel, then the rule under it. */
    public static final int HEADING = 13;

    /**
     * The gap between a row's text and the buttons that act on it, which is also
     * where the override star stands.
     *
     * <p>A row keeps nothing at its own two ends. The window's rim is the margin
     * — putting another one inside it spends the width twice and leaves the name
     * clipped that much sooner, and a name is the one thing here that runs out
     * of room.
     */
    public static final int MARK = 8;

    /**
     * A row's icon column: a pixel of relief, the 16 square icon, and the gap
     * before the text.
     *
     * <p>The relief is not the row's margin — the row has none, and the window's
     * rim is what holds it off the edge. It is the icon's own: a stack drawn
     * hard against a dark border reads as having been cut off by it.
     */
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

    /**
     * Above the schedule's item icons, which are drawn at z 150, and below the
     * tooltips at 400. Without this the popup's plate covers the table's text
     * and its items still come through — they are batched by render type, so a
     * later fill does not beat an earlier item.
     */
    private static final int OVER = 200;

    /** Create's card header, which holds what the stop does. */
    private static final int HEADER = 22;

    /** Where a card's content starts, past the spine down its left edge. */
    private static final int CONTENT = 26;

    /** Where that spine is, measured from the card's left edge. */
    private static final int SPINE = 8;

    /**
     * Create's own card width, and with it Create's own answer to how many
     * alternatives are on screen: one, with the rest scrolled in from the side.
     *
     * <p>Sizing the card to its contents instead is what kept going wrong. Three
     * columns of 150 come to 549 pixels; at GUI scale 4 the whole screen is 480,
     * so the card ran off both edges and took its arrows with it. Create picked
     * 195 once and every measurement in {@code ScheduleScreen} is relative to
     * it, so taking the number outright is both smaller and more correct than
     * any rule of our own for arriving at one.
     */
    private static final int CARD_WIDTH = 195;

    /** Where a card sits on {@code AllGuiTextures.SCHEDULE}, taken from {@code ScheduleScreen}. */
    private static final int CARD_AT = 25;

    /** Where the spine down its side sits, also taken from {@code ScheduleScreen}. */
    private static final int SPINE_AT = 33;

    /**
     * How many buttons a stop's row ends with: its conditions, a copy of it, and
     * its removal. Moving one up or down would be two more, and this is the only
     * place that would have to hear about them.
     */
    public static final int SLOTS = 3;

    /**
     * The edge fades, over the fields and under the tooltips. Relative, because
     * the whole popup is already drawn {@link #OVER} everything else.
     */
    private static final int FADE = 10;

    /** Create's own shading, for the card, whose arrows stand clear of it. */
    private static final int FADE_CARD = 0x44000000;

    /**
     * And the route window's, which is darker. Its band has no gutter, so its
     * arrows lie on the fields themselves — at Create's weight a four pixel glyph
     * on a lit plate is not something anyone finds.
     */
    private static final int FADE_BAND = 0x88000000;

    /**
     * How far the route window holds its content off its own edges: the name
     * chip either side, and the band's content at both ends of what it scrolls
     * through. Small enough that the band gives up almost nothing, and the same
     * number in both places so the first alternative starts under the name.
     */
    private static final int INSET = 4;

    /** Create's scroll arrows, 4 by 8. */
    private static final int ARROW_WIDTH = 4;

    private static final int ARROW_HEIGHT = 8;

    /**
     * One more condition this column must also wait for, and one more column.
     * Their sizes are written out because {@code AllGuiTextures} keeps its own
     * private; these are the numbers its table declares them with.
     */
    private static final AllGuiTextures APPEND = AllGuiTextures.SCHEDULE_CONDITION_APPEND;

    private static final int APPEND_WIDTH = 10;

    private static final AllGuiTextures NEW = AllGuiTextures.SCHEDULE_CONDITION_NEW;

    private static final int NEW_WIDTH = 19;

    /** Where 8 pixels of text sit in a row taller than they are. */
    public static final int TEXT = (CtSkin.ROW_HEIGHT - 8) / 2;

    /** And where a 16 pixel icon sits in the same row. */
    public static final int ICON_INSET = (CtSkin.ROW_HEIGHT - 16) / 2;

    /** A stop answering for its own conditions instead of taking the route's. */
    private static final String OVERRIDDEN = "*";

    /**
     * Stands where a stop's index would, for the conditions that belong to the
     * route rather than to any one stop. Negative because every real index is
     * not, and -1 already means "none".
     */
    public static final int DEFAULTS = -2;

    /** What the route's own card carries instead of an instruction. */
    private static final ItemStack ROUTE_ICON = AllItems.SCHEDULE.asStack();

    /** What clicking a drawn thing does. */
    /**
     * What a click on a band of conditions means, which belongs to whoever owns
     * the conditions rather than to whoever drew them.
     *
     * <p>The band is drawn twice at two widths — over the screen for a stop, and
     * inside its window for the route's own — and the two differ only in which
     * list they are editing and which scroll the arrows move. That difference is
     * everything this hands over, and it used to be an integer stop number
     * carried through four layers so that one {@code == DEFAULTS} at the far end
     * could ask it.
     */
    public interface Conditions {

        Action condition(int column, int row);

        /** One more condition in a column the train must also wait for. */
        Action add(int column);

        /** One more column: a second way for the train to be let go. */
        Action alternative();

        /** @param target where the band should scroll to, in pixels */
        Action scroll(int target);
    }

    /**
     * Where a band's two scroll arrows sit, and how far down.
     *
     * <p>Handed in whole rather than as three loose numbers. None of them means
     * anything without the other two, and all three are the caller's to decide:
     * they depend on what it has around its band, which the band cannot see.
     * Create's card has a 26 pixel gutter and centres them down the whole card;
     * a window that gives its whole width to the conditions has neither.
     */
    private record Arrows(int left, int right, int y) {}

    /**
     * What a click on a row of stops means, which belongs to whoever owns the
     * data rather than to whoever drew it.
     *
     * <p>This table knows which row was hit and which of its buttons. It does
     * not know what removing a stop is — and the enum it used to hand back was
     * exactly that knowledge, written in a form that let it pretend otherwise.
     *
     * @param slot which of the row's buttons, or -1 for the row itself
     */
    public interface Stops {

        Action at(int index, int slot);

        /**
         * A press on the row's icon, which is what carries it somewhere else.
         *
         * <p>The icon column and nothing more. A row that could be dragged from
         * anywhere would have to decide afterwards whether a press was a drag or
         * a click, and the click is the thing a player does most — deferring it
         * to the release to find out changes how the common case feels for the
         * sake of the rare one.
         */
        Action grip(int index, ScrollTable rows);

        /**
         * Said as a row is drawn under the cursor, so that the map can mark the
         * stations it means.
         *
         * <p>Told rather than asked because the table is the one that knows: the
         * row a point falls in is arithmetic the table already does, and a
         * second copy of it outside would be a second copy to keep in step.
         */
        default void over(int index) {}

        /** Whether the map's cursor is on a station this stop means. */
        default boolean lit(int index) {
            return false;
        }
    }

    /**
     * One drawn rectangle and what answers for it.
     *
     * <p>For the parts that are not tables: a band's columns are each their own
     * width and a card's buttons are wherever the sheet painted them, so there
     * is no arithmetic that finds them again afterwards. Recording them as they
     * are drawn is the next cheapest thing, and it cannot fall out of step with
     * the drawing because it is the drawing.
     */
    public record Line(Box at, Action action) {

        Line(int x, int y, int width, int height, Action action) {
            this(new Box(x, y, width, height), action);
        }
    }

    private RouteTable() {}

    /**
     * Draws the table and returns every rectangle that can be clicked.
     *
     * <p>The row under the cursor is lit as it is drawn rather than being
     * remembered. Choosing a stop used to be a state of its own, from when
     * clicking one twice was how its conditions opened; the conditions have
     * their own button now, so nothing was left for a chosen row to mean. The
     * band alone says which row it is — a marker beside it would be a second
     * way of saying the same thing.
     */
    public static ScrollTable render(GuiGraphics graphics, Font font, List<ScheduleEntry> entries, int x,
        int y, int width, int bottom, double mouseX, double mouseY, Scroll scroll, Stops stops) {
        // Where the text of a row may run: from past the icon to short of the
        // buttons, and short again of the mark that sits beside them.
        int textX = x + ICON;
        int textEnd = x + width - Strip.width(SLOTS) - MARK;
        int actions = actionColumn(font, entries, textEnd - textX);

        graphics.drawString(font, heading("create_transit.route.column.action"), textX, y + 2,
            CtSkin.FIELD_TEXT, false);
        graphics.drawString(font, heading("create_transit.route.column.stop"), textX + actions, y + 2,
            CtSkin.FIELD_TEXT, false);
        CtSkin.rule(graphics, x, y + HEADING - 1, width);

        // The headings stay put; only what is under them moves. One row past the
        // stops for the line that adds another.
        //
        // Whole rows only. The window is not a multiple of a row at any row
        // height, and a row sliced off by the bottom edge reads as a drawing
        // mistake; the remainder is better spent as margin under the last one.
        int top = y + HEADING;
        int buttons = textEnd + MARK;

        // One row past the stops for the line that adds another. It carries the
        // same band and the same lighting: it is one more row of the same list,
        // reached down the same column, and a row that alone stays dark reads as
        // disabled.
        ScrollTable window = new ScrollTable(CtSkin.ROW_HEIGHT, new ScrollTable.Row() {

            @Override
            public void paint(GuiGraphics graphics, Font font, int index, Box at, boolean hovered,
                double mouseX, double mouseY) {
                if (index == entries.size()) {
                    graphics.drawString(font, Component.translatable("create_transit.route.add_stop"),
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

    /**
     * The three things a row can have done to it.
     *
     * <p>Create's own card glyphs, because these are Create's own operations —
     * a player who has used a schedule already knows what they mean, and two
     * pictures for one action is one picture too many.
     */
    private static void buttons(GuiGraphics graphics, Font font, ScheduleEntry entry, int x, int y) {
        Strip strip = Strip.endingAt(graphics, x + Strip.width(SLOTS), y, SLOTS);

        // A nested route has no conditions of its own — its stops each answer
        // for themselves — so the slot it would have spent on them opens it
        // instead, which is the only other thing a row can lead into. Nor has an
        // instruction that never waits any conditions: Create draws no condition
        // area for one, and a button onto an editor for something the train
        // passes straight through is a button onto nothing. The slot is still
        // spent either way, so the two that follow do not shuffle along.
        // Two different doors, so two different pictures. I_VIEW_SCHEDULE is a
        // list of lines — a schedule, which is what a route is — and it means
        // the same thing on every screen here. The chevron stays with the
        // conditions, where it has always been.
        boolean nested = RouteReference.of(entry.instruction) != null;
        if (nested)
            strip.button(AllIcons.I_VIEW_SCHEDULE);
        else if (entry.instruction.supportsConditions()) {
            strip.button(AllIcons.I_CONFIG_OPEN);
            if (!entry.conditions.isEmpty())
                graphics.drawString(font, OVERRIDDEN, x - 6, y + TEXT, CtSkin.FIELD_TEXT, false);
        } else
            strip.blank();

        // Icons, not Create's card glyphs. Those are 12 square where an icon is
        // 16 and painted for a light plate, so beside the conditions icon they
        // came out smaller and darker — and on an unbanded row the remove cross
        // is #393939 on #393939, which is to say absent.
        //
        // Both of these are picked for their picture and not for their name,
        // which is worth saying out loud so that nobody corrects them back:
        // I_FX_BLEND_OFF is two sheets with the front one whole, and I_DISABLE
        // is a clean cross. The atlas has no copy and no delete of its own, and
        // the near misses are worse — I_OPEN_FOLDER is drawn hollow, and
        // I_MTD_CLOSE has grey at the ends of its strokes.
        strip.button(AllIcons.I_FX_BLEND_OFF);
        strip.button(AllIcons.I_DISABLE);
    }

    /**
     * One stop's wait conditions, over everything else.
     *
     * <p>Columns are alternatives — any one of them satisfied lets the train go
     * — and within a column every condition must hold. That is Create's
     * structure, kept; only its shape on screen is ours.
     */
    public static List<Line> conditions(GuiGraphics graphics, Font font, ScheduleEntry entry,
        Conditions of, Action close, int screenWidth, int screenHeight, double mouseX,
        double mouseY, Scroll down, Scroll across) {
        List<List<ScheduleWaitCondition>> columns = entry.conditions;

        int cardWidth = CARD_WIDTH;
        // Create's own, with a floor it never needs. Its card is one of a list
        // with ten pixels of gap under it, so a stop with no conditions at all
        // leaves its lone "new alternative" plate a pixel off the bottom edge and
        // reads as merely tight. Alone on a panel there is nothing under it for
        // the eye to carry on to, and the same pixel reads as an overflow. Eight
        // more gives it the nine every other row already gets.
        int cardHeight = Math.max(HEADER + 32, HEADER + 24 + rowsIn(columns) * CONDITION_ROW);
        // Create's list area, which is what the card scrolls inside. A condition
        // drawn past the bottom is one the train still waits for and the player
        // cannot see.
        //
        // The card starts nine pixels into that area, and the content is that
        // much again past its end — so scrolled all the way down the last row
        // clears the edge by the same margin it clears the top.
        int inset = CARD_AT - CtSkin.LIST_AT;
        int offset = down.at(inset + cardHeight + inset, CtSkin.LIST_HEIGHT);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, OVER);

        // Dimmed the way Create dims the screen behind its own editor, so the
        // two read as the same kind of thing — and so nothing behind looks
        // available while it is not.
        graphics.fill(0, 0, screenWidth, screenHeight, 0xB0000000);
        Box panel = CtSkin.schedulePanel(graphics, font,
            Component.translatable("create_transit.route.window.conditions"), screenWidth, screenHeight,
            mouseX, mouseY);
        int x = panel.x();
        int y = panel.y();

        int cardX = x + CARD_AT;
        int cardY = y + CARD_AT - offset;

        graphics.enableScissor(x + CtSkin.LIST_AT, y + CtSkin.LIST_AT, x + CtSkin.LIST_AT + CtSkin.LIST_WIDTH,
            y + CtSkin.LIST_AT + CtSkin.LIST_HEIGHT);
        card(graphics, cardX, cardY, cardWidth, cardHeight);

        // After the plate, not before it. Create draws this inside
        // renderScheduleEntry once the four fills are down, so the strip lies on
        // the card; drawing it first buries everything the card covers and
        // leaves only the ends showing, which reads as a broken line.
        UIRenderHelper.drawStretched(graphics, cardX + SPINE, cardY, 3, cardHeight + 10, 0,
            AllGuiTextures.SCHEDULE_STRIP_LIGHT);
        AllGuiTextures.SCHEDULE_STRIP_TRAVEL.render(graphics, cardX + 4, cardY + 6);
        AllGuiTextures.SCHEDULE_STRIP_WAIT.render(graphics, cardX + 4, cardY + 28);

        // And the darker one over the top of it, which is the second line in
        // Create's own: it runs the length of the list rather than of a card,
        // and it is drawn above them all.
        UIRenderHelper.drawStretched(graphics, x + SPINE_AT, y + CtSkin.LIST_AT, 3, CtSkin.LIST_HEIGHT, FADE,
            AllGuiTextures.SCHEDULE_STRIP_DARK);

        // What the stop does, in the header, drawn as its own field rather than
        // as a title — this is the same summary the table row shows, and the
        // card is where a player has seen it before.
        // 100, which is Create's own minimum for this field. Asking for the
        // card's whole width instead pinned it at the plate's 150 cap every
        // time, so a two word instruction got the same plate as a sentence.
        chip(graphics, font, entry.instruction.getSummary(), cardX + CONTENT, cardY + 5, false, 100,
            CHIP_MAX);
        entry.instruction.renderSpecialIcon(graphics, cardX + CONTENT + 4, cardY + 5);

        List<Line> lines = new ArrayList<>();
        // The fade and the arrows are Create's own numbers: the fade runs the
        // card's field, header to foot, and the arrows are centred down that same
        // field rather than down the conditions in it. Centring on the conditions
        // is what a tall column scrolls out of view.
        //
        // Both stand outside the area the conditions are clipped to, which starts
        // three pixels before the fields for the plate's notch. Five clear of it
        // on the left, where Create's card has 26 pixels of gutter — the notch is
        // what the eye reads as the edge, so an arrow measured off the fields
        // instead lands a pixel inside it. Flush on the right, where the 16
        // pixels there are shared with the card's own buttons; Create seats that
        // one a pixel inside as well and settles the overlap the way we do, by
        // checking the arrows before the columns.
        int inner = cardWidth - CONTENT - 16;
        int clipped = cardX + CONTENT - 3;
        band(graphics, font, columns, of, cardX + CONTENT, cardY + 29, inner, cardHeight - 29, 0,
            new Arrows(clipped - 5 - ARROW_WIDTH, clipped + 3 + inner,
                cardY + (HEADER + cardHeight - ARROW_HEIGHT) / 2),
            new Box(x + CtSkin.LIST_AT, y + CtSkin.LIST_AT, CtSkin.LIST_WIDTH, CtSkin.LIST_HEIGHT), across,
            lines);
        fades(graphics, cardX + CONTENT, inner, cardY + HEADER + 2, cardY + cardHeight - 2, FADE_CARD,
            across);

        graphics.disableScissor();

        // The confirm plate is already drawn, over the sheet's own painted tick;
        // this is only the hit target for it, which is the panel's to place and
        // this method's own action to answer with.
        lines.add(new Line(CtSkin.confirm(x, y), close));

        pose.popPose();
        return lines;
    }

    /**
     * The route's own conditions, in its window instead of over the screen.
     *
     * <p>The same card the popup draws, at the window's width rather than
     * Create's 195 — this one is not competing with anything for the screen, so
     * it may as well use what it is given. Where a stop's card names its
     * instruction, this one names the route: these are the conditions every stop
     * that declares none of its own will be waiting on, and the header says
     * whose they are.
     *
     * <p>No panel and no dimming. The window it is drawn in already has a frame
     * and a title, and there is nothing behind it to hide.
     */
    public static List<Line> defaults(GuiGraphics graphics, Font font, String name,
        List<List<ScheduleWaitCondition>> columns, Conditions of, int x, int y, int width, int bottom,
        Scroll down, Scroll across) {
        // A row for the name, then one line per condition and one more for the
        // button that adds another.
        int tall = (rowsIn(columns) + 1) * CONDITION_ROW;
        int top = y - down.at(CONDITION_ROW + 4 + tall, bottom - y);

        // The whole width, minus the three pixels a plate hangs its
        // notch into. No gutter for the arrows: they lie on the fields, over the
        // fades that already say there is more that way, and a strip of empty
        // kept beside them would cost eight pixels of the narrowest band on the
        // screen to buy back four.
        //
        // Four, because an arrow's target is padded outwards only: what it can
        // still take is the glyph's own footprint, one pixel at the left where
        // the notch strip absorbs the rest and four at the right where there is
        // nothing to absorb it. Both land on the column that is cut off at that
        // edge — one press away from the middle — and Create's card gives up the
        // same two pixels in the same place.
        int bandX = x + 3;
        int band = width - 3;

        graphics.enableScissor(x, y, x + width, bottom);

        // The name has the row to itself and four pixels either side of it. It is
        // the one thing here that does not scroll, so it is also the one thing
        // that has pixels to give: the band below is spending all of its own to
        // fit an alternative and a half, and a header run flush into two corners
        // reads as having overflowed rather than as filling the row.
        //
        // Four each way, counted from the notch a plate hangs at x - 3 rather
        // than from the plate, because the notch is the edge that shows.
        //
        // A plate treats a width as a floor and not a ceiling — a field grows
        // to fit its text — so anything beside it is something a long enough name
        // ends up underneath.
        // Floor and ceiling both, so the name fills its row exactly. Create's
        // plates grow to fit and stop at 150, which is wider than this band —
        // left to that, a long name ran off the edge and took its own ellipsis
        // with it, drawn where nobody could read it.
        chip(graphics, font, Pair.of(ROUTE_ICON, Component.literal(name)), bandX + INSET, top + 2,
            false, band - INSET * 2, band - INSET * 2);

        List<Line> lines = new ArrayList<>();

        // The fade and the arrows are measured off the panel, not off the content
        // that scrolls inside it — the panel is a fixed size and the content is
        // whatever the route has. Create's card is cut to its content, so there
        // the two are the same number and it never had to choose.
        //
        // Both are the panel's whole height, name row included. That row is
        // content here rather than chrome, and it scrolls with the rest, so a
        // fade that started under it would leave the top of the edge bare as soon
        // as anything moved.
        int rowTop = top + CONDITION_ROW + 4;
        band(graphics, font, columns, of, bandX, rowTop, band, tall, INSET,
            new Arrows(x, x + width - ARROW_WIDTH, (y + bottom - ARROW_HEIGHT) / 2),
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

    /**
     * Every alternative on a card, the buttons that add one, and the arrows that
     * reach the ones off the edge.
     *
     * <p>All of it measured from the card, so the popup and the route's own
     * window get the same thing at two widths. Everything that differs between
     * them — the panel, the dimming, the header, the footer — is the caller's.
     */
    private static void band(GuiGraphics graphics, Font font, List<List<ScheduleWaitCondition>> columns,
        Conditions of, int bandX, int rowTop, int band, int tall, int pad, Arrows arrows, Box view,
        Scroll across, List<Line> lines) {
        // What is drawn is cut twice — once by the caller's window and once by
        // the band's own scissor — so what is recorded is cut by both as well.
        // Only the first of those was ever accounted for, and then only
        // sideways: a column taller than the band answered for every row it had,
        // including the ones scrolled under the footer.
        Box cut = new Box(bandX - 3, rowTop, band + 3, tall).within(view);

        // The last slot is the button that starts another column, laid out as if
        // it were one so that scrolling has no special case to make.
        int slots = columns.size() + 1;

        // A column is as wide as its widest condition, and they run sideways the
        // way Create's own do — the rest scrolled in with the arrows.
        int[] widths = new int[columns.size()];
        int[] starts = new int[columns.size()];
        int spread = NEW_WIDTH;
        for (int column = 0; column < columns.size(); column++) {
            int wide = CHIP_MIN;
            for (ScheduleWaitCondition condition : columns.get(column))
                wide = Math.max(wide, fieldWidth(font, condition.getSummary(), CHIP_MIN, CHIP_MAX));
            widths[column] = wide;
            starts[column] = spread - NEW_WIDTH;
            spread += wide + COLUMN_GAP;
        }

        // Inside the scrolling, not around it: added to what there is to scroll
        // rather than taken off what there is room for, so the band keeps its
        // whole width and the margin is something that scrolls away. The starts
        // above are untouched — each is still the offset that brings its column
        // to the same place the first one sits at rest.
        spread += pad * 2;

        int sideways = across.at(spread, band);

        // Three pixels of slack on the left, because a plate hangs the
        // plate's notch at x - 3 and a scissor starting at x would shave it off
        // the first column while leaving it on every other.
        graphics.enableScissor(bandX - 3, rowTop, bandX + band, rowTop + tall);
        int columnX = bandX + pad - sideways;
        for (int column = 0; column < slots; column++) {
            int wide = column < columns.size() ? widths[column] : NEW_WIDTH;
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
            APPEND.render(graphics, columnX + (wide - APPEND_WIDTH) / 2, appendY);
            // Clickable across the whole column, not just the glyph: nothing
            // else is on that line, and a 10 pixel target is a 10 pixel miss.
            record(lines, cut, columnX, appendY, wide, of.add(column));
            columnX += wide + COLUMN_GAP;
        }
        graphics.disableScissor();

        // Above the fades, not merely after them: a fade is a fill at z FADE and
        // a glyph is a blit at z 0, so drawn in either order the arrow would end
        // up beneath the shading. On Create's card that only dims it, because it
        // sits in the dark gutter beside the fields; lying on a field it
        // disappears altogether.
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

    /**
     * One condition-sized rectangle, cut to what is on screen, or nothing at all
     * where none of it is.
     *
     * <p>A column halfway off the edge is drawn halfway and answers for halfway.
     * Recorded whole it would put a hit target under the scroll arrow beside it,
     * and the arrow — added to the front of the list — would never be the one
     * found.
     */
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
     * <p>Drawn sideways, because {@code fillGradient} only interpolates down the
     * screen: the pose is turned a quarter turn and the gradient runs along what
     * is now its vertical. Per side rather than both at once, so an edge with
     * nothing past it stays clear.
     *
     * <p>The caller's, not the band's: it is given a top, a bottom and a shade
     * rather than working any of them out. Those are only the band's own numbers
     * on Create's card, which is cut to its content and has a gutter for its
     * arrows; a window is a fixed size and has neither. Drawn after the columns
     * and before the arrows only in reading order — the arrow sits at
     * {@code FADE + 1} and wins on depth whichever went down first.
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
     * Create's card plate, which is four stretched bands and no texture of its
     * own.
     *
     * <p>Copied out of {@code renderScheduleEntry} rather than borrowed, because
     * that method draws a whole card — its remove and duplicate buttons, its
     * move arrows, its destination field — around these few lines, and those
     * belong to the row this was opened from.
     *
     * <p>The spine down the left edge is not drawn here either. It runs the
     * length of the window rather than of the card, the way it runs the length
     * of Create's list, so the caller lays it under this.
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

    /**
     * How wide {@link #chip} will draw a summary, between a floor and a ceiling.
     *
     * <p>Create's {@code getFieldSize} says the same thing — the text, a slot's
     * width if there is an item, and the plate's two caps — but says it
     * privately, which is why this was ever a copy. It is not one any more: this
     * is what decides the width, and {@link #chip} is what draws it.
     */
    private static int fieldWidth(Font font, Pair<ItemStack, Component> summary, int minSize,
        int maxSize) {
        Component text = summary.getSecond();
        int wide = (text == null ? 0 : font.width(text)) + (summary.getFirst()
            .isEmpty() ? 0 : 20) + 16;
        return Math.min(Math.max(wide, minSize), maxSize);
    }

    /**
     * One of Create's condition plates, drawn here rather than borrowed.
     *
     * <p>{@code ScheduleScreen.renderInput} draws exactly this and is protected,
     * so a mixin could hand it over — and did. What it could not hand over was
     * the measuring: {@code getFieldSize} is private, so the arithmetic above had
     * to be copied anyway, and a plate whose width we work out but do not draw is
     * the same knowledge kept in two places on purpose. Six calls is the whole of
     * what was being borrowed for it.
     *
     * <p>Two differences from Create's, both in the text. It cuts at a flat 120
     * pixels, which is six short of the room a plate without an item has and six
     * past what one with an item has; here the room is worked out from the plate,
     * so the cut lands where the plate ends. And it cuts bare, where
     * {@link CtSkin#clipped} leaves a mark saying it did.
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

    /**
     * A scroll arrow and the target around it.
     *
     * <p>The glyph is four pixels wide, which is a four pixel miss. Create pads
     * its own the same way — the arrow is drawn at 15 and answers from 12 to 19.
     *
     * <p>Outwards only, though, where Create pads both ways. The three pixels on
     * the inside are the ones that reach into the conditions, and they are the
     * three the gutter then has to be made wider to hold — paid for twice, in a
     * window whose whole band is a hundred pixels. Away from the band there is
     * nothing to take them from.
     */
    private static void arrow(GuiGraphics graphics, List<Line> lines, boolean back, Action action,
        AllGuiTextures glyph, int x, int y) {
        glyph.render(graphics, x, y);
        // First in the list, because the target may still overlap the last pixel
        // or two of the condition area and whoever is found first wins. Create
        // settles the same overlap the same way: in its own hit test the arrows
        // are checked before the columns.
        int pad = 3;
        int from = back ? x - pad : x;
        lines.add(0, new Line(from, y - 4, ARROW_WIDTH + pad, ARROW_HEIGHT + 8, action));
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

    /**
     * How wide the action column has to be: the widest of them, so the stop
     * names line up instead of each starting wherever its verb ended.
     *
     * <p>Half the room at the most. The widest action is not bounded by
     * anything — "Update schedule title" is a hundred pixels on its own, and at
     * 320 the table is 182 wide with 54 of that spent on buttons, so left to
     * itself the action column takes the whole line and the stop column, which
     * is the one a player is actually reading down, gets nothing. Both halves
     * cut rather than one of them vanishing.
     *
     * <p>Its own heading is the floor. A route with no stops yet has no widest
     * action either, and a column of nothing wide put the second heading on top
     * of the first — a heading is content of the column it names, and measuring
     * the column without it was the whole of that bug.
     */
    private static int actionColumn(Font font, List<ScheduleEntry> entries, int room) {
        int widest = font.width(heading("create_transit.route.column.action"));
        for (ScheduleEntry entry : entries)
            widest = Math.max(widest, font.width(action(entry)));
        return Math.min(widest + 4, room / 2);
    }

    /**
     * What the stop does, named the way the editor's type dropdown names it.
     *
     * <p>Built from the instruction's id rather than asked of the instruction,
     * because {@code getTitleAs} is the tooltip's wording and an instruction may
     * override it into a whole sentence — ours says "follow a shared route"
     * where the dropdown says "Follow Route". This is the key
     * {@code Schedule.getTypeOptions} assembles, so a column of these reads as
     * the same words the player picked from.
     */
    public static Component action(ScheduleEntry entry) {
        ResourceLocation id = entry.instruction.getId();
        return Component.translatable(id.getNamespace() + ".schedule.instruction." + id.getPath());
    }

    /**
     * Takes a whole key rather than a column name. Building one from a prefix
     * hides it from scripts/lang_audit.py, which then reports the key as unused
     * and the assembled one as missing — both of which it has already done once.
     */
    private static Component heading(String key) {
        return Component.translatable(key)
            .withStyle(ChatFormatting.BOLD);
    }

}
