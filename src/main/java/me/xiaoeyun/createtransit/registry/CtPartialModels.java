package me.xiaoeyun.createtransit.registry;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;

import me.xiaoeyun.createtransit.CreateTransit;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Extra models baked alongside the block models, for renderers to swap in.
 *
 * Flywheel keeps these in a map with weak values and collects the locations to
 * bake when models are registered, so a partial model has to be held by a field
 * like these and has to exist before that registration happens — hence
 * {@link #init()} being called from the mod's constructor rather than from a
 * setup event.
 */
@OnlyIn(Dist.CLIENT)
public class CtPartialModels {

    /** A postbox's flag in the transit livery, for ports that are endpoints. */
    public static final PartialModel TRANSIT_POSTBOX_FLAG =
        PartialModel.of(CreateTransit.asResource("block/transit_postbox_flag"));

    /**
     * A gate's mouth, in place of the packager's iris.
     *
     * A border post is a doorway rather than a shutter, so the gate hangs strip
     * curtain over its opening — the same hinged-slat vocabulary Create's own
     * funnels use, which reads as "traffic passes through here" without any
     * explanation. Being the hatch rather than block geometry is what makes it
     * work: the renderer swaps the two states, so the slats actually part for a
     * package instead of being clipped through.
     */
    public static final PartialModel TRANSIT_GATE_HATCH_CLOSED =
        PartialModel.of(CreateTransit.asResource("block/transit_gate/hatch_closed"));

    public static final PartialModel TRANSIT_GATE_HATCH_OPEN =
        PartialModel.of(CreateTransit.asResource("block/transit_gate/hatch_open"));

    /** Loading the class is the registration; this only makes that deliberate. */
    public static void init() {}

}
