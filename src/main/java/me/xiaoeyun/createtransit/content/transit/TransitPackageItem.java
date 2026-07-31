package me.xiaoeyun.createtransit.content.transit;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;

/**
 * The box a package wears while it is still foreign.
 *
 * Create invites this: {@code PackageStyles.STYLES} says in as many words that
 * an addon should register its own {@link PackageItem} rather than insert into
 * that list. Being a real package item rather than a look is what makes it work
 * everywhere — half of Create's package rendering never sees NBT, because it
 * asks {@code AllPartialModels.PACKAGES} for a model by <em>item id</em>. A
 * separate item is visible to that lookup, so belts, frogports, chain
 * conveyors, dropped entities and both Flywheel paths all show it without a
 * single mixin. Nothing carried in a tag could have done that.
 */
public class TransitPackageItem extends PackageItem {

    /**
     * Twelve by ten, which is what every one of Create's rare boxes is, so
     * {@code create:item/package/custom_12x10} and its rigging model both fit
     * without drawing any geometry of our own.
     *
     * The type name never reaches a resource location — {@code getItemId()} and
     * {@code getRiggingModel()} both build paths in Create's namespace, so this
     * style is only ever asked for its measurements.
     */
    public static final PackageStyle STYLE = new PackageStyle("transit", 12, 10, 21f, false);

    public TransitPackageItem(Properties properties, PackageStyle style) {
        super(properties, style);

        // PackageItem's constructor files every instance into the lists
        // getRandomBox draws from, which would have ordinary packagers all over
        // the world posting transit boxes at random. Leaving is the whole fix:
        // isPackage is an instanceof and consults no list, so nothing else
        // notices. getDefaultBox takes ALL_BOXES.get(0), so that one matters
        // too even though we would not be first today.
        PackageStyles.ALL_BOXES.remove(this);
        PackageStyles.STANDARD_BOXES.remove(this);
        PackageStyles.RARE_BOXES.remove(this);
    }

    /**
     * Create's own description id is hard-coded to its namespace, so every
     * package in the game — ours included — would otherwise be called Package.
     */
    @Override
    public String getDescriptionId() {
        return "item.create_transit.transit_package";
    }

}
