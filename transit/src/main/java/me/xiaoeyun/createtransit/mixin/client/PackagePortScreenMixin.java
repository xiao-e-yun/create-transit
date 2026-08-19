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

import me.xiaoeyun.createtransit.client.PortEndpointToggle;
import me.xiaoeyun.createtransit.client.TransitAddress;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Adds the transit endpoint switch to both package ports — frogports and postboxes share this one
 * screen, so a single mixin covers them. Presentational only: the button changes how the address
 * box is read and wraps the delimiters around its contents on the way to
 * {@code PackagePortConfigurationPacket}, which is otherwise untouched.
 */
@Mixin(PackagePortScreen.class)
public abstract class PackagePortScreenMixin extends AbstractSimiContainerScreen<PackagePortMenu> {

    @Shadow(remap = false)
    private EditBox addressBox;

    @Shadow(remap = false)
    private AllGuiTextures background;

    @Unique
    @Nullable
    private PortEndpointToggle createTransit$endpoint;

    private PackagePortScreenMixin(PackagePortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /**
     * Vanilla has already put the whole stored address into the box by now, so
     * the switch reads {@code addressFilter} instead and sets the box itself —
     * the box may have clamped what it was handed, and an endpoint shows only
     * the bare name anyway.
     *
     * The four pixels of clearance are measured to the confirm button's socket, which the
     * background art bakes in six pixels left of the button it holds.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void createTransit$addEndpointToggle(CallbackInfo ci) {
        createTransit$endpoint = new PortEndpointToggle(menu.contentHolder.addressFilter, addressBox,
            getGuiLeft() + background.getWidth() - 61, getGuiTop() + background.getHeight() - 24,
            this::addRenderableWidget);
    }

    /**
     * Vanilla's placeholder for an empty address box is the port's own item name; with the switch
     * on, an empty box means the default lane, which is a real destination. Redirecting the name
     * rather than the drawing is reached only inside the branch that already decided the box is
     * empty and unfocused.
     */
    @Redirect(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
    private Component createTransit$namePlaceholderForEndpoints(ItemStack icon) {
        if (createTransit$endpoint != null && createTransit$endpoint.isEndpoint())
            return TransitAddress.defaultLane();
        return icon.getHoverName();
    }

    @ModifyArg(method = "removed",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagePort/PackagePortConfigurationPacket;<init>"
                + "(Lnet/minecraft/core/BlockPos;Ljava/lang/String;Z)V",
            remap = false),
        index = 1)
    private String createTransit$composeAddress(String boxValue) {
        return createTransit$endpoint == null ? boxValue : createTransit$endpoint.compose(boxValue);
    }

}
