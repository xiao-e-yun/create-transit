package me.xiaoeyun.createroutes.client;

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

    /** Where the cursor has got to while the press that took this is held; there is no matching release. */
    default void drag(double mouseX, double mouseY) {}

}
