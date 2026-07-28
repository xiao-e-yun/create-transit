package me.xiaoeyun.createnestnetwork.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.simibubi.create.content.logistics.packagePort.frogport.FrogportRenderer;

import me.xiaoeyun.createnestnetwork.content.transit.TransitNameplate;
import net.minecraft.network.chat.Component;

/**
 * Keeps a frogport's hovering nameplate free of label delimiters, the same as
 * a postbox's.
 */
@Mixin(value = FrogportRenderer.class, remap = false)
public class FrogportRendererMixin {

    @ModifyArg(
        method = "renderSafe(Lcom/simibubi/create/content/logistics/packagePort/frogport/FrogportBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagePort/frogport/FrogportRenderer;"
                + "renderNameplateOnHover(Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;"
                + "Lnet/minecraft/network/chat/Component;FLcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
        index = 1)
    private Component createNestNetwork$plainNameplate(Component address) {
        return TransitNameplate.plain(address);
    }

}
