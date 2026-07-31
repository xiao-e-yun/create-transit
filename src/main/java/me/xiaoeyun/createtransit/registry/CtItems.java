package me.xiaoeyun.createtransit.registry;

import static me.xiaoeyun.createtransit.CreateTransit.registrate;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import me.xiaoeyun.createtransit.content.transit.TransitPackageItem;

public class CtItems {

    /**
     * Mirrors {@code BuilderTransformers.packageItem}, which is how Create
     * registers its own fourteen: a stack of one, and the model is the rare
     * boxes' shared parent with our texture on it. The {@code create:packages}
     * tag is added as a hand-written data file rather than through Registrate,
     * because this mod ships its resources by hand and never runs datagen.
     */
    public static final ItemEntry<TransitPackageItem> TRANSIT_PACKAGE = registrate()
        .item("transit_package", p -> new TransitPackageItem(p, TransitPackageItem.STYLE))
        .properties(p -> p.stacksTo(1))
        .register();

    /** True when this is the box a package wears while it is still foreign. */
    public static boolean isTransit(PackageItem item) {
        return item instanceof TransitPackageItem;
    }

    public static void register() {}

}
