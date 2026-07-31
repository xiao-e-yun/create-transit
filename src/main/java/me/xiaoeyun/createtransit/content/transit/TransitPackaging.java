package me.xiaoeyun.createtransit.content.transit;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;

import me.xiaoeyun.createtransit.registry.CtItems;
import net.minecraft.world.item.ItemStack;

/**
 * Which box a package should be wearing, decided by the address it carries.
 *
 * One rule covers every case: a package whose address still begins with a
 * transit label has not cleared its last border, so it wears the transit box;
 * anything else wears one of Create's. That is deliberately the same fact the
 * routing already turns on, so the look cannot drift from the behaviour — a box
 * that looks foreign is foreign, by construction rather than by bookkeeping.
 *
 * The swap has to replace the stack rather than change it, because an item
 * stack's item is final. So this returns the box to store instead of editing
 * one in place, and every caller is somewhere the stack can actually be put
 * back: the packager's local, the gate's own hands.
 */
public final class TransitPackaging {

    private TransitPackaging() {}

    /**
     * The same package, in the box its address calls for. Returns the stack
     * unchanged whenever it is already right, which is the common case and
     * costs nothing.
     */
    public static ItemStack restyle(ItemStack box) {
        if (box.isEmpty() || !PackageItem.isPackage(box))
            return box;

        boolean foreign = AddressLabels.startsWithLabel(PackageItem.getAddress(box));
        boolean styled = box.getItem() instanceof TransitPackageItem;
        if (foreign == styled)
            return box;

        // A box coming home takes a random Create style rather than a fixed
        // one, because that is what Create does whenever it makes a box, and a
        // route that always produced the same cardboard would read as a tell.
        ItemStack out = foreign ? CtItems.TRANSIT_PACKAGE.asStack()
            : PackageStyles.getRandomBox();
        out.setCount(box.getCount());
        if (box.hasTag())
            out.setTag(box.getTag()
                .copy());
        return out;
    }

}
