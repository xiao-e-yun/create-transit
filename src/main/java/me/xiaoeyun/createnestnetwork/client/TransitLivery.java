package me.xiaoeyun.createnestnetwork.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

/**
 * The flag a package port hands to its baked model when it is a transit
 * endpoint.
 *
 * The livery rides on model data rather than on a block state, because a port's
 * address lives in its block entity and Create's block states have no room for
 * it — nor should they, since a property would have to be written to disc and
 * kept in step with a string that already says the same thing.
 */
@OnlyIn(Dist.CLIENT)
public final class TransitLivery {

    public static final ModelProperty<Boolean> ENDPOINT = new ModelProperty<>();

    /** Immutable and shared: every endpoint's data is the same one bit. */
    public static final ModelData ENDPOINT_DATA = ModelData.builder()
        .with(ENDPOINT, Boolean.TRUE)
        .build();

    private TransitLivery() {}

    public static boolean isEndpoint(ModelData data) {
        return Boolean.TRUE.equals(data.get(ENDPOINT));
    }

}
