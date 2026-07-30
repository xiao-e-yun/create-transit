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
     * work: the strips actually part for a package instead of being clipped
     * through.
     *
     * The rail is what the gate hands back where a packager returns its iris,
     * because the rail is the part that does not move. The strips are one model
     * each and placed by {@link me.xiaoeyun.createtransit.client.TransitGateCurtain}
     * — a matrix applies to a whole model, so a curtain in one model could only
     * hold one pose.
     */
    public static final PartialModel TRANSIT_GATE_RAIL =
        PartialModel.of(CreateTransit.asResource("block/transit_gate/rail"));

    /**
     * Two strips, differing only in where the scuff sits, alternated across the
     * curtain so four identical strips do not read as a printed pattern. Create
     * breaks up its own repeated slats the same way.
     */
    public static final PartialModel TRANSIT_GATE_FLAP =
        PartialModel.of(CreateTransit.asResource("block/transit_gate/flap"));

    public static final PartialModel TRANSIT_GATE_FLAP_ALT =
        PartialModel.of(CreateTransit.asResource("block/transit_gate/flap_alt"));

    /** Which of the two strip variants column {@code strip} carries. */
    public static PartialModel flap(int strip) {
        return strip % 2 == 0 ? TRANSIT_GATE_FLAP : TRANSIT_GATE_FLAP_ALT;
    }

    /** Loading the class is the registration; this only makes that deliberate. */
    public static void init() {}

}
