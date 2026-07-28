package me.xiaoeyun.createnestnetwork.content.transit;

import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * How a package port spells its address out above itself.
 *
 * A port whose address is a transit label reads as a plain name, the same as
 * any other port — the delimiters are wire format, and a livery already says
 * this one is a border. Showing {@code <[factory]>} floating over the block
 * would put the grammar in front of players who never need to know it.
 *
 * Anything else is shown exactly as stored, oddities included. An address with
 * a path trailing behind a label is not a name and pretending otherwise would
 * hide that somebody typed something that does nothing.
 */
@OnlyIn(Dist.CLIENT)
public final class TransitNameplate {

    private TransitNameplate() {}

    public static Component plain(Component address) {
        String text = address.getString();
        if (!AddressLabels.isEndpoint(text))
            return address;
        return Component.literal(AddressLabels.headLabelName(text));
    }

}
