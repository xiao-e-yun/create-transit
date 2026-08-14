package me.xiaoeyun.createtransit.client;

import java.util.function.IntPredicate;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A window onto rows taller than itself: banded, lit and scrolled as they are
 * drawn.
 *
 * <p>Placing is pure arithmetic over the box it is offered, so it can be asked
 * for before a frame has ever been drawn — which is what a button belonging to
 * Create needs, since its position is set in {@code init()}. The scroll offset
 * is worked out once a frame, in {@link #paint}, and is what a click between
 * frames is answered against — hit-testing does not ask the scroll again,
 * which would ease it a second time for the one frame that moved.
 */
public final class ScrollTable {

    /** What a row puts in itself, once it is known where it goes. */
    public interface Row {

        void paint(GuiGraphics graphics, Font font, int index, Box at, boolean hovered, double mouseX,
            double mouseY);

        /**
         * Who answers for a click in a row, given the same box it was drawn in.
         *
         * <p>Rows are not objects of their own. They are all one height and all
         * the same shape, so which one a point is in is arithmetic — and a table
         * of eleven would otherwise be eleven objects a frame that exist only to
         * be asked a question their parent could answer.
         */
        default Action hit(ScrollTable rows, int index, Box at, double x, double y) {
            return null;
        }
    }

    private final int rowHeight;

    private final Row row;

    private final Scroll scroll;

    private int rows;

    /**
     * Which rows are lit by something that is not the cursor, or null for none.
     *
     * <p>The map lights the stops that mean the station under it, and the same
     * light is the answer on purpose: a row and a station lit together are the
     * two ends of one thing, and a second colour would be a second thing to
     * learn.
     */
    private IntPredicate lit;

    /** Where placing put this. Meaningless until {@link #arrange} has run once. */
    private Box box = new Box(0, 0, 0, 0);

    /** How far the last {@link #paint} scrolled. */
    private int offset;

    public ScrollTable(int rowHeight, Row row, Scroll scroll) {
        this.rowHeight = rowHeight;
        this.row = row;
        this.scroll = scroll;
    }

    /** How many rows there are this frame, which the data decides. */
    public ScrollTable rows(int rows) {
        this.rows = rows;
        return this;
    }

    public ScrollTable lit(IntPredicate lit) {
        this.lit = lit;
        return this;
    }

    public Box box() {
        return box;
    }

    /** Takes all the room it is offered; whatever is stacked under it starts at the bottom of that. */
    public void arrange(Box within) {
        box = within;
    }

    /** Nothing outside the window answers, which is the row's own bounds checked in the same step. */
    public Action hit(double x, double y) {
        if (!box.holds(x, y))
            return null;
        int index = rowAt(y);
        return index < 0 ? null : row.hit(this, index, at(index), x, y);
    }

    /**
     * Which row is at a height, or -1. Not cut to whatever window this is
     * scrolling inside: a drag that has wandered a little past the last row
     * still means the last row, where stopping at the edge would make it mean
     * nothing at all.
     */
    public int rowAt(double y) {
        int index = (int) ((y - box.y() + offset) / rowHeight);
        return index >= 0 && index < rows ? index : -1;
    }

    /** Where a row goes, which drawing and hitting both have to agree on. */
    private Box at(int index) {
        return new Box(box.x(), box.y() - offset + index * rowHeight, box.width(), rowHeight);
    }

    public void paint(GuiGraphics graphics, Font font, double mouseX, double mouseY) {
        offset = scroll.at(rows * rowHeight, box.height());

        graphics.enableScissor(box.x(), box.y(), box.right(), box.bottom());
        for (int i = 0; i < rows; i++) {
            Box at = at(i);
            // Whether any of the row is inside the window, which is whether to
            // draw it: a row scrolled out of it is still somewhere — under the
            // headings, or past the last one — and one that answers to a click
            // nobody aimed would be worse than one that is merely skipped.
            if (at.bottom() <= box.y() || at.y() >= box.bottom())
                continue;
            // Lit rows are lit, but only the cursor's row is hovered: what a row
            // does when it is touched is not something the map may set off from
            // across the screen.
            boolean hovered = box.holds(mouseX, mouseY) && at.holds(mouseX, mouseY);
            CtSkin.row(graphics, at.x(), at.y(), at.width(), at.height(), i,
                hovered || (lit != null && lit.test(i)));
            row.paint(graphics, font, i, at, hovered, mouseX, mouseY);
        }
        graphics.disableScissor();
    }

}
