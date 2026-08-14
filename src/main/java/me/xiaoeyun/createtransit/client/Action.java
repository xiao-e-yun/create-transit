package me.xiaoeyun.createtransit.client;

import net.minecraft.client.gui.GuiGraphics;

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
