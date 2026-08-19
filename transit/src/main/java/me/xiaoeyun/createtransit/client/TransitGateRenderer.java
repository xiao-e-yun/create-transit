package me.xiaoeyun.createtransit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerRenderer;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;

import me.xiaoeyun.createtransit.content.transit.TransitGateBlockEntity;
import me.xiaoeyun.createtransit.registry.CtPartialModels;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Swings a gate's curtain when Flywheel's backend is off. Only the strips are added here —
 * everything else a gate draws is the packager's own {@code renderSafe}, which this calls first.
 *
 * The visibility check is the same one Create makes: with a Flywheel backend running,
 * {@link TransitGateVisual} owns the strips and drawing them here too would double them.
 */
public class TransitGateRenderer extends PackagerRenderer {

    public TransitGateRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(PackagerBlockEntity be, float partialTicks, PoseStack ms,
        MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        if (!(be instanceof TransitGateBlockEntity gate))
            return;
        if (VisualizationManager.supportsVisualization(gate.getLevel()))
            return;

        BlockState blockState = gate.getBlockState();
        Direction facing = blockState.getValue(PackagerBlock.FACING);
        float flapness = gate.curtainPush(partialTicks);

        // Solid rather than cutout: every texel of a strip is opaque, and it is
        // the layer Create draws the hatch this stands in for into.
        var vertices = buffer.getBuffer(RenderType.solid());
        for (int strip = 0; strip < TransitGateCurtain.STRIPS; strip++)
            TransitGateCurtain
                .place(CachedBuffers.partial(CtPartialModels.flap(strip), blockState), facing, strip,
                    flapness)
                .light(light)
                .renderInto(ms, vertices);
    }

}
