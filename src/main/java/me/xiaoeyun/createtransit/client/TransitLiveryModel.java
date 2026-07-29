package me.xiaoeyun.createtransit.client;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Two baked models behind one, picked per block from its model data.
 *
 * Standing in front of the vanilla model rather than replacing it is what keeps
 * every postbox its dyed colour and every ordinary port untouched: a port that
 * is not an endpoint never reaches the second model at all, and the block it
 * draws is byte for byte the one Create baked.
 */
@OnlyIn(Dist.CLIENT)
public class TransitLiveryModel extends BakedModelWrapper<BakedModel> {

    private final BakedModel livery;

    public TransitLiveryModel(BakedModel plain, BakedModel livery) {
        super(plain);
        this.livery = livery;
    }

    private BakedModel pick(ModelData data) {
        return TransitLivery.isEndpoint(data) ? livery : originalModel;
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

    @Override
    public @NotNull ModelData getModelData(@NotNull net.minecraft.world.level.BlockAndTintGetter level,
        @NotNull net.minecraft.core.BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        return pick(data).getModelData(level, pos, state, data);
    }

}
