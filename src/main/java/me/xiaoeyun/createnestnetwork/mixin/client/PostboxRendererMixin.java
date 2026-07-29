package me.xiaoeyun.createnestnetwork.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import me.xiaoeyun.createnestnetwork.content.transit.AddressLabels;
import me.xiaoeyun.createnestnetwork.content.transit.TransitAddress;
import me.xiaoeyun.createnestnetwork.registry.CnnPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The two things a postbox's renderer decides: what its nameplate says, and
 * what its flag is made of.
 *
 * The flag has to be handled here rather than with the rest of the livery. The
 * box is a baked model and takes its brass from model data, but the flag is
 * drawn by this renderer from a partial model of Create's, which no amount of
 * model data reaches. It is also the only part of a postbox that was going to
 * carry the network's colour at all — the box belongs to whoever dyed it, and
 * the frame is where the brass went.
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
    private Component createNestNetwork$plainNameplate(Component address) {
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
    private SuperByteBuffer createNestNetwork$paintEndpointFlag(PartialModel flag, BlockState state,
        PostboxBlockEntity postbox, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
        int overlay) {
        if (AddressLabels.startsWithLabel(postbox.addressFilter))
            flag = CnnPartialModels.TRANSIT_POSTBOX_FLAG;
        return CachedBuffers.partial(flag, state);
    }

}
