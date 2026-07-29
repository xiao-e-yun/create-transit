package me.xiaoeyun.createnestnetwork.mixin.client;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.packagePort.PackagePortMenu;
import com.simibubi.create.content.logistics.packagePort.PackagePortScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;

import me.xiaoeyun.createnestnetwork.content.transit.PortEndpointToggle;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

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
     * option in it.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void createNestNetwork$addEndpointToggle(CallbackInfo ci) {
        createNestNetwork$endpoint = new PortEndpointToggle(menu.contentHolder.addressFilter, addressBox,
            getGuiLeft() + background.getWidth() - 55, getGuiTop() + background.getHeight() - 24,
            this::addRenderableWidget);
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
