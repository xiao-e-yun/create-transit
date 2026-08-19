package me.xiaoeyun.createtransit.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import me.xiaoeyun.createtransit.content.transit.TransitGateBlockEntity;
import me.xiaoeyun.createtransit.registry.CtPartialModels;

/**
 * Gives a transit gate its curtain rail where a packager has its iris.
 *
 * The hatch is not part of the block model — it is drawn from a partial model this static method
 * picks at a hardcoded {@code create:} location, which no model or texture override reaches, and
 * being static rather than virtual leaves a renderer subclass nothing to override either.
 * {@code PackagerVisual} routes through here as well, so this covers the Flywheel path too.
 *
 * The answer does not depend on the animation: the hatch slot now carries only the rail, and the
 * strips that move are one model each, swung by
 * {@link me.xiaoeyun.createtransit.client.TransitGateCurtain}.
 */
@Mixin(value = PackagerRenderer.class, remap = false)
public class PackagerRendererMixin {

    @Inject(
        method = "getHatchModel(Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;)"
            + "Ldev/engine_room/flywheel/lib/model/baked/PartialModel;",
        at = @At("HEAD"), cancellable = true)
    private static void createTransit$curtainInsteadOfIris(PackagerBlockEntity packager,
        CallbackInfoReturnable<PartialModel> cir) {
        if (!(packager instanceof TransitGateBlockEntity))
            return;
        cir.setReturnValue(CtPartialModels.TRANSIT_GATE_RAIL);
    }

}
