package me.xiaoeyun.createtransit.mixin.client;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.packagePort.PackagePortMenu;
import com.simibubi.create.content.logistics.packagePort.PackagePortScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;

import me.xiaoeyun.createtransit.content.transit.PortEndpointToggle;
import me.xiaoeyun.createtransit.content.transit.TransitAddress;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Adds the transit endpoint switch to both package ports — frogports and
 * postboxes share this one screen, so a single mixin covers them.
 *
 * Purely presentational, and deliberately small: one button that changes how
 * the address box is read, and the delimiters wrapped around its contents on
 * the way to {@code PackagePortConfigurationPacket}. The packet and everything
 * server-side are untouched, and an address typed out by hand is still the same
 * string the button produces.
 */
@Mixin(PackagePortScreen.class)
public abstract class PackagePortScreenMixin extends AbstractSimiContainerScreen<PackagePortMenu> {

    @Shadow(remap = false)
    private EditBox addressBox;

    @Shadow(remap = false)
    private AllGuiTextures background;

    @Unique
    @Nullable
    private PortEndpointToggle createNestNetwork$endpoint;

    private PackagePortScreenMixin(PackagePortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /**
     * Vanilla has already put the whole stored address into the box by now, so
     * the switch reads {@code addressFilter} instead and sets the box itself —
     * the box may have clamped what it was handed, and an endpoint shows only
     * the bare name anyway.
     *
     * The switch sits to the left of the confirm button, four pixels clear of
     * it, rather than beside the two acceptance buttons on the left: those two
     * are one either-or choice, and joining that row would have read as a third
     * option in it. The four pixels are measured to the confirm's socket, which
     * the background art bakes in six pixels left of the button it holds.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void createNestNetwork$addEndpointToggle(CallbackInfo ci) {
        createNestNetwork$endpoint = new PortEndpointToggle(menu.contentHolder.addressFilter, addressBox,
            getGuiLeft() + background.getWidth() - 61, getGuiTop() + background.getHeight() - 24,
            this::addRenderableWidget);
    }

    /**
     * The placeholder vanilla draws in an empty address box is the port's own
     * item name, which says nothing about what an empty box means. With the
     * switch on it means the default lane — a real destination — so the box has
     * to say that, or a configured endpoint would look exactly like a port
     * nobody had touched.
     *
     * Redirecting the name rather than the drawing keeps every other decision
     * vanilla's: this is reached only inside the branch that already decided the
     * box is empty and unfocused, and the text is still drawn in the same place
     * in the same colour.
     */
    @Redirect(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
    private Component createNestNetwork$namePlaceholderForEndpoints(ItemStack icon) {
        if (createNestNetwork$endpoint != null && createNestNetwork$endpoint.isEndpoint())
            return TransitAddress.defaultLane();
        return icon.getHoverName();
    }

    @ModifyArg(method = "removed",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagePort/PackagePortConfigurationPacket;<init>"
                + "(Lnet/minecraft/core/BlockPos;Ljava/lang/String;Z)V",
            remap = false),
        index = 1)
    private String createNestNetwork$composeAddress(String boxValue) {
        return createNestNetwork$endpoint == null ? boxValue : createNestNetwork$endpoint.compose(boxValue);
    }

}
