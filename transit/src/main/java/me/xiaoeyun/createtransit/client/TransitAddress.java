package me.xiaoeyun.createtransit.client;

import java.util.ArrayList;
import java.util.List;

import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import net.minecraft.network.chat.Component;

/**
 * How a labelled address is spelled out for a player to read. Every surface showing one puts it
 * through here, and the wire format's delimiters become the hops they stand for:
 *
 * <pre>
 * &lt;[depot]&gt; &lt;[north]&gt; drawer 4   reads as   depot → north → drawer 4
 * </pre>
 *
 * Nothing is dropped: labels are named in the order they will be peeled off and the path stays on
 * the end, even the path behind a label that routing ignores.
 */
public final class TransitAddress {

    /** One layer of label, in the same arrow Create puts in front of a package's address. */
    private static final String HOP = " → ";

    /**
     * What the default lane is called on screen: a blank label name is a real destination, and
     * left blank a nameplate would look unconfigured. The translation carries its own delimiters,
     * marking it as a name the game supplied.
     */
    private static final String DEFAULT_LANE = "create_transit.package_port.default_lane";

    private TransitAddress() {}

    /** What to show wherever the default lane needs a name. */
    public static Component defaultLane() {
        return Component.translatable(DEFAULT_LANE);
    }

    /** The address as a player should read it, or itself when unlabelled. */
    public static String spell(String address) {
        List<String> names = AddressLabels.labelNames(address);
        if (names.isEmpty())
            return address;
        List<String> hops = new ArrayList<>();
        for (String name : names)
            hops.add(name.isEmpty() ? defaultLane().getString() : name);
        String path = AddressLabels.path(address);
        if (!path.isEmpty())
            hops.add(path);
        return String.join(HOP, hops);
    }

    /**
     * The same, for a component somebody else built. An unlabelled address is
     * returned untouched rather than rebuilt, so nothing outside transit loses
     * the styling and siblings it arrived with.
     */
    public static Component spell(Component address) {
        String text = address.getString();
        if (!AddressLabels.startsWithLabel(text))
            return address;
        return Component.literal(spell(text));
    }

}
