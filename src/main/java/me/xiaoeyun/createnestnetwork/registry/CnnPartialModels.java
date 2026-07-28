package me.xiaoeyun.createnestnetwork.registry;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;

import me.xiaoeyun.createnestnetwork.CreateNestNetwork;
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
public class CnnPartialModels {

    /** A postbox's flag in the transit livery, for ports that are endpoints. */
    public static final PartialModel TRANSIT_POSTBOX_FLAG =
        PartialModel.of(CreateNestNetwork.asResource("block/transit_postbox_flag"));

    /** Loading the class is the registration; this only makes that deliberate. */
    public static void init() {}

}
