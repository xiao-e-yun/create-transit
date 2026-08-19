package me.xiaoeyun.createtransit.mixin.client;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.redstone.displayLink.LinkBulbRenderer;
import com.simibubi.create.content.redstone.displayLink.LinkWithBulbBlockEntity;

import me.xiaoeyun.createtransit.content.ticker.TransitTickerBlockEntity;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.Level;

/**
 * What a link's bulb says once it sits on a Transit Ticker: a white heartbeat
 * is the ticker polling, and a red flash is a fault — a proxy
 * cycle closing through this link's frequency, or a dual mount starving the
 * transit link on this ticker.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = LinkBulbRenderer.class, remap = false)
public class LinkBulbRendererMixin {

    private static final String RENDER_SAFE =
        "renderSafe(Lcom/simibubi/create/content/redstone/displayLink/LinkWithBulbBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V";

    /**
     * Full red rather than Create's brightness with the other channels dimmed:
     * the glow layer is additive, so whatever survives in green and blue drags
     * the result back towards white and reads as pink over lit brass.
     */
    private static final int FAULT_RED = 255;
    private static final int FAULT_GREEN = 0;
    private static final int FAULT_BLUE = 0;

    /** Ticks in one flash, half of them lit: too fast to read as the heartbeat, too slow to strobe. */
    private static final int FLASH = 8;

    /** Ticks between heartbeats. */
    private static final int PERIOD = 60;

    /** Create's own pulse decays on an EXP chaser at this rate, so a heartbeat reads as the blink players know. */
    private static final double DECAY = 0.5;

    /** The last tick of a blink that still draws: {@code DECAY^3} is Create's own {@code 0.125f} cutoff restated. */
    private static final int BLINK = 3;

    /** Decided once per bulb: {@code renderSafe} calls {@code getGlow} before it ever reaches {@code color}. */
    @Unique
    @Nullable
    private TransitTickerBlockEntity createTransit$ticker;

    /**
     * Both readings are functions of the tick and of state the ticker already
     * syncs for its goggle tooltip, so neither costs a packet.
     */
    @Redirect(method = RENDER_SAFE,
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/redstone/displayLink/LinkWithBulbBlockEntity;getGlow(F)F"))
    private float createTransit$faultOrBeatOnMountingPoints(LinkWithBulbBlockEntity be, float partialTicks) {
        float glow = be.getGlow(partialTicks);
        TransitTickerBlockEntity ticker = mountedTicker(be);
        createTransit$ticker = ticker;
        if (ticker == null)
            return glow;
        if (isFaulted(ticker, be))
            return flash(ticker, partialTicks);
        // A binding that reaches nothing has no poll to announce.
        if (!ticker.isChildConnected())
            return glow;
        return Math.max(glow, heartbeat(ticker, partialTicks));
    }

    @Redirect(method = RENDER_SAFE,
        at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/render/SuperByteBuffer;color(IIII)Lnet/createmod/catnip/render/SuperByteBuffer;"))
    private SuperByteBuffer createTransit$redOnFault(SuperByteBuffer glow, int red, int green, int blue,
        int alpha, LinkWithBulbBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
        int overlay) {
        if (isFaulted(createTransit$ticker, be))
            return glow.color(FAULT_RED, FAULT_GREEN, FAULT_BLUE, alpha);
        return glow.color(red, green, blue, alpha);
    }

    /**
     * The fault flash, deliberately <em>not</em> offset by position the way the
     * heartbeat is: one fault is one fault however many tickers and chunks it
     * spans, so the bulbs reporting it in unison is the true reading.
     */
    private static float flash(TransitTickerBlockEntity ticker, float partialTicks) {
        Level level = ticker.getLevel();
        if (level == null)
            return 1;
        float phase = Math.floorMod(level.getGameTime(), FLASH) + partialTicks;
        return phase < FLASH / 2f ? 1 : 0;
    }

    /**
     * The blink, offset by the <em>ticker's</em> position rather than the
     * link's: several links on one ticker report one poll and beat together,
     * while separate tickers drift apart instead of claiming a chorus.
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
     * Create's own {@code getPackager} decides it, so one test covers a border's
     * Transit Link and a domestic mounting's vanilla Stock Link alike, and its
     * exclusions — repackagers, links silenced by full redstone — come free.
     */
    @Nullable
    private static TransitTickerBlockEntity mountedTicker(LinkWithBulbBlockEntity be) {
        if (!(be instanceof PackagerLinkBlockEntity link))
            return null;
        return link.getPackager() instanceof TransitTickerBlockEntity ticker ? ticker : null;
    }

    /**
     * A cycle is asked per frequency because a ticker can wear several links on
     * unrelated networks; a dual mount is not, because it is a fault of the
     * ticker itself and starves every border on it.
     */
    private static boolean isFaulted(@Nullable TransitTickerBlockEntity ticker, LinkWithBulbBlockEntity be) {
        if (ticker == null || !(be instanceof PackagerLinkBlockEntity link) || link.behaviour == null)
            return false;
        return ticker.isMountConflicted() || ticker.isCycling(link.behaviour.freqId);
    }

}
