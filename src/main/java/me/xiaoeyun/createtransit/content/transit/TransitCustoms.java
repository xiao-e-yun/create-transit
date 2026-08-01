package me.xiaoeyun.createtransit.content.transit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * The customs declaration a package carries: which parent order slot this
 * shipment stands in for, one entry per border it has yet to clear. The goods
 * say what they stand for, so nothing is remembered on their behalf and a
 * reload cannot separate the two.
 *
 * A ticker cannot write on boxes packed by the child network, so it files its
 * declaration against the child order id and {@code PackageItem#setOrder} —
 * intercepted in {@code PackageItemMixin} — stamps it onto each box. Packing is
 * synchronous, so a filing lives exactly as long as the call that makes it.
 *
 * A box crossing several borders carries a stack of declarations, outermost
 * last, and each gate consumes the head only if its {@link #label} names that
 * gate's border: a label may have no declaration behind it, so the two stacks
 * are not the same height. Two borders on one route sharing a sign will confuse
 * that, as two package ports sharing an address already do in vanilla.
 */
public record TransitCustoms(String label, int parentOrderId, int parentLinkIndex, boolean parentIsFinalLink) {

    /** Root-level key. Never inside {@code Fragment}, which is vanilla's. */
    private static final String KEY = "TransitCustoms";

    /** Declarations filed for a child order, by that order's id — global because {@code setOrder} knows only the id. */
    private static final Map<Integer, List<TransitCustoms>> FILED = new ConcurrentHashMap<>();

    // Filing

    /** The declarations filed for {@code childOrderId}, outermost last — the read that chains borders together. */
    public static List<TransitCustoms> filedFor(int childOrderId) {
        return FILED.getOrDefault(childOrderId, List.of());
    }

    public static void file(int childOrderId, List<TransitCustoms> declarations) {
        FILED.put(childOrderId, declarations);
    }

    /**
     * Puts whatever is filed for {@code orderId} onto a box being stamped with
     * it. Clears otherwise, because a packager reuses a package item it finds in
     * the container, tag and all.
     */
    public static void stampOnto(ItemStack box, int orderId) {
        List<TransitCustoms> declarations = FILED.get(orderId);
        if (declarations == null) {
            clear(box);
            return;
        }
        store(box, declarations);
    }

    /** Drops a filing now that its packing is over; belongs in a finally, so a filing's whole life is the call that made it. */
    public static void close(int childOrderId) {
        FILED.remove(childOrderId);
    }

    /**
     * The declaration a ticker crossing the border {@code address} names should
     * write, or null when the address names no border for a gate to answer to.
     */
    @Nullable
    public static TransitCustoms of(String address, int parentOrderId, int parentLinkIndex,
        boolean parentIsFinalLink) {
        String label = AddressLabels.headLabelName(address);
        return label == null ? null
            : new TransitCustoms(label, parentOrderId, parentLinkIndex, parentIsFinalLink);
    }

    // Package side

    public static List<TransitCustoms> on(ItemStack box) {
        if (!box.hasTag())
            return List.of();
        List<TransitCustoms> declarations = new ArrayList<>();
        for (Tag entryTag : box.getTag()
            .getList(KEY, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) entryTag;
            declarations.add(new TransitCustoms(entry.getString("Label"), entry.getInt("OrderId"),
                entry.getInt("LinkIndex"), entry.getBoolean("IsFinalLink")));
        }
        return declarations;
    }

    /**
     * The declaration this box carries for the border {@code address} is
     * standing at, or null when it carries none for it. Takes the address
     * rather than the label so that {@link #answersTo} stays the only place
     * that decides what a border is called.
     */
    @Nullable
    public static TransitCustoms head(ItemStack box, String address) {
        List<TransitCustoms> declarations = on(box);
        return declarations.isEmpty() || !answersTo(declarations.get(0), address) ? null : declarations.get(0);
    }

    /** What remains once the declaration for {@code address}, if any, is consumed. */
    public static List<TransitCustoms> pop(ItemStack box, String address) {
        List<TransitCustoms> declarations = on(box);
        if (declarations.isEmpty() || !answersTo(declarations.get(0), address))
            return declarations;
        return declarations.subList(1, declarations.size());
    }

    private static boolean answersTo(TransitCustoms declaration, String address) {
        String label = AddressLabels.headLabelName(address);
        return label != null && declaration.label()
            .equals(label);
    }

    public static void store(ItemStack box, List<TransitCustoms> declarations) {
        if (declarations.isEmpty()) {
            clear(box);
            return;
        }
        ListTag list = new ListTag();
        for (TransitCustoms declaration : declarations) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Label", declaration.label());
            entry.putInt("OrderId", declaration.parentOrderId());
            entry.putInt("LinkIndex", declaration.parentLinkIndex());
            entry.putBoolean("IsFinalLink", declaration.parentIsFinalLink());
            list.add(entry);
        }
        box.getOrCreateTag()
            .put(KEY, list);
    }

    private static void clear(ItemStack box) {
        if (box.hasTag())
            box.getTag()
                .remove(KEY);
    }

}
