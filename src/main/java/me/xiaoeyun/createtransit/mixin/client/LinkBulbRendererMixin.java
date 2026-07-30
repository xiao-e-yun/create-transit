package me.xiaoeyun.createtransit.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.redstone.displayLink.LinkBulbRenderer;
import com.simibubi.create.content.redstone.displayLink.LinkWithBulbBlockEntity;

import me.xiaoeyun.createtransit.content.transit.TransitLinkBlockEntity;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Paints a transit link's bulb red while it has no label.
 *
 * A link without one is not a link that is idle, it is a link that cannot do
 * its job: it stamps nothing and silently behaves like a plain Stock Link. That
 * was only visible through goggles, which is the wrong place for a fault a
 * player wants to spot while walking past.
 *
 * The bulb is the natural place to say so, and the glow layer is already tinted
 * per frame — Create passes the same value three times for a neutral white — so
 * this costs no model and no texture, only different arguments. The steady half
 * belongs to the block entity, which holds {@code getGlow} at full for a blank
 * label instead of letting it decay: a missing label is a standing condition,
 * not the event the pulse was built for. Between them the vocabulary stays
 * legible — a white blink is work happening, a red hold is a fault.
 *
 * Every other link in the game reaches this method too, hence the type check.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = LinkBulbRenderer.class, remap = false)
public class LinkBulbRendererMixin {

    /** How much green and blue survive, leaving red. */
    private static final int DAMPEN = 4;

    @Redirect(
        method = "renderSafe(Lcom/simibubi/create/content/redstone/displayLink/LinkWithBulbBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/render/SuperByteBuffer;color(IIII)Lnet/createmod/catnip/render/SuperByteBuffer;"))
    private SuperByteBuffer createNestNetwork$redWhileUnlabelled(SuperByteBuffer glow, int red, int green, int blue,
        int alpha, LinkWithBulbBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
        int overlay) {
        if (be instanceof TransitLinkBlockEntity link && link.getLabel()
            .isBlank())
            return glow.color(red, green / DAMPEN, blue / DAMPEN, alpha);
        return glow.color(red, green, blue, alpha);
    }

}
