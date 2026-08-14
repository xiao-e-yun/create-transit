package me.xiaoeyun.createroutes.client;

import java.util.function.IntPredicate;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** A window onto rows taller than itself: banded, lit and scrolled as they are drawn. */
public final class ScrollTable {

    /** What a row puts in itself, once it is known where it goes. */
    public interface Row {

        void paint(GuiGraphics graphics, Font font, int index, Box at, boolean hovered, double mouseX,
            double mouseY);

        /** Who answers for a click in a row, given the same box it was drawn in. */
        default Action hit(ScrollTable rows, int index, Box at, double x, double y) {
            return null;
        }
    }

    private final int rowHeight;

    private final Row row;

    private final Scroll scroll;

    private int rows;

    /** Which rows are lit by something that is not the cursor, or null for none. */
    private IntPredicate lit;

    /** Where placing put this; meaningless until {@link #arrange} has run once. */
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

    /** Pure arithmetic over the offered box, so it may be called before a frame is drawn — Create sets a button's position in {@code init()}. */
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

    /** Which row is at a height, or -1; not clamped to the window, so a drag past the last row still means the last row. */
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
            // Skip rows scrolled entirely outside the window.
            if (at.bottom() <= box.y() || at.y() >= box.bottom())
                continue;
            // Lit rows are lit, but only the cursor's row is hovered.
            boolean hovered = box.holds(mouseX, mouseY) && at.holds(mouseX, mouseY);
            CtSkin.row(graphics, at.x(), at.y(), at.width(), at.height(), i,
                hovered || (lit != null && lit.test(i)));
            row.paint(graphics, font, i, at, hovered, mouseX, mouseY);
        }
        graphics.disableScissor();
    }

}
