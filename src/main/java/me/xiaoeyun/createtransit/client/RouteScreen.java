package me.xiaoeyun.createtransit.client;

import java.util.UUID;

import javax.annotation.Nullable;

/**
 * A screen this addon has taken over.
 *
 * <p>One question so far, asked from a mixin that cannot see this one's field:
 * which route is open. Create's screen is the target of two of ours, and an
 * interface on the screen is how the one that only suggests names reaches what
 * the one that draws the layout knows.
 */
public interface RouteScreen {

    /** Which route is being edited, or null when this is a train's own schedule. */
    @Nullable
    UUID editingRoute();

}
