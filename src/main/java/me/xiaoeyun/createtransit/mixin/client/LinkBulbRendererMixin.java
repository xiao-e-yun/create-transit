package me.xiaoeyun.createtransit.mixin.client;

import javax.annotation.Nullable;

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
 * The three things a link's bulb has to say that Create's own never had to.
 *
 * <p><b>Dark while inert.</b> Two conditions stop a link from doing anything at
 * all: a transit link with no label stamps no border, and a mounting point whose
 * binding reaches nothing has no network to poll. Neither gets a blink, because
 * a blink means work happened and none did.
 *
 * <p>Both are made dark here, by refusing the heartbeat, and the unlabelled case
 * needs that as well as its block entity returning zero glow — not instead of
 * it. The heartbeat is offered <em>over</em> whatever the link reports, through
 * {@link Math#max}, so zeroing a link's own glow only silences it while nothing
 * is being offered on top. A blank label pins the link's own contribution at
 * nothing; this file is what decides nothing is added to it.
 *
 * <p>Below Create's glow threshold no bulb is drawn at all, so an inert block
 * ends up looking exactly like the idle link it has become. That is the price
 * of the paragraph below and is paid on purpose.
 *
 * <p><b>A heartbeat on a mounting point.</b> A link attached to a Transit Ticker
 * never blinks on its own. Nothing is packed there — under flattened mounting
 * the child's own links take the assignments and blink at their own packagers,
 * and across a border the ticker forwards into a fresh child order — so a
 * mounting point sits dark however much traffic crosses it. The heartbeat says
 * the ticker is there and polling, which is the one thing the block does
 * continuously, and it stays white deliberately: the ticker and its link are
 * ours, and periodic work is still work, so it belongs in the same vocabulary
 * rather than beside it. It is withheld when the binding reaches nothing, which
 * is what makes silence a reading of its own: a dark bulb on a ticker says
 * unbound or unloaded, rather than merely saying no traffic.
 *
 * <p><b>Red on a proxy cycle.</b> The heartbeat's own premise is what makes this
 * necessary: a mounting point beats the same contented white whether the network
 * below it is serving stock or feeding back into the one above and contributing
 * nothing. The ticker has known about the cycle all along — it recomputes the
 * flag every lazyTick for its goggle tooltip — but under a domestic mounting the
 * block a player is looking at is a <em>vanilla</em> Stock Link, which has no
 * fault vocabulary of its own and no block entity of ours to put one in. Reading
 * the flag off the ticker through the link is what lets an unmodified block
 * report our fault, and it is why this state lives in the renderer rather than
 * beside the other two in the block entity. It is asked per frequency, not per
 * ticker: a mounting point can wear several links on unrelated networks, and a
 * loop closing through one of them is no reason to accuse the rest.
 *
 * <p>Red now means exactly one thing, which is the point of the first paragraph
 * giving its own colour up. Inert is dark, working is white, and the single red
 * left is the one fault a player cannot find by looking — a loop spanning
 * chunks with nothing to point at.
 *
 * <p>All three land on the same two values Create already computes, so the
 * precedence falls out for free: the flash squares the glow outright while the
 * heartbeat only offers through {@link Math#max}, and the tint is decided by
 * the same test the flash is — so a cycle outranks everything with no code to
 * say so.
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

    /**
     * The fault colour, as the tint handed to an additive glow layer.
     *
     * Full red rather than Create's own brightness with the other two channels
     * dimmed. Two things were washing the old tint out. The layer is drawn
     * additively, so whatever survives in green and blue is added straight onto
     * the background and drags the result back towards white — over a lit brass
     * casing a quarter of each was enough to read as pink. And the brightness
     * this tint scales was never full to begin with: Create maps glow through
     * {@code 1 - 2*(glow - .75)^2} and then onto 200, so a held bulb peaks at
     * 175, not 255.
     *
     * Pinning the red rather than passing Create's value through costs no
     * animation, because the tint was never where a fault's animation lives:
     * Create skips the glow layer outright below {@code .125f}, so what the
     * glow value decides for a fault is whether the bulb draws at all, and both
     * faults use exactly that — a blank label pins it lit, a cycle squares it
     * on and off. Brightness in between would only ever be a dimmer red.
     */
    private static final int FAULT_RED = 255;
    private static final int FAULT_GREEN = 0;
    private static final int FAULT_BLUE = 0;

    /**
     * Ticks in one flash of a cycle, half of them lit.
     *
     * Two and a half hertz: fast enough that it cannot be mistaken for the
     * sixty-tick heartbeat it replaces, slow enough to stay short of the rate
     * at which a flashing light stops being a warning and becomes a strobe.
     */
    private static final int FLASH = 8;

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
     * outright, so this is Create's own cutoff restated rather than a taste:
     * past it the beat is contributing nothing a player could see anyway.
     */
    private static final int BLINK = 3;

    /**
     * Flashes a mounting point's bulb through a cycle, and raises it between
     * beats otherwise.
     *
     * Reading both off the clock is what keeps this to one client-side file:
     * the brightness is a function of the tick, the one condition it depends on
     * is a block lookup the client can do itself, and so there is no packet, no
     * synced field and no server-side code behind any of it. The cycle adds a
     * lookup but no traffic either — the ticker already syncs the flag for its
     * own goggle tooltip.
     */
    @Redirect(method = RENDER_SAFE,
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/redstone/displayLink/LinkWithBulbBlockEntity;getGlow(F)F"))
    private float createNestNetwork$faultOrBeatOnMountingPoints(LinkWithBulbBlockEntity be, float partialTicks) {
        float glow = be.getGlow(partialTicks);
        TransitTickerBlockEntity ticker = mountedTicker(be);
        if (ticker == null)
            return glow;
        if (isCycling(ticker, be))
            return flash(ticker, partialTicks);
        // Nothing to report being alive about, from either end: a binding that
        // reaches nothing has no poll worth announcing, and an unlabelled link
        // is not part of the poll being announced — the ticker never counts it
        // as a border, so it is standing on the mounting rather than making
        // one. Refusing the heartbeat is what actually makes these dark;
        // zeroing the link's own glow does not, because the heartbeat is
        // offered over the top of it and Math#max would hand it straight back.
        if (!ticker.isChildConnected() || isInert(be))
            return glow;
        return Math.max(glow, heartbeat(ticker, partialTicks));
    }

    /**
     * The cycle flash, as a plain function of the tick.
     *
     * Deliberately <em>not</em> offset by position the way the heartbeat is.
     * The heartbeat desyncs because each ticker polls on a phase of its own and
     * a chorus would claim a coordination that is not there; a loop is the
     * opposite case — every link it closes through is the same one fault,
     * however many tickers and chunks it spans, so those bulbs saying so in
     * unison is the true reading. Which links those are is decided elsewhere,
     * and is not all of them.
     */
    private static float flash(TransitTickerBlockEntity ticker, float partialTicks) {
        Level level = ticker.getLevel();
        if (level == null)
            return 1;
        float phase = Math.floorMod(level.getGameTime(), FLASH) + partialTicks;
        return phase < FLASH / 2f ? 1 : 0;
    }

    /**
     * The blink, as a plain function of the tick.
     *
     * The phase is offset by position so that a room full of mounting points
     * does not blink in unison — each ticker polls on a lazyTick phase of its
     * own, and the bulbs saying so in chorus would claim a coordination that is
     * not there. The position offsetting it is the <em>ticker's</em>, not the
     * link's, because the poll being reported is the ticker's: several links on
     * one ticker beat together because there is one poll behind them, and only
     * separate tickers drift apart.
     */
    private static float heartbeat(TransitTickerBlockEntity ticker, float partialTicks) {
        Level level = ticker.getLevel();
        if (level == null)
            return 0;
        int offset = Math.floorMod(ticker.getBlockPos()
            .asLong(), PERIOD);
        float phase = Math.floorMod(level.getGameTime() + offset, PERIOD) + partialTicks;
        return phase > BLINK ? 0 : (float) Math.pow(DECAY, phase);
    }

    /**
     * The ticker this link mounts a network onto, or null if it mounts none.
     * Create's own {@code getPackager} is what decides it, which is what makes
     * one test cover both modes — the vanilla Stock Link of a domestic mounting
     * and the Transit Link of a border alike — and hands us its exclusions for
     * free: it already refuses repackagers, and already returns null for a link
     * silenced by full redstone, which is exactly when the ticker stops
     * mounting.
     *
     * <p>Every other link in the game leaves at the instanceof, so the block
     * lookup is confined to links that could plausibly sit on a ticker.
     */
    @Nullable
    private static TransitTickerBlockEntity mountedTicker(LinkWithBulbBlockEntity be) {
        if (!(be instanceof PackagerLinkBlockEntity link))
            return null;
        return link.getPackager() instanceof TransitTickerBlockEntity ticker ? ticker : null;
    }

    @Redirect(method = RENDER_SAFE,
        at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/render/SuperByteBuffer;color(IIII)Lnet/createmod/catnip/render/SuperByteBuffer;"))
    private SuperByteBuffer createNestNetwork$redOnCycle(SuperByteBuffer glow, int red, int green, int blue,
        int alpha, LinkWithBulbBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
        int overlay) {
        if (isCycling(mountedTicker(be), be))
            return glow.color(FAULT_RED, FAULT_GREEN, FAULT_BLUE, alpha);
        return glow.color(red, green, blue, alpha);
    }

    /**
     * Whether this link can do anything at all.
     *
     * Only a transit link can fail this, and only by having no label: it stamps
     * no border, so the ticker it sits on never counts it as one, never sees a
     * request arrive through it, and drops what does. There is no mounting here
     * for a heartbeat to be about.
     */
    private static boolean isInert(LinkWithBulbBlockEntity be) {
        return be instanceof TransitLinkBlockEntity link && link.getLabel()
            .isBlank();
    }

    /**
     * Whether the loop this ticker reports closes through <em>this</em> link.
     *
     * A ticker can wear several links at once, on networks that have nothing to
     * do with each other, and a loop closing through one of them says nothing
     * about the rest. Asking by frequency is what keeps an innocent mounting
     * dark: the ticker names the networks the child can reach back around to,
     * and a link whose own network is not among them is doing its job.
     *
     * <p>The frequency comes off Create's own behaviour, which writes it into
     * both save and update tags unconditionally — so the answer is available on
     * the client for a vanilla Stock Link no less than for ours, which is the
     * point, since under a domestic mounting the vanilla link is the block a
     * player is looking at.
     */
    private static boolean isCycling(@Nullable TransitTickerBlockEntity ticker, LinkWithBulbBlockEntity be) {
        if (ticker == null || !(be instanceof PackagerLinkBlockEntity link) || link.behaviour == null)
            return false;
        return ticker.isCycling(link.behaviour.freqId);
    }

}
