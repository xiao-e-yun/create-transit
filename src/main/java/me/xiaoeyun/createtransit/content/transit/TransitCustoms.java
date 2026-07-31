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
 * shipment stands in for, one entry per border it has yet to clear.
 *
 * This is Create's own discipline applied one layer up. Vanilla never records
 * anywhere what an order should amount to — {@code IsFinal} and
 * {@code IsFinalLink} on the boxes are how a shipment states its own size, and
 * a repackager rebuilds the whole picture from the boxes in front of it every
 * pass. A declaration is the same idea for identity: the goods say what they
 * stand for, so nothing has to be remembered on their behalf, and a reload
 * cannot separate the two.
 *
 * <h2>Getting it onto the goods</h2>
 *
 * A ticker cannot write on the boxes — they are packed by the child network's
 * own packagers, and nothing a ticker hands to
 * {@code LogisticsManager#findPackagersForRequest} reaches a box as free-form
 * data. But it does not have to. It files its declaration against the child
 * order id on the way in, and {@code PackageItem#setOrder} — intercepted in
 * {@code PackageItemMixin} — reads it back as each box is stamped, because
 * that id is exactly what {@code setOrder} is handed. The key is the fact
 * itself rather than a convenient handle: a declaration says which parent slot
 * child order X stands for.
 *
 * A filing lives exactly as long as the call that makes it, and that is the
 * whole of its lifetime rule. Packing is synchronous — {@code
 * performPackageRequests} copies the requests into a list of its own and
 * drives the packager until they are gone — so every box of a child order is
 * stamped before the ticker's own call returns, and the filing is dropped in a
 * finally. Nothing here is ever written to disk or reloaded, so the state that
 * broke the old design — goods that outlived the record of what they were for
 * — has nowhere to form. Once a box is stamped, everything about it is on the
 * box, and nothing reads this table on its behalf ever again.
 *
 * <h2>Pairing</h2>
 *
 * A box may cross several borders, so it carries a stack of declarations,
 * outermost last, and each gate consumes the head — but only if the head
 * {@link #label} names that gate's border. Position alone would not do: a
 * label may have no declaration behind it, since a transit link on an ordinary
 * packager stamps one and files nothing, so the labels on the address and the
 * entries in this list are not the same height.
 *
 * Two borders on one route given the same sign will confuse that, and are
 * allowed to. The shipment waits at the wrong gate for siblings that are not
 * coming and the player empties the buffer — which is what Create already does
 * about two package ports sharing an address, and what a player naming two
 * doors alike has always been signing up for. Telling them apart would mean
 * every declaration also carrying the depth it was written at, a field earning
 * its keep in exactly one arrangement that the player can fix by renaming a
 * sign.
 *
 * <h2>Ordering rule</h2>
 *
 * {@code PackageItem#setOrder} clears a box's declarations when nothing is
 * filed for the order being stamped, so that a package item recycled by a
 * packager cannot leave wearing the previous shipment's paperwork. The one
 * place we stamp an identity ourselves — a gate merging a child order —
 * therefore stores its declarations <em>after</em> that call rather than before.
 */
public record TransitCustoms(String label, int parentOrderId, int parentLinkIndex, boolean parentIsFinalLink) {

    /** Root-level key. Never inside {@code Fragment}, which is vanilla's. */
    private static final String KEY = "TransitCustoms";

    /**
     * Declarations filed for a child order, by that order's id.
     *
     * Global because the lookup has to be: {@code setOrder} knows an order id
     * and nothing else — not which ticker filed it, not where, not when — so
     * the id has to be enough on its own. It is not shared state despite the
     * shape. Each entry has exactly one writer, one remover, and one set of
     * readers, all of them inside a single ticker's forward, and the key is a
     * random int no other ticker can name.
     */
    private static final Map<Integer, List<TransitCustoms>> FILED = new ConcurrentHashMap<>();

    // Filing

    /**
     * The declarations filed for {@code childOrderId}, outermost last, or empty
     * when nothing is.
     *
     * Read by a ticker crossing an inner border, to carry the outer border's
     * filing onto the order it is about to broadcast — the read that chains
     * borders together. Boxes are served by {@link #stampOnto} instead.
     */
    public static List<TransitCustoms> filedFor(int childOrderId) {
        return FILED.getOrDefault(childOrderId, List.of());
    }

    public static void file(int childOrderId, List<TransitCustoms> declarations) {
        FILED.put(childOrderId, declarations);
    }

    /**
     * Puts whatever is filed for {@code orderId} onto a box being stamped with
     * it. The one moment any of this touches a package.
     *
     * Clearing when nothing is filed is not housekeeping. A packager packing a
     * container that already holds a package reuses that item wholesale, tag
     * and all, so a box stamped for a shipment that declares nothing must be
     * stripped of whatever paperwork it was carrying from its previous life.
     */
    public static void stampOnto(ItemStack box, int orderId) {
        List<TransitCustoms> declarations = FILED.get(orderId);
        if (declarations == null) {
            clear(box);
            return;
        }
        store(box, declarations);
    }

    /**
     * Drops a filing now that its packing is over. Unconditional, and belongs
     * in a finally, so that a filing's whole life is the call that made it —
     * a rule you can check by reading {@code forwardSlot} rather than by
     * reasoning about who might still be holding what.
     *
     * The tempting refinement is to keep a filing nothing was stamped from, in
     * case its packing had been deferred past this call rather than having
     * simply found nothing to pack; from here the two are indistinguishable.
     * It insures against something rearranging Create's packaging, and costs a
     * record leaked on every forward that packed nothing, plus a lifetime rule
     * that takes a paragraph instead of a sentence. Packaging on 1.20.1 is
     * synchronous and no longer moving, and were an addon to defer it anyway
     * the failure would be total rather than intermittent: nothing would merge
     * at any border, and the first box anyone opened would be missing its tag.
     * Cheap to put back the day that stops being true.
     *
     * Counting the boxes still owed is the other tempting refinement, and is
     * not one either. Counting works — a link's boxes run 0..n and the last one
     * says so, so charging {@code +1} a box and {@code -(index + 1)} at each
     * link's last one nets a completed link to zero — but it cannot tell those
     * same two cases apart: a link that packed everything and a link that
     * packed nothing both contribute zero. What it does add is a way to leak
     * that looks like progress, because a link that runs out of items mid-way
     * never sends its last box and leaves a balance standing for good. (It
     * would also need a pin held for the duration of this call, or an early
     * link completing would zero the count while later ones were still to
     * come.)
     */
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

    public static void clear(ItemStack box) {
        if (box.hasTag())
            box.getTag()
                .remove(KEY);
    }

}
