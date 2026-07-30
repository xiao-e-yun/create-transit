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
 * One strip is one model. That split is the animation's: a matrix applies to a
 * whole model, so four strips in one model can only ever hold one pose, and the
 * gate's curtain used to be exactly that — two baked models, one closed and one
 * open, swapped at a threshold. Create's tunnel flaps solved the same problem the
 * same way (one {@code BELT_TUNNEL_FLAP}, four instances, a step between them),
 * and this is that arrangement with the gate's own numbers.
 *
 * <h2>Why one method for three renderers</h2>
 *
 * The chain below is written once against {@link Affine} and executed by
 * everything that needs it — Flywheel's {@code TransformedInstance}, catnip's
 * {@code SuperByteBuffer} for the no-visualization fallback, and the matrix
 * accumulator in {@code DumpTransforms} that feeds the offline renderer. All
 * three are {@code Affine}, so none of them holds a second copy of the
 * arithmetic. A curtain that renders in one path and not another, or that the
 * fixtures agree with while the game disagrees, is the failure this rules out
 * rather than tests for.
 *
 * <h2>The chain</h2>
 *
 * It opens with Create's own hatch placement, because a gate's curtain is drawn
 * where a packager's iris is drawn, and it must keep tracking that if Create ever
 * moves it. Then, applied to the model first (a transform stack post-multiplies,
 * so the last call is the innermost): step sideways to this strip's column, then
 * swing about the pivot the strips hang from.
 *
 * The step is along the authored X axis. Create's chain contains a 180-degree Y
 * rotation, so authored X runs the opposite way in the world — which costs
 * nothing here, since the strips are laid out symmetrically and a mirrored order
 * is the same curtain.
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

    /**
     * How far each strip swings when fully pushed aside. The outer two move less
     * than the inner two, which is what makes a curtain read as hanging from a
     * rail rather than as a hinged flap. The magnitudes are the ones the two
     * baked poses carried; only the sign has changed, so the fully pushed curtain
     * is the mirror of the picture that pose was.
     */
    private static final float[] SWING = { 22.5f, 45f, 45f, 22.5f };

    private TransitGateCurtain() {
    }

    /**
     * The swing, from the push the block entity is holding.
     *
     * How far the curtain is pushed and which way is not computed here, and not
     * computed from the tray's position at all: see
     * {@link TransitGateBlockEntity#tick()}, which kicks a decaying value when a
     * package crosses the strips. This is only the shape of the result — negative
     * pushes the strips out through the mouth, positive back into the block.
     *
     * At a full outward push a strip's tip stands six and a half units clear of
     * the block face. That is a smaller excursion than Create's own tray makes:
     * {@code getTrayOffset} is in <em>blocks</em> and the tray translates by all
     * of it, so at its peak the tray is a full block outside the gate. Geometry
     * leaving this block is the family's own idiom, not a bug to design around.
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
