package me.xiaoeyun.createtransit.content.transit;

import java.util.List;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.box.PackageStyles.PackageStyle;

import net.minecraft.world.item.ItemStack;

/**
 * Which box a package should be wearing, decided by the address it carries: still labelled means
 * the transit box, anything else one of Create's.
 *
 * The swap replaces the stack rather than editing it, because an item stack's item is final — so
 * this returns the box to store, and every caller is somewhere the stack can be put back.
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

        ItemStack out = sameSize(box, foreign ? TransitPackageItem.BOXES : PackageStyles.STANDARD_BOXES);
        out.setCount(box.getCount());
        if (box.hasTag())
            out.setTag(box.getTag()
                .copy());
        return out;
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
