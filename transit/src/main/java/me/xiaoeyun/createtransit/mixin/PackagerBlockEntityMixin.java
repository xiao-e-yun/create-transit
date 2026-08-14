package me.xiaoeyun.createtransit.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.simibubi.create.AllBlocks;
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
 * <p>Three methods compare against {@code AllBlocks.STOCK_LINK} by block
 * identity, and missing any of them is one bug rather than three: {@code
 * flashLink} stops blinking the bulb, {@code getLinkPos} leaves {@code
 * PackagerBlock.LINKED} unset so the packager serves the network while still in
 * redstone mode, and {@code submitNewArrivals} never retires a promise, which
 * stalls a factory panel permanently.
 *
 * <p>Repackagers, and the gate that extends one, are untouched: their {@code
 * recheckIfLinksPresent} is empty and their {@code redstoneModeActive} is always
 * true, and a link refuses to take one as its packager in the first place.
 */
// remap = false: the target is a Create class, so its names are never obfuscated
// and there is no SRG mapping for the annotation processor to resolve.
@Mixin(value = PackagerBlockEntity.class, remap = false)
public class PackagerBlockEntityMixin {

    @Redirect(
        method = { "flashLink()V", "getLinkPos()Lnet/minecraft/core/BlockPos;",
            "submitNewArrivals(Lcom/simibubi/create/content/logistics/packager/InventorySummary;"
                + "Lcom/simibubi/create/content/logistics/packager/InventorySummary;)V" },
        at = @At(value = "INVOKE",
            target = "Lcom/tterrag/registrate/util/entry/BlockEntry;"
                + "has(Lnet/minecraft/world/level/block/state/BlockState;)Z"),
        require = 4)
    private boolean createTransit$transitLinksCountToo(BlockEntry<?> queried, BlockState adjacent) {
        // submitNewArrivals asks the same question of FACTORY_GAUGE first, and
        // answering yes there would make it consume the direction and skip the
        // link check entirely.
        return queried.has(adjacent) || queried == AllBlocks.STOCK_LINK && CtBlocks.TRANSIT_LINK.has(adjacent);
    }

    /**
     * Puts a freshly packed box into the one its address calls for, at the sole
     * instruction between the last {@code addAddress} the choice depends on and
     * the two places {@code createdBox} is handed off.
     */
    @Inject(method = "attemptToSend(Ljava/util/List;)V",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;"
                + "getLinkPos()Lnet/minecraft/core/BlockPos;"))
    private void createTransit$boxForeignPackages(List<PackagingRequest> queuedRequests, CallbackInfo ci,
        @Local(name = "createdBox") LocalRef<ItemStack> createdBox) {
        createdBox.set(TransitPackaging.restyle(createdBox.get()));
    }

}
