package me.xiaoeyun.createtransit.mixin;

import javax.annotation.Nullable;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import me.xiaoeyun.createtransit.content.ticker.TransitTickerBlockEntity;
import net.createmod.catnip.data.Pair;
import net.minecraft.world.item.ItemStack;

/**
 * Two things a Transit Ticker needs from the machinery that hands out a request,
 * both of them consequences of its being a packager that holds nothing itself.
 *
 * <p>Gives a Transit Ticker an identity in the one place a network dedupes by one.
 *
 * <p>Vanilla already refuses to count a warehouse twice when two links present
 * it: {@code createSummaryOfNetwork} skips a link whose inventory another link
 * has shown already, and {@code findPackagersForRequest} groups links by
 * inventory and assigns through only one of each group. Both ask
 * {@code getInventoryIdentifierFromLink}, and both fall back to counting every
 * link separately when it answers null.
 *
 * <p>It answers null for a ticker, because the guard is
 * {@code targetInventory.hasInventory()} and a ticker deliberately has no
 * inventory — it holds nothing itself, and the warehouse it speaks for is a
 * whole network away. So two Transit Links on one ticker in one parent network
 * each contributed the child's entire stock, and the parent browsed a warehouse
 * twice the size of the real one. Nothing duplicated: the second child order
 * clamped against stock the first had already consumed, so the order simply
 * under-delivered against a number that was never true.
 *
 * <p>Answering here rather than making {@code hasInventory} say yes is what
 * keeps the fix to the question actually being asked. The other caller of that
 * guard is {@code FactoryPanelBehaviour}, where it means "does this packager
 * have somewhere to restock into" — a ticker does not, and a panel told
 * otherwise would try to fill a block that holds nothing.
 *
 * <p>The ticker's own position is the identity. A packager's identifier names
 * the container it reaches into, never the packager, so no real inventory can
 * ever claim this position — the ticker is standing in it. And identifiers are
 * compared here as map keys, by record equality rather than by
 * {@code contains}, so a {@code Single} cannot collide with the {@code Bounds}
 * of some multiblock that happens to enclose the ticker.
 *
 * <p>And gives it the order's crafts, which vanilla spends on the first link
 * that takes any of the order and nulls for the rest, so that exactly one
 * shipment box carries them. A ticker is not a shipment: it re-orders across the
 * border, and the crafts are the only thing telling the far-side gate to repack
 * by recipe, so a ticker drawn anywhere but first forwarded a craftless order
 * and the mechanical crafters at the far end got boxes they could not read.
 * Handing it the order is what vanilla would have done had it been drawn first.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = LogisticsManager.class, remap = false)
public class LogisticsManagerMixin {

    /**
     * On the way out rather than the way in, so only the links vanilla gave up
     * on pay for the second {@code getBlockEntity} this costs.
     */
    @ModifyReturnValue(method = "getInventoryIdentifierFromLink", at = @At("RETURN"))
    private static InventoryIdentifier createTransit$identifyMountingPoints(
        @Nullable InventoryIdentifier identifier, LogisticallyLinkedBehaviour link) {
        if (identifier != null)
            return identifier;
        if (!(link.blockEntity instanceof PackagerLinkBlockEntity plbe))
            return null;
        if (!(plbe.getPackager() instanceof TransitTickerBlockEntity ticker))
            return null;
        return new InventoryIdentifier.Single(ticker.getBlockPos());
    }

    /**
     * At the call rather than at the {@code context = null} after it, so a ticker
     * still counts as a link that has been drawn — and because every hop of a
     * chain of borders reaches this same call, one per forwarded order, nothing
     * has to be carried across the hops.
     */
    @WrapOperation(method = "findPackagersForRequest",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagerLink/LogisticallyLinkedBehaviour;"
                + "processRequest(Lnet/minecraft/world/item/ItemStack;ILjava/lang/String;I"
                + "Lorg/apache/commons/lang3/mutable/MutableBoolean;I"
                + "Lcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;"
                + "Lcom/simibubi/create/content/logistics/packager/IdentifiedInventory;)"
                + "Lnet/createmod/catnip/data/Pair;"))
    private static Pair<PackagerBlockEntity, PackagingRequest> createTransit$tickersAlwaysGetTheOrder(
        LogisticallyLinkedBehaviour link, ItemStack stack, int amount, String address, int linkIndex,
        MutableBoolean finalLink, int orderId, @Nullable PackageOrderWithCrafts context,
        @Nullable IdentifiedInventory ignoredHandler,
        Operation<Pair<PackagerBlockEntity, PackagingRequest>> original,
        @Local(argsOnly = true) PackageOrderWithCrafts order) {
        if (link.blockEntity instanceof PackagerLinkBlockEntity plbe
            && plbe.getPackager() instanceof TransitTickerBlockEntity)
            context = order;
        return original.call(link, stack, amount, address, linkIndex, finalLink, orderId, context, ignoredHandler);
    }

}
