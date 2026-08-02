package me.xiaoeyun.createtransit.content.transit;

import java.util.List;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;

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

        // transmuteCopy carries the whole component patch — address, contents,
        // order data, customs — onto the new item, which is exactly the old
        // "copy the tag" in 1.21 words.
        return box.transmuteCopy(sameSize(box, foreign ? TransitPackageItem.BOXES : PackageStyles.STANDARD_BOXES)
            .getItem(), box.getCount());
    }

    /**
     * The counterpart box of the same dimensions, so a shipment keeps its
     * shape across a border. A rare box measures like a standard one and so
     * maps like one; only a size nobody has a counterpart for falls back to
     * Create's own random pick.
     */
    private static ItemStack sameSize(ItemStack box, List<? extends PackageItem> pool) {
        PackageStyle style = ((PackageItem) box.getItem()).style;
        for (PackageItem candidate : pool)
            if (candidate.style.width() == style.width() && candidate.style.height() == style.height())
                return new ItemStack(candidate);
        return PackageStyles.getRandomBox();
    }

}
