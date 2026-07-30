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
 * Swings a gate's curtain when Flywheel's backend is off.
 *
 * The block entity renderer is the fallback path, and it only has to add the
 * strips: everything else a gate draws — the rail in place of the iris, the tray,
 * the package — is drawn by the packager's own {@code renderSafe}, which this
 * calls first. Adding rather than replacing is what keeps Create's twenty-odd
 * lines out of this file, and it works because the part of a gate that moves
 * differently is exactly the part that is not Create's.
 *
 * The visibility check is the same one Create makes: with a Flywheel backend
 * running, {@link TransitGateVisual} owns the strips and drawing them here as
 * well would double them.
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
