package me.xiaoeyun.createtransit.client;

/** A rectangle, and — more to the point — <em>which</em> rectangle. */
public record Box(int x, int y, int width, int height) {

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    /** What is left of this inside another, or null when nothing is. */
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
