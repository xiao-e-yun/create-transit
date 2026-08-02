package me.xiaoeyun.createtransit.client;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;

import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * Two baked models behind one, picked per block from its address.
 *
 * Standing in front of the vanilla model rather than replacing it is what keeps
 * every postbox its dyed colour and every ordinary port untouched: a port that
 * is not an endpoint never reaches the second model at all, and the block it
 * draws is byte for byte the one Create baked.
 */
@OnlyIn(Dist.CLIENT)
public class TransitLiveryModel extends BakedModelWrapper<BakedModel> {

    private static final ModelProperty<Boolean> ENDPOINT = new ModelProperty<>();

    /** Immutable and shared: every endpoint's data is the same one bit. */
    private static final ModelData ENDPOINT_DATA = ModelData.builder()
        .with(ENDPOINT, Boolean.TRUE)
        .build();

    private final BakedModel livery;

    public TransitLiveryModel(BakedModel plain, BakedModel livery) {
        super(plain);
        this.livery = livery;
    }

    private BakedModel pick(ModelData data) {
        return Boolean.TRUE.equals(data.get(ENDPOINT)) ? livery : originalModel;
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(BlockState state, Direction side, @NotNull RandomSource rand,
        @NotNull ModelData data, RenderType renderType) {
        return pick(data).getQuads(state, side, rand, data, renderType);
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        return pick(data).getParticleIcon(data);
    }

    /**
     * Read off the address, which is already on the client to be drawn as a
     * nameplate, so nothing is stored, synchronised or migrated for the livery.
     */
    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos,
        @NotNull BlockState state, @NotNull ModelData data) {
        return level.getBlockEntity(pos) instanceof PackagePortBlockEntity port
            && AddressLabels.startsWithLabel(port.addressFilter) ? ENDPOINT_DATA : data;
    }

}
