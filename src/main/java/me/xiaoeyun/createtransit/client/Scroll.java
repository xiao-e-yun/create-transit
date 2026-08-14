package me.xiaoeyun.createtransit.client;

import net.minecraft.util.Mth;

/** How far a list has been scrolled, and how far it may go. */
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

    /** Moves to a given place, for a button that was told where to land. */
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
