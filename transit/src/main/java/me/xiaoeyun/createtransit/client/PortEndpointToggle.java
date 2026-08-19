package me.xiaoeyun.createtransit.client;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;

import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Turns a package port into a transit endpoint: a door foreign packages are addressed to, rather
 * than an address inside the local domain.
 *
 * The button changes how the address box is read and nothing else. Off, the box holds an ordinary
 * address as vanilla left it; on, it holds a label name and {@code <[} {@code ]>} go around it on
 * the way out, so an endpoint's stored address is the string a player could have typed by hand.
 * Inside the switch a blank name is the default lane and {@code *} takes any label, one layer up
 * from what those two mean to a vanilla filter. There is no state behind the button.
 */
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
     * Switching changes what the text means and never the text itself, so {@code 工廠} reads as a
     * label one way and a plain address the other. An address already carrying label syntax is
     * wrapped as literally as any other; a port is matched on its head label alone.
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
