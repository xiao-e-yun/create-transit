package me.xiaoeyun.createtransit.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.LinkBulbRenderer;
import com.simibubi.create.content.redstone.displayLink.LinkWithBulbBlockEntity;

import me.xiaoeyun.createtransit.content.ticker.TransitTickerBlockEntity;
import me.xiaoeyun.createtransit.content.transit.TransitLinkBlockEntity;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.Level;

/**
 * The two things a link's bulb has to say that Create's own never had to.
 *
 * <p><b>Red while unlabelled.</b> A transit link without a label is not a link
 * that is idle, it is a link that cannot do its job: it stamps nothing and
 * silently behaves like a plain Stock Link. That was only visible through
 * goggles, which is the wrong place for a fault a player wants to spot while
 * walking past. The bulb is the natural place to say so, and the glow layer is
 * already tinted per frame — Create passes the same value three times for a
 * neutral white — so this costs no model and no texture, only different
 * arguments. The steady half belongs to the block entity, which holds
 * {@code getGlow} at full for a blank label instead of letting it decay: a
 * missing label is a standing condition, not the event the pulse was built for.
 *
 * <p><b>A heartbeat on a mounting point.</b> A link attached to a Transit Ticker
 * never blinks on its own. Nothing is packed there — under flattened mounting
 * the child's own links take the assignments and blink at their own packagers,
 * and across a border the ticker forwards into a fresh child order — so a
 * mounting point sits dark however much traffic crosses it. The heartbeat says
 * the ticker is there and polling, which is the one thing the block does
 * continuously, and it stays white deliberately: the ticker and its link are
 * ours, and periodic work is still work, so it belongs in the same vocabulary
 * rather than beside it.
 *
 * <p>Both halves land on the same two values Create already computes, so the
 * precedence falls out for free: an unlabelled link is pinned at full glow, and
 * {@link Math#max} cannot raise that, so a fault outranks a heartbeat with no
 * code to say so.
 *
 * <p>Every other link in the game reaches these methods too, hence the type
 * checks.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = LinkBulbRenderer.class, remap = false)
public class LinkBulbRendererMixin {

    private static final String RENDER_SAFE =
        "renderSafe(Lcom/simibubi/create/content/redstone/displayLink/LinkWithBulbBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    /** How much green and blue survive, leaving red. */
    private static final int DAMPEN = 4;

    /** Ticks between heartbeats. */
    private static final int PERIOD = 60;

    /**
     * How the glow falls away over the blink, per tick. Create's own pulse
     * decays on an EXP chaser at this same rate, so a heartbeat reads as the
     * blink a player already knows rather than a second, unfamiliar animation.
     */
    private static final double DECAY = 0.5;

    /**
     * The last tick of a blink that still draws. {@code DECAY^3} is exactly the
     * {@code 0.125f} below which {@link LinkBulbRenderer} skips the glow layer
     * outright, so this is Create's own cutoff restated rather than a taste —
     * and it keeps the mounting-point lookup below off all but four ticks in
     * sixty.
     */
    private static final int BLINK = 3;

    /**
     * Raises the glow while a mounting point is between beats.
     *
     * Reading the beat off the clock is what keeps this to one client-side
     * file: the brightness is a function of the tick, the one condition it
     * depends on is a block lookup the client can do itself, and so there is no
     * packet, no synced field and no server-side code behind any of it.
     */
    @Redirect(method = RENDER_SAFE,
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/redstone/displayLink/LinkWithBulbBlockEntity;getGlow(F)F"))
    private float createNestNetwork$heartbeatOnMountingPoints(LinkWithBulbBlockEntity be, float partialTicks) {
        float glow = be.getGlow(partialTicks);
        float beat = heartbeat(be, partialTicks);
        if (beat <= glow)
            return glow;
        return isMountingPoint(be) ? beat : glow;
    }

    /**
     * The blink, as a plain function of the tick.
     *
     * The phase is offset by the block's own position so that a room full of
     * mounting points does not blink in unison — each ticker polls on a
     * lazyTick phase of its own, and the bulbs saying so in chorus would claim
     * a coordination that is not there.
     */
    private static float heartbeat(LinkWithBulbBlockEntity be, float partialTicks) {
        Level level = be.getLevel();
        if (level == null)
            return 0;
        int offset = Math.floorMod(be.getBlockPos()
            .asLong(), PERIOD);
        float phase = Math.floorMod(level.getGameTime() + offset, PERIOD) + partialTicks;
        return phase > BLINK ? 0 : (float) Math.pow(DECAY, phase);
    }

    /**
     * Whether this link mounts a network onto a ticker. Create's own
     * {@code getPackager} is what decides it, which is what makes one test
     * cover both modes — the vanilla Stock Link of a domestic mounting and the
     * Transit Link of a border alike — and hands us its exclusions for free: it
     * already refuses repackagers, and already returns null for a link silenced
     * by full redstone, which is exactly when the ticker stops mounting.
     */
    private static boolean isMountingPoint(LinkWithBulbBlockEntity be) {
        return be instanceof PackagerLinkBlockEntity link && link.getPackager() instanceof TransitTickerBlockEntity;
    }

    @Redirect(method = RENDER_SAFE,
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
