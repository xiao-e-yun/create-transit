package me.xiaoeyun.createtransit.client.ponder;

import java.util.List;

import com.simibubi.create.content.logistics.box.PackageItem;
import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import me.xiaoeyun.createtransit.content.transit.TransitCustoms;
import me.xiaoeyun.createtransit.content.transit.TransitPackaging;
import net.minecraft.world.item.ItemStack;

/**
 * The freight every storyboard ships and the addresses they ship it to. The
 * storyboards themselves live one class per block family beside this one.
 *
 * A Ponder world is client-side only, so none of the server logic that decides
 * those beats ever runs: every trigger is scripted with modifyBlockEntity, the
 * way Create's own {@code PonderHilo} scripts a packager. Kinetics are the
 * same deal — {@code BeltBlock.initBelt} returns early on the client, so the
 * schematics bake their own belt networks and every scene with a belt opens on
 * {@code setKineticSpeed(everywhere())}, which is also the one number to flip
 * if a belt turns out to run the wrong way in-game.
 *
 * What is not faked is the freight — every address, every customs declaration
 * and every box item on screen is produced by the same code that produces them
 * in a real world, so a scene cannot quietly show something the mod would never
 * do. Caption strings are the exception, and have to be: they are literals so
 * that the lang audit can compare them against en_us.
 *
 * Which is also why nothing here takes a caption. Ponder numbers captions by
 * the order the {@code text(...)} calls run, so one written into a shared
 * helper would be numbered wherever the helper happened to be called from.
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
     * One part of a declared order, carrying the parent slot it stands in for.
     *
     * A fragment names two orders: the child order it was packed under, and the
     * parent order across the border that it fulfils. Only one shipment is ever
     * on screen at a time, so both are constants.
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
