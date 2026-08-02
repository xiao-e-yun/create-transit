package me.xiaoeyun.createtransit.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import me.xiaoeyun.createtransit.CreateTransit;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * Puts the transit livery in front of the two ports' baked models.
 *
 * Both blocks are Create's, and both pick their model from a block state that
 * knows nothing about addresses, so the variant cannot be selected the ordinary
 * way. Wrapping the baked result instead lets the choice happen per block, at
 * draw time, from the model data the block entity supplies.
 *
 * Doing it here rather than in a renderer is also what spares the frogport a
 * second implementation: a base plate is part of the chunk mesh, which Flywheel
 * does not replace, so this one path covers both settings.
 */
// The bus is inferred per listener from the event type on NeoForge; both
// events here are mod-bus events.
@EventBusSubscriber(modid = CreateTransit.MOD_ID, value = Dist.CLIENT)
public final class CtModelEvents {

    private static final String CREATE = "create";
    private static final String POSTBOX_SUFFIX = "_postbox";

    private static final List<String> DYES = Arrays.stream(DyeColor.values())
        .map(DyeColor::getSerializedName)
        .toList();

    /** The turn Create's own block state applies for each facing; north is the authored front, so it is the zero. */
    private static final Map<String, Integer> FACINGS = Direction.Plane.HORIZONTAL.stream()
        .collect(Collectors.toMap(Direction::getSerializedName, d -> ((int) d.toYRot() + 180) % 360));

    private CtModelEvents() {}

    /** Nothing references these from a block state, so they need asking for. 1.21 files such models under a standalone variant. */
    @SubscribeEvent
    public static void registerLiveryModels(ModelEvent.RegisterAdditional event) {
        event.register(standalone("block/transit_frogport_base"));
        for (String dye : DYES)
            for (String state : new String[] { "closed", "open" })
                for (int facing : FACINGS.values())
                    event.register(standalone("block/transit_" + dye + "_postbox_" + state + "_y" + facing));
    }

    private static ModelResourceLocation standalone(String path) {
        return new ModelResourceLocation(CreateTransit.asResource(path),
            ModelResourceLocation.STANDALONE_VARIANT);
    }

    @SubscribeEvent
    public static void wrapPortModels(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (ModelResourceLocation id : new ArrayList<>(models.keySet())) {
            ModelResourceLocation liveryId = liveryFor(id);
            if (liveryId == null)
                continue;
            BakedModel plain = models.get(id);
            BakedModel livery = models.get(liveryId);
            // A missing livery is a broken build, not something to hide behind
            // a half-painted world: leave Create's model exactly as it was.
            if (plain == null || livery == null)
                continue;
            models.put(id, new TransitLiveryModel(plain, livery));
        }
    }

    /**
     * The livery model for a block state variant, or null for anything that is
     * not a package port. Held items are skipped — a postbox in the hand has no
     * address yet, and an inventory model is never handed model data.
     */
    @Nullable
    private static ModelResourceLocation liveryFor(ModelResourceLocation id) {
        if (!CREATE.equals(id.id()
            .getNamespace()) || "inventory".equals(id.variant()))
            return null;

        String path = id.id()
            .getPath();
        if ("package_frogport".equals(path))
            return standalone("block/transit_frogport_base");

        if (!path.endsWith(POSTBOX_SUFFIX))
            return null;
        String dye = path.substring(0, path.length() - POSTBOX_SUFFIX.length());
        if (!DYES.contains(dye))
            return null;

        String variant = id.variant();
        String state = variant.contains("open=true") ? "open" : "closed";
        Integer facing = facingOf(variant);
        if (facing == null)
            return null;
        return standalone("block/transit_" + dye + "_postbox_" + state + "_y" + facing);
    }

    /**
     * A block state's own rotation is baked into its model, and a model asked
     * for by name is baked without one — so the livery is generated already
     * turned, one model per facing, and the right one is chosen here.
     */
    @Nullable
    private static Integer facingOf(String variant) {
        for (Map.Entry<String, Integer> facing : FACINGS.entrySet())
            if (variant.contains("facing=" + facing.getKey()))
                return facing.getValue();
        return null;
    }

}
