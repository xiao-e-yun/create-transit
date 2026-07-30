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
 * Gives a transit gate strip curtain where a packager has its iris.
 *
 * The hatch is not part of the block model — it is drawn from a partial model
 * this static method picks, so borrowing the packager's geometry means
 * borrowing its iris with it, and no amount of model or texture overriding
 * reaches a hardcoded {@code create:} location. The method is public and static
 * rather than virtual, so a renderer subclass has nothing to override either.
 *
 * Intercepting it is the narrow fix: gates answer with their own models and
 * every other packager in the world is untouched. {@code PackagerVisual} routes
 * through here as well, so this covers the flywheel path too.
 */
@Mixin(value = PackagerRenderer.class, remap = false)
public class PackagerRendererMixin {

    @Inject(
        method = "getHatchModel(Lcom/simibubi/create/content/logistics/packager/PackagerBlockEntity;)"
            + "Ldev/engine_room/flywheel/lib/model/baked/PartialModel;",
        at = @At("HEAD"), cancellable = true)
    private static void createNestNetwork$curtainInsteadOfIris(PackagerBlockEntity packager,
        CallbackInfoReturnable<PartialModel> cir) {
        if (!(packager instanceof TransitGateBlockEntity))
            return;
        cir.setReturnValue(PackagerRenderer.isHatchOpen(packager)
            ? CtPartialModels.TRANSIT_GATE_HATCH_OPEN
            : CtPartialModels.TRANSIT_GATE_HATCH_CLOSED);
    }

}
