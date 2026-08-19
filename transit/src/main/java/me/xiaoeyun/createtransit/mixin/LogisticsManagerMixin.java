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
 * A Transit Ticker is a packager that holds nothing itself, which two of Create's assumptions
 * trip on.
 *
 * <p>{@code getInventoryIdentifierFromLink} answers null unless
 * {@code targetInventory.hasInventory()}, and the two dedupe paths that ask it —
 * {@code createSummaryOfNetwork} and {@code findPackagersForRequest} — then count every link
 * separately, so two links on one ticker each contributed the child's whole stock. Answered here
 * rather than in {@code hasInventory}, whose other caller {@code FactoryPanelBehaviour} reads it
 * as "has somewhere to restock into". The ticker's own position is safe as an identity: an
 * identifier names a container, never the packager, and identifiers compare by record equality.
 *
 * <p>{@code findPackagersForRequest} spends the order's crafts on the first link to take any of
 * the order and nulls them for the rest. A ticker re-orders across the border, and the crafts are
 * the only thing telling the far-side gate to repack by recipe.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = LogisticsManager.class, remap = false)
public class LogisticsManagerMixin {

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

    /** At the call, not the {@code context = null} after it, so a ticker still counts as a link drawn. */
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
