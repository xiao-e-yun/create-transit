package me.xiaoeyun.createtransit.client;

import dev.engine_room.flywheel.lib.transform.Affine;

import me.xiaoeyun.createtransit.content.transit.TransitGateBlockEntity;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Where each strip of a gate's curtain goes, and how far it has swung.
 *
 * <p>Written once against {@link Affine} so that all three consumers — Flywheel's
 * {@code TransformedInstance}, catnip's {@code SuperByteBuffer} on the
 * no-visualization fallback, and {@code DumpTransforms} feeding the offline
 * renderer — execute the same arithmetic rather than three copies of it.
 *
 * <p>The chain opens with Create's own hatch placement, so the curtain keeps
 * tracking the iris if Create ever moves it, then (post-multiplying, so the last
 * call is innermost) steps sideways to this strip's column and swings about the
 * pivot the strips hang from.
 */
public final class TransitGateCurtain {

    /** Strips across the aperture. draw_curtain.py's STRIPS has to match. */
    public static final int STRIPS = 4;

    /**
     * Distance between strips, in blocks: 2.925 of strip and 0.1 of seam. The
     * seam keeps neighbouring strips off coplanar, where the depth buffer cannot
     * order them.
     */
    public static final float PITCH = 3.025f / 16f;

    /**
     * The axis the strips hang from: the top edge of an authored strip, at the
     * middle of its depth. X is irrelevant — the swing is about the X axis — so
     * one pivot serves every strip however far along it has been stepped.
     */
    public static final Vec3 PIVOT = VecHelper.voxelSpace(0, 15, 7.1);

    /** How far each strip swings when fully pushed aside; the outer two move less, so it hangs rather than hinges. */
    private static final float[] SWING = { 22.5f, 45f, 45f, 22.5f };

    private TransitGateCurtain() {
    }

    /**
     * The swing, from the decaying push {@link TransitGateBlockEntity#tick()}
     * kicks when a package crosses the strips: negative pushes them out through
     * the mouth, positive back into the block.
     */
    public static float angle(int strip, float flapness) {
        return SWING[Math.floorMod(strip, SWING.length)] * flapness;
    }

    /**
     * Place one strip. {@code blockstateFacing} is the value of
     * {@code PackagerBlock.FACING}, not a renderer's derived variable, so callers
     * need no semantics of their own.
     */
    public static <T extends Affine<T>> T place(T stack, Direction blockstateFacing, int strip,
        float flapness) {
        Direction facing = blockstateFacing.getOpposite();
        return stack.translate(Vec3.atLowerCornerOf(facing.getNormal())
                .scale(.49999f))
            .rotateYCenteredDegrees(AngleHelper.horizontalAngle(facing))
            .rotateXCenteredDegrees(AngleHelper.verticalAngle(facing))
            .translate(PIVOT)
            .rotateXDegrees(angle(strip, flapness))
            .translateBack(PIVOT)
            .translate(strip * PITCH, 0, 0);
    }

}
