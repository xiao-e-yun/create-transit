package me.xiaoeyun.createtransit.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import me.xiaoeyun.createtransit.content.transit.TransitCustoms;
import net.minecraft.world.item.ItemStack;

/**
 * All of Create's address matching converges on {@code matchAddress} — frogports, chain conveyor
 * routing tables, train cargo, stations and filters all reach it — and {@code setOrder} is the
 * one place a package is handed an order identity. Both injections defer to vanilla whenever
 * nothing transit-related is in play.
 */
// remap = false: the target is a Create class, so its names are never obfuscated
// and there is no SRG mapping for the annotation processor to resolve.
@Mixin(value = PackageItem.class, remap = false)
public class PackageItemMixin {

    @Inject(method = "matchAddress(Ljava/lang/String;Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private static void createTransit$matchTransitLabels(String boxAddress, String address,
        CallbackInfoReturnable<Boolean> cir) {
        Boolean labelled = AddressLabels.match(boxAddress, address);
        if (labelled != null)
            cir.setReturnValue(labelled);
    }

    /**
     * A ticker cannot write on the boxes a child network packs for it, so it files its declaration
     * against the child order id; this is where a box is handed that id.
     */
    @Inject(method = "setOrder(Lnet/minecraft/world/item/ItemStack;IIZIZL"
        + "com/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;)V", at = @At("TAIL"))
    private static void createTransit$landCustomsDeclaration(ItemStack box, int orderId, int linkIndex,
        boolean isFinalLink, int fragmentIndex, boolean isFinal, @Nullable PackageOrderWithCrafts orderContext,
        CallbackInfo ci) {
        TransitCustoms.stampOnto(box, orderId);
    }

}
