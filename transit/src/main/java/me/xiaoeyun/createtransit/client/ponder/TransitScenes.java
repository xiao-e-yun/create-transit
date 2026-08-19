package me.xiaoeyun.createtransit.client.ponder;

import java.util.List;

import com.simibubi.create.content.logistics.box.PackageItem;
import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import me.xiaoeyun.createtransit.content.transit.TransitCustoms;
import me.xiaoeyun.createtransit.content.transit.TransitPackaging;
import net.minecraft.world.item.ItemStack;

/**
 * The freight every storyboard ships and the addresses they ship it to, and the rules all of them
 * follow. The storyboards themselves live one class per block family beside this one.
 *
 * A Ponder world is client-side only, so no server logic runs: every trigger is scripted with
 * modifyBlockEntity, the way Create's own {@code PonderHilo} scripts a packager. Kinetics are the
 * same — {@code BeltBlock.initBelt} returns early on the client, so the schematics bake their own
 * belt networks and a storyboard restates them with {@code setKineticSpeed}, one call per belt run
 * rather than per scene, since belts turning a corner run at opposite signs.
 *
 * Caption strings have to be literals so the lang audit can compare them against en_us, and Ponder
 * numbers them positionally: it counts the {@code text(...)} calls in execution order into
 * {@code create_transit.ponder.<scene>.text_N}. A caption therefore stays one per beat at the top
 * level of its storyboard — never in a loop, a branch or a shared helper.
 */
public class TransitScenes {

    /** The border every scene routes through, and the local address behind it. */
    static final String BORDER = "depot";
    static final String DESTINATION = "drawer 4";
    private static final int CHILD_ORDER = 21;
    static final int PARENT_ORDER = 7;

    /** A package as the mod would have produced it: addressed first, then wearing whatever livery that address calls for. */
    static ItemStack box(String address, ItemStack contents) {
        ItemStack box = PackageItem.containing(List.of(contents));
        PackageItem.addAddress(box, address);
        return TransitPackaging.restyle(box);
    }

    /**
     * One part of a declared order, naming both the child order it was packed under and the parent
     * order across the border it fulfils. Only one shipment is ever on screen, so both are
     * constants.
     */
    static ItemStack fragment(ItemStack contents, int index, boolean last) {
        String address = AddressLabels.push(BORDER, DESTINATION);
        ItemStack box = PackageItem.containing(List.of(contents));
        PackageItem.addAddress(box, address);
        PackageItem.setOrder(box, CHILD_ORDER, 0, true, index, last, null);
        TransitCustoms.store(box, List.of(TransitCustoms.of(address, PARENT_ORDER, 0, true)));
        return TransitPackaging.restyle(box);
    }

}
