package me.xiaoeyun.createtransit.client;

/**
 * A window onto something taller than itself.
 *
 * <p>Four places kept their own copy of this: work out how much is shown, clamp
 * it to whole rows, ask the scroll where it is, cut to the window, and put the
 * content back by that much. It is the same five lines every time and the only
 * thing that differed was what was inside.
 *
 * <p>The content is placed again on every frame rather than once, because the
 * offset eases and a box worked out two frames ago is two frames stale. That is
 * the one thing here that breaks the rule the base class states, and it is
 * cheap enough to be worth the exception: arithmetic over a dozen rows, and
 * nothing else in the tree depends on where the content ended up.
 */
public class ScrollNode extends Node {

    private final Node child;

    private final Scroll scroll;

    private int content;

    public ScrollNode(Node child, Scroll scroll) {
        this.child = child;
        this.scroll = scroll;
    }

    /**
     * Takes all the room it is offered, and remembers how much the content
     * wanted.
     *
     * <p>No rounding to whole rows. A row sliced off by the bottom edge does
     * read as a drawing fault, but the answer to that is a window measured in
     * rows — {@code BODY} is built out of {@code ROWS * ROW_HEIGHT} and the
     * route list keeps a constant for the same reason — and a guarantee in how
     * something is sized beats a clamp that runs every frame to find nothing to
     * do. It would also be this class knowing what a row is, which is the one
     * thing it is for not knowing.
     */
    @Override
    public Box arrange(Box within) {
        content = child.arrange(within)
            .height();
        return box = within;
    }

    /**
     * Nothing outside the window answers — which is the cut made a second time,
     * without being written a second time.
     */
    @Override
    public Action hit(double x, double y) {
        return box.holds(x, y) ? child.hit(x, y) : null;
    }


    @Override
    public void paint(Paint paint) {
        int offset = scroll.at(content, box.height());
        child.arrange(new Box(box.x(), box.y() - offset, box.width(), content));

        paint.graphics()
            .enableScissor(box.x(), box.y(), box.right(), box.bottom());
        child.paint(paint.within(box));
        paint.graphics()
            .disableScissor();
    }

}
