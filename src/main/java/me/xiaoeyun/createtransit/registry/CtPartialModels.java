package me.xiaoeyun.createtransit.registry;

import com.simibubi.create.AllPartialModels;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;

import me.xiaoeyun.createtransit.CreateTransit;
import net.minecraft.resources.ResourceLocation;
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
     * What the gate hands back where a packager returns its iris: the rail is
     * the part of the curtain that does not move, and each strip is a model of
     * its own because a matrix applies to a whole one.
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

    /**
     * Enrols the transit package in the plain {@code HashMap} that dropped
     * packages, frogports and chain conveyors look a box's model up in, and
     * which Create fills from its own styles alone.
     */
    private static void registerPackage() {
        ResourceLocation id = CreateTransit.asResource("transit_package");
        AllPartialModels.PACKAGES.put(id, PartialModel.of(CreateTransit.asResource("item/transit_package")));
        AllPartialModels.PACKAGE_RIGGING.put(id,
            PartialModel.of(new ResourceLocation("create", "item/package/rigging_12x10")));
    }

    /** Loading the class is the registration; this only makes that deliberate. */
    public static void init() {
        registerPackage();
    }

}
