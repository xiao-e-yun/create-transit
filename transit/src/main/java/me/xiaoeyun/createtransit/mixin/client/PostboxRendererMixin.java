package me.xiaoeyun.createtransit.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import me.xiaoeyun.createtransit.client.TransitAddress;
import me.xiaoeyun.createtransit.registry.CtPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The two things a postbox's renderer decides: what its nameplate says, and what its flag is made
 * of. The flag cannot go with the rest of the livery — the box is a baked model taking its brass
 * from model data, but the flag is drawn here from a partial model of Create's, which model data
 * never reaches.
 */
@Mixin(value = PostboxRenderer.class, remap = false)
public class PostboxRendererMixin {

    @ModifyArg(
        method = "renderSafe(Lcom/simibubi/create/content/logistics/packagePort/postbox/PostboxBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagePort/postbox/PostboxRenderer;"
                + "renderNameplateOnHover(Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;"
                + "Lnet/minecraft/network/chat/Component;FLcom/mojang/blaze3d/vertex/PoseStack;"
                + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
        index = 1)
    private Component createTransit$plainNameplate(Component address) {
        return TransitAddress.spell(address);
    }

    @Redirect(
        method = "renderSafe(Lcom/simibubi/create/content/logistics/packagePort/postbox/PostboxBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/render/CachedBuffers;partial"
                + "(Ldev/engine_room/flywheel/lib/model/baked/PartialModel;"
                + "Lnet/minecraft/world/level/block/state/BlockState;)"
                + "Lnet/createmod/catnip/render/SuperByteBuffer;"))
    private SuperByteBuffer createTransit$paintEndpointFlag(PartialModel flag, BlockState state,
        PostboxBlockEntity postbox, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
        int overlay) {
        if (AddressLabels.startsWithLabel(postbox.addressFilter))
            flag = CtPartialModels.TRANSIT_POSTBOX_FLAG;
        return CachedBuffers.partial(flag, state);
    }

}
