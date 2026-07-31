package me.xiaoeyun.createtransit.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.tterrag.registrate.util.entry.BlockEntry;

import me.xiaoeyun.createtransit.content.transit.TransitPackaging;
import me.xiaoeyun.createtransit.registry.CtBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lets a packager see a transit link where it looks for a stock link.
 *
 * Both places it looks compare against {@code AllBlocks.STOCK_LINK} by block
 * identity, and a transit link is a different block, so a packager wearing one
 * behaved as though it had no link at all. Three things followed from that, and
 * they are one bug rather than three:
 *
 * <ul>
 * <li>{@code flashLink} never sent the WiFi packet, so the bulb never blinked
 * and the burst never played. That blink is the only signal a link gives that it
 * is doing anything, and a machine with no feedback reads as a broken one.</li>
 * <li>{@code getLinkPos} left {@code PackagerBlock.LINKED} unset, so the packager
 * kept its unlinked model and stayed in redstone mode while also serving the
 * network — a packager that is both at once, which Create never produces.</li>
 * <li>The same lookup skipped {@code deductFromAccurateSummary}, so packing loose
 * items for a request left the network's cached stock too high until something
 * else recomputed it.</li>
 * </ul>
 *
 * The mode switch is Create's design rather than a side effect worth avoiding.
 * A packager alone is a redstone device: a pulse packs its container and sends it
 * to the sign's address. Attaching a stock link makes it a warehouse on the
 * network, and Create makes the two exclusive — {@code redstoneModeActive} is
 * literally {@code !LINKED}, both entry points check it, and the state is visible
 * in the model. A transit link is a stock link that stamps a label on the way
 * out, so it belongs on the same side of that switch.
 *
 * Repackagers, and the gate that extends one, are untouched: their
 * {@code recheckIfLinksPresent} is empty and their {@code redstoneModeActive} is
 * always true, and a link refuses to take one as its packager in the first place.
 *
 * Redirecting the comparison rather than either method keeps this to the one
 * question being asked, and {@code require = 2} means a Create version that stops
 * asking it in either place fails loudly instead of quietly reverting all three.
 */
// remap = false: the target is a Create class, so its names are never obfuscated
// and there is no SRG mapping for the annotation processor to resolve.
@Mixin(value = PackagerBlockEntity.class, remap = false)
public class PackagerBlockEntityMixin {

    @Redirect(
        method = { "flashLink()V", "getLinkPos()Lnet/minecraft/core/BlockPos;" },
        at = @At(value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/BlockEntry;"
                + "has(Lnet/minecraft/world/level/block/state/BlockState;)Z"),
        require = 2)
    private boolean createNestNetwork$transitLinksCountToo(BlockEntry<?> stockLink, BlockState adjacent) {
        return stockLink.has(adjacent) || CtBlocks.TRANSIT_LINK.has(adjacent);
    }

    /**
     * Puts a freshly packed box into the right kind of box for where it is
     * going: still-labelled addresses leave in a transit package, everything
     * else in one of Create's.
     *
     * The moment matters twice over. It has to be after the address is on —
     * the address is the only thing the choice depends on — and before the box
     * goes anywhere, and there is exactly one instruction between the last
     * {@code addAddress} and the two places {@code createdBox} is handed off.
     * Injecting at the {@code getLinkPos} call that sits in that gap catches
     * both of them at once, which is why this is one injection rather than one
     * per exit.
     *
     * Rewriting the local is the point: an item stack's item is final, so the
     * box cannot be converted in place, only replaced before anything holds a
     * reference to it. A gate is untouched here because it overrides
     * {@code attemptToSend} outright and does its own restyling.
     */
    @Inject(method = "attemptToSend(Ljava/util/List;)V",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;"
                + "getLinkPos()Lnet/minecraft/core/BlockPos;"))
    private void createNestNetwork$boxForeignPackages(List<PackagingRequest> queuedRequests, CallbackInfo ci,
        @Local(name = "createdBox") LocalRef<ItemStack> createdBox) {
        createdBox.set(TransitPackaging.restyle(createdBox.get()));
    }

}
