package me.xiaoeyun.createnestnetwork.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.box.PackageItem;

import me.xiaoeyun.createnestnetwork.content.transit.AddressLabels;

/**
 * All of Create's address matching converges on this one method — frogports,
 * chain conveyor routing tables, train cargo, stations and filters all reach it
 * — so a single rule here gives labels consistent semantics everywhere, and
 * keeps the routing hardware itself 100% vanilla.
 *
 * The injection defers to vanilla whenever neither side carries a label, so
 * existing saves and other addons see no behavioural change. Overwriting the
 * method outright would break that guarantee.
 */
// remap = false: the target is a Create class, so its names are never obfuscated
// and there is no SRG mapping for the annotation processor to resolve.
@Mixin(value = PackageItem.class, remap = false)
public class PackageItemMixin {

    @Inject(method = "matchAddress(Ljava/lang/String;Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
    private static void createNestNetwork$matchTransitLabels(String boxAddress, String address,
        CallbackInfoReturnable<Boolean> cir) {
        Boolean labelled = AddressLabels.match(boxAddress, address);
        if (labelled != null)
            cir.setReturnValue(labelled);
    }

}
