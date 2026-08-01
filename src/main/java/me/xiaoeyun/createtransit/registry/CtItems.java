package me.xiaoeyun.createtransit.registry;

import static me.xiaoeyun.createtransit.CreateTransit.registrate;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;
import com.tterrag.registrate.util.entry.ItemEntry;

import me.xiaoeyun.createtransit.content.transit.TransitPackageItem;

public class CtItems {

    /**
     * One box per standard Create size, in {@code PackageStyles.STYLES} order.
     *
     * Mirrors {@code BuilderTransformers.packageItem}, which is how Create
     * registers its own fourteen: a stack of one, and the model is Create's own
     * {@code cardboard_WxH} with our texture on it. The {@code create:packages}
     * tag is added as a hand-written data file rather than through Registrate,
     * because this mod ships its resources by hand and never runs datagen.
     */
    public static final List<ItemEntry<TransitPackageItem>> TRANSIT_PACKAGES = registerPackages();

    private static List<ItemEntry<TransitPackageItem>> registerPackages() {
        List<ItemEntry<TransitPackageItem>> entries = new ArrayList<>();
        for (PackageStyle style : TransitPackageItem.STYLES)
            entries.add(registrate().item(TransitPackageItem.idOf(style),
                p -> new TransitPackageItem(p, style))
                .properties(p -> p.stacksTo(1))
                .register());
        return List.copyOf(entries);
    }

    public static void register() {}

}
