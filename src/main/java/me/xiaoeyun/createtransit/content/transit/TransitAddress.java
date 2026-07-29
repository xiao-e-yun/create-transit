package me.xiaoeyun.createtransit.content.transit;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * How a labelled address is spelled out for a player to read.
 *
 * {@code <[} and {@code ]>} are wire format. They exist so the grammar can find
 * where a label ends, and a player who never types one has no reason to learn
 * them — so every surface that shows an address to somebody puts it through
 * here, and the delimiters become the hops they stand for:
 *
 * <pre>
 * &lt;[depot]&gt; &lt;[north]&gt; drawer 4   reads as   depot → north → drawer 4
 * </pre>
 *
 * Nothing is dropped in the translation. Every label is named in the order it
 * will be peeled off and the path stays on the end, so an address with a path
 * behind a label still shows that path — the routing ignores it, and a spelling
 * that quietly swallowed it would be the reason nobody could work out why.
 */
@OnlyIn(Dist.CLIENT)
public final class TransitAddress {

    /**
     * Reads as "and then", which is what one layer of label means. The same
     * arrow Create puts in front of a package's address, so a spelled address
     * reads as one chain rather than as a prefix and a different notation.
     */
    private static final String HOP = " → ";

    private TransitAddress() {}

    /** The address as a player should read it, or itself when unlabelled. */
    public static String spell(String address) {
        List<String> hops = new ArrayList<>(AddressLabels.labelNames(address));
        if (hops.isEmpty())
            return address;
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
