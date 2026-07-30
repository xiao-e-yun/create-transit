package me.xiaoeyun.createtransit.content.transit;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Turns a package port into a transit endpoint: a door that foreign packages
 * are addressed to, rather than an address inside the local domain.
 *
 * The button switches how the address box is read, nothing more. Off, the box
 * holds an ordinary address exactly as vanilla left it. On, it holds a label
 * name, and {@code <[} {@code ]>} are put around it on the way out — so an
 * endpoint's stored address is the same string a player could type by hand, and
 * the delimiters never take up room in a field the player is typing into.
 *
 * The box's two vanilla-flavoured values keep their vanilla meanings inside the
 * switch, one layer up: blank is the default lane, taking border traffic that
 * names no door, the way a blank filter takes packages that name no address;
 * {@code *} takes any label, the way it takes any address. Leaving the name out
 * therefore does something now, which is the point — a switch that quietly did
 * nothing when the box was empty was a switch that looked broken.
 *
 * There is no state hiding behind the button, and no {@code <[} {@code ]>} on
 * screen either: the box shows a plain name in both positions, and the button
 * alone says whether that name is a transit label or an ordinary address.
 */
@OnlyIn(Dist.CLIENT)
public class PortEndpointToggle {

    private final EditBox addressBox;
    private final IconButton button;

    private boolean endpoint;

    public PortEndpointToggle(String address, EditBox addressBox, int x, int y,
        Consumer<AbstractWidget> register) {
        this.addressBox = addressBox;

        String name = endpointName(address);
        endpoint = name != null;

        button = new IconButton(x, y, AllIcons.I_TARGET);
        button.withCallback(this::toggle);
        register.accept(button);

        // Taken from the stored address rather than from the box, which was
        // handed the whole string and may have clamped it.
        addressBox.setValue(endpoint ? name : address);
        refresh();
    }

    /** Whether the box is currently being read as a transit label. */
    public boolean isEndpoint() {
        return endpoint;
    }

    /** The address to store for what the player left in the box. */
    public String compose(String boxValue) {
        return endpoint ? AddressLabels.endpoint(boxValue) : boxValue;
    }

    /**
     * The label name of an address that is one label and nothing else, or null
     * for anything a port would read as an ordinary address. A label with a
     * path behind it is not an endpoint: the path can never be matched against,
     * since a labelled address is compared on its head label alone.
     */
    @Nullable
    private static String endpointName(String address) {
        return AddressLabels.isEndpoint(address) ? AddressLabels.headLabelName(address) : null;
    }

    /**
     * Switching changes what the text means and never the text itself, so
     * {@code 工廠} reads as a label one way and as a plain address the other.
     * The delimiters stay out of sight — they belong to the wire format, and
     * nobody should have to learn them to press a button.
     *
     * An address that already carries label syntax is wrapped as literally as
     * any other, oddities and all. Whoever typed it chose it, and a port is
     * matched on its head label alone, so nothing downstream is any the wiser.
     */
    private void toggle() {
        endpoint = !endpoint;
        refresh();
    }

    private void refresh() {
        button.green = endpoint;
        button.getToolTip()
            .clear();
        button.getToolTip()
            .add(Component.translatable("create_transit.package_port.transit_endpoint"));
        button.getToolTip()
            .add(Component.translatable(endpoint
                ? "create_transit.package_port.endpoint_on"
                : "create_transit.package_port.endpoint_off")
                .withStyle(ChatFormatting.GRAY));
    }

}
