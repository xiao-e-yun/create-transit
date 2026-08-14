package me.xiaoeyun.createtransit.client;

/**
 * A rectangle, and — more to the point — <em>which</em> rectangle.
 *
 * <p>Everything here used to be loose ints, and the two that matter look
 * identical when they are: the box a thing is drawn in, and the box it is
 * measured against. A card is cut to its contents, so Create never had to tell
 * them apart; a window is not, so the fades, the scroll arrows and the scissor
 * each had to pick one, and getting it wrong is invisible until a list is long
 * enough to scroll.
 *
 * <p>The other half is the rim. A caller that writes {@code x + 1} has decided
 * how thick the frame is, in a place that cannot see it — so widening the frame
 * by a pixel is a hunt through every call site rather than one edit here.
 */
public record Box(int x, int y, int width, int height) {

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    /**
     * What is left of this inside another, or null when nothing is.
     *
     * <p>For recording a hit target the way it was drawn: a scissor cuts the
     * drawing and nothing cuts the record, so a row scrolled under an edge stays
     * clickable where it cannot be seen.
     */
    public Box within(Box other) {
        int left = Math.max(x, other.x());
        int top = Math.max(y, other.y());
        int right = Math.min(right(), other.right());
        int bottom = Math.min(bottom(), other.bottom());
        return right > left && bottom > top ? new Box(left, top, right - left, bottom - top) : null;
    }

    public boolean holds(double px, double py) {
        return px >= x && px < right() && py >= y && py < bottom();
    }

}
