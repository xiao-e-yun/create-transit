package me.xiaoeyun.createtransit.content.transit;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;

/**
 * The box a package wears while it is still foreign, in each of Create's four
 * standard sizes.
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
     * Our counterpart to each of Create's standard styles, measurements and
     * rigging offset included, so a size that changes upstream changes here.
     * The type name never reaches a resource location — {@code getItemId()} and
     * {@code getRiggingModel()} both build paths in Create's namespace, so a
     * style is only ever asked for its numbers.
     */
    public static final List<PackageStyle> STYLES = PackageStyles.STYLES.stream()
        .filter(style -> !style.rare())
        .map(style -> new PackageStyle("transit", style.width(), style.height(), style.riggingOffset(), false))
        .toList();

    /** Our own boxes, since the constructor takes them back out of Create's lists. */
    public static final List<TransitPackageItem> BOXES = new ArrayList<>();

    /** Mirrors Create's {@code package_WxH} naming, one id per size. */
    public static String idOf(PackageStyle style) {
        return "transit_package_" + style.width() + "x" + style.height();
    }

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
        BOXES.add(this);
    }

    /**
     * One name for every size, as Create names all four of its own
     * {@code item.create.package}, and its own id is hard-coded to its
     * namespace so ours would otherwise read "Cardboard Package".
     */
    @Override
    public String getDescriptionId() {
        return "item.create_transit.transit_package";
    }

}
