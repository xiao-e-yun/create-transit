package me.xiaoeyun.createtransit.client;

import java.util.function.IntPredicate;

/**
 * Rows of one height, one under another, banded and lit as they are drawn.
 *
 * <p>Knows nothing about scrolling. It answers with the height of all its rows,
 * however many that is, and a container that has less room than that is the one
 * that decides what to do about it — which is the same division HTML draws
 * between a block and {@code overflow}.
 *
 * <p>What is left to the caller is a row's contents, because that is the only
 * part the two tables here do not share. Everything around it — the banding,
 * the phase it starts on, which rows are worth drawing, whether the cursor
 * counts as being on one — was written twice, and in one of the two the drawing
 * and the clicking each kept their own copy of it.
 */
public class TableNode extends Node {

    /** What a row puts in itself, once it is known where it goes. */
    public interface Row {

        void paint(Paint paint, int index, Box at, boolean hovered);

        /**
         * Who answers for a click in a row, given the same box it was drawn in.
         *
         * <p>Rows are not nodes of their own. They are all one height and all
         * the same shape, so which one a point is in is arithmetic — and a table
         * of eleven would otherwise be eleven objects a frame that exist only to
         * be asked a question their parent could answer.
         */
        default Action hit(TableNode rows, int index, Box at, double x, double y) {
            return null;
        }
    }

    private final int rowHeight;

    private final Row row;

    private int rows;

    /**
     * Which row counts as the first for banding.
     *
     * <p>A list with something above it in the same column carries on that
     * column's stripe rather than starting its own, and the two tables here
     * happen to disagree about which one that is.
     */
    private int phase;

    /**
     * Which rows are lit by something that is not the cursor, or null for none.
     *
     * <p>The map lights the stops that mean the station under it, and the same
     * light is the answer on purpose: a row and a station lit together are the
     * two ends of one thing, and a second colour would be a second thing to
     * learn.
     */
    private IntPredicate lit;

    public TableNode(int rowHeight, Row row) {
        this.rowHeight = rowHeight;
        this.row = row;
    }

    /** How many rows there are this frame, which the data decides. */
    public TableNode rows(int rows) {
        this.rows = rows;
        return this;
    }

    public TableNode phase(int phase) {
        this.phase = phase;
        return this;
    }

    public TableNode lit(IntPredicate lit) {
        this.lit = lit;
        return this;
    }

    @Override
    public Box arrange(Box within) {
        return box = new Box(within.x(), within.y(), within.width(), rows * rowHeight);
    }

    @Override
    public Action hit(double x, double y) {
        if (!box.holds(x, y))
            return null;
        int index = (int) ((y - box.y()) / rowHeight);
        return index >= 0 && index < rows ? row.hit(this, index, at(index), x, y) : null;
    }

    /**
     * Which row is at a height, or -1. Not cut to whatever window this is
     * scrolling inside: a drag that has wandered a little past the last row
     * still means the last row, where stopping at the edge would make it mean
     * nothing at all.
     */
    public int rowAt(double y) {
        int index = (int) ((y - box.y()) / rowHeight);
        return index >= 0 && index < rows ? index : -1;
    }

    /** Where a row goes, which drawing and hitting both have to agree on. */
    private Box at(int index) {
        return new Box(box.x(), box.y() + index * rowHeight, box.width(), rowHeight);
    }

    @Override
    public void paint(Paint paint) {
        for (int i = 0; i < rows; i++) {
            Box at = at(i);
            if (!paint.shows(at))
                continue;
            // Lit rows are lit, but only the cursor's row is hovered: what a
            // row does when it is touched is not something the map may set off
            // from across the screen.
            boolean hovered = paint.over(at);
            CtSkin.row(paint.graphics(), at.x(), at.y(), at.width(), at.height(), i + phase,
                hovered || (lit != null && lit.test(i)));
            row.paint(paint, i, at, hovered);
        }
    }

}
