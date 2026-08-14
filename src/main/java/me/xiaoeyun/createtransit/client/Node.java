package me.xiaoeyun.createtransit.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Something that is given room and draws in it.
 *
 * <p>Two passes rather than one, and the split is the whole point. Placing is
 * pure arithmetic over the data and the screen size, so it can be asked for
 * before a frame has ever been drawn — which is what a button belonging to
 * Create needs, since its position is set in {@code init()}. Drawing is
 * everything that changes between frames and owns none of it.
 *
 * <p>So nothing that moves per frame is stored here. The scroll offset eases,
 * the cursor moves, and a node that baked either into its box would be stale by
 * the time it was drawn; both arrive in {@link Paint} instead. What is stored is
 * only where the node was put, which changes when the data or the window does —
 * and both of those already run {@code init()} again.
 */
public abstract class Node {

    /** Where placing put this. Meaningless until {@link #arrange} has run once. */
    protected Box box = new Box(0, 0, 0, 0);

    /** Whoever answers for a point, or null if nobody here does. */
    public abstract Action hit(double x, double y);

    /**
     * Place this in the room it is offered, and answer how much of it was taken.
     *
     * <p>Taking less is normal — a table of four rows in a window of eleven is
     * four rows tall, and whatever is stacked under it starts there rather than
     * at the bottom of the window.
     */
    public abstract Box arrange(Box within);

    /** Draw, and record what can be clicked while doing it. */
    public abstract void paint(Paint paint);

    public Box box() {
        return box;
    }

    /**
     * What a node does when it is reached.
     *
     * @param click 0 for the left button, 1 for the right, and anything else for
     *              the cursor merely resting there — which is when a tooltip is
     *              wanted and {@code graphics} is not null
     * @return whether this took it; if not, whatever is behind is offered it
     */
    public interface Action {

        boolean act(GuiGraphics graphics, double mouseX, double mouseY, int click);

        /**
         * Where the cursor has got to, while the press that took this is held.
         *
         * <p>The same object for the whole gesture, which is what makes this
         * worth having: whatever it captured when it was pressed — which row,
         * which column — is still true here, and there is nothing to work out
         * again from the coordinates.
         *
         * <p>There is no matching release. Nothing here holds a change back
         * until one, so letting go is simply the last time this is called.
         */
        default void drag(double mouseX, double mouseY) {}
    }

    /**
     * What a node needs to draw that is true of this frame only.
     *
     * <p>A record rather than a parameter list because it was one: the band that
     * draws a stop's conditions took eleven arguments, and five of them were
     * this, handed down through three call sites that had no use for them.
     */
    public record Paint(GuiGraphics graphics, Font font, double mouseX, double mouseY, Box clip) {

        /** Everything a container has not cut away. */
        public Paint within(Box to) {
            return new Paint(graphics, font, mouseX, mouseY, to);
        }

        /**
         * Whether the cursor is over a box, which is most of what hovering is.
         *
         * <p>Against the clip as well as the box, because a row scrolled out of
         * its window is still somewhere — under the headings, or past the last
         * one — and a row that lights up where it cannot be seen is a row that
         * answers to a click nobody aimed.
         */
        public boolean over(Box box) {
            return clip.holds(mouseX, mouseY) && box.holds(mouseX, mouseY);
        }

        /** Whether any of a box is inside the clip, which is whether to draw it. */
        public boolean shows(Box box) {
            return box.bottom() > clip.y() && box.y() < clip.bottom();
        }
    }

}
