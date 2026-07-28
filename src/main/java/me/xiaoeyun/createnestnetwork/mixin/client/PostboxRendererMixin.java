package me.xiaoeyun.createnestnetwork.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import me.xiaoeyun.createnestnetwork.content.transit.AddressLabels;
import me.xiaoeyun.createnestnetwork.registry.CnnPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Paints a postbox's flag in the transit livery when the port is an endpoint.
 *
 * The flag is the one part of a postbox a renderer draws — the box itself is a
 * baked model chosen by the block state, which is also what keeps all sixteen
 * dye colours working: the box stays whatever the player dyed it, and only the
 * flag says the port is a border.
 *
 * Nothing new is stored for this: the address already reaches the client for
 * the nameplate, and it is the address that decides the routing in the first
 * place, so a livery read off it cannot end up claiming something the port does
 * not do.
 *
 * The test is a leading label rather than the stricter one the screen's switch
 * uses. The switch has to be able to show a bare name and put it back, but a
 * port catches foreign packages on its head label alone — an address with a
 * path trailing behind one still takes transit traffic, and a box that took it
 * silently would be the block lying about itself.
 */
@Mixin(value = PostboxRenderer.class, remap = false)
public class PostboxRendererMixin {

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
