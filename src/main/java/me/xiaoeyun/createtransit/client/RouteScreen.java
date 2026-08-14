package me.xiaoeyun.createtransit.client;

import java.util.UUID;

import javax.annotation.Nullable;

/** A screen this addon has taken over. */
public interface RouteScreen {

    /** Which route is being edited, or null when this is a train's own schedule. */
    @Nullable
    UUID editingRoute();

}
