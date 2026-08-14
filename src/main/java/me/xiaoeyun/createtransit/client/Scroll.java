package me.xiaoeyun.createtransit.client;

import net.minecraft.util.Mth;

/**
 * How far a list has been scrolled, and how far it may go.
 *
 * <p>Nothing here is vertical. It clamps one number against how much more there
 * is than there is room for, so an axis is whatever the caller passes in — a
 * band of alternatives too wide for its card would use it the same way a list of
 * stops too tall for its window does.
 *
 * <p>Cheap here for one reason: hit targets are recorded as they are drawn, so
 * moving the drawing moves what can be clicked with it. There is no second copy
 * of the geometry to offset, and nothing to keep in agreement.
 *
 * <p>The range is set by whoever draws, because that is where both measurements
 * are known — a caller that worked them out first would be a second opinion
 * about a layout it does not own. Until something has been drawn once the range
 * is zero and the wheel does nothing, which is also the right answer for a list
 * that fits.
 */
public class Scroll {

    /** A notch of the wheel, which is about a row either way. */
    private static final int STEP = 16;

    /** How much of the way there each frame carries it. */
    private static final float EASE = 0.35f;

    /** Where it is going. */
    private int offset;

    /** Where it is, which catches up. */
    private float shown;

    private int range;

    /**
     * Clamps to what is currently on screen and answers where to draw from.
     *
     * @param content how much there is, along whichever axis this is
     * @param visible how much of it there is room for
     */
    public int at(int content, int visible) {
        range = Math.max(0, content - visible);
        offset = Mth.clamp(offset, 0, range);

        // ponytail: eased per frame, so it runs faster on a faster machine.
        // Create ticks a LerpedFloat instead, which is frame-rate independent
        // and costs a containerTick injection; if the difference ever shows,
        // that is the upgrade.
        shown += (offset - shown) * EASE;
        if (Math.abs(offset - shown) < 0.5f)
            shown = offset;
        return Math.round(shown);
    }

    /** Whether the wheel was used here, so the caller knows to consume it. */
    public boolean wheel(double delta) {
        return to(offset - (int) Math.signum(delta) * STEP);
    }

    /**
     * Moves to a given place, for a button that was told where to land.
     *
     * <p>Alternatives are not all the same width, so an arrow that stepped by a
     * fixed amount would leave one of them half off the edge. Whoever draws them
     * knows where each one starts and hands the target over.
     */
    public boolean to(int where) {
        if (range <= 0)
            return false;
        offset = Mth.clamp(where, 0, range);
        return true;
    }

    /** Back to the top, for when this is now showing something else. */
    public void reset() {
        offset = 0;
        shown = 0;
    }

    /** Whether there is anything above or behind, for an arrow to point at. */
    public boolean before() {
        return offset > 0;
    }

    public boolean after() {
        return offset < range;
    }

}
