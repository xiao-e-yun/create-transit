package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRoutingTable;

import me.xiaoeyun.createtransit.content.transit.AddressLabels;

/**
 * Makes a wildcard transit endpoint the last resort on a chain. Vanilla files a port advertising
 * {@code *} at distance 1000 and everything else at 0; a port filtering {@code <[*]>} means the
 * same thing one layer up but is not that string, so it would be filed at 0 and race named border
 * posts on physical distance. Redirecting the comparison rather than the distance leaves the
 * branch that produces 1000 untouched, so no literal here can drift out of step with it.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = ChainConveyorRoutingTable.class, remap = false)
public class ChainConveyorRoutingTableMixin {

    @Redirect(method = "receivePortInfo(Ljava/lang/String;Lnet/minecraft/core/BlockPos;)V",
        at = @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"))
    private boolean createTransit$deprioritiseWildcardEndpoints(String wildcard, Object filter) {
        if (wildcard.equals(filter))
            return true;
        return filter instanceof String port
            && AddressLabels.WILDCARD.equals(AddressLabels.headLabelName(port));
    }

}
