package me.xiaoeyun.createtransit.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Quaternionfc;

import dev.engine_room.flywheel.lib.transform.Affine;

import me.xiaoeyun.createtransit.client.TransitGateCurtain;
import me.xiaoeyun.createtransit.content.transit.TransitGateBlockEntity;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Writes out the matrices a block entity renderer applies to its partial models, by
 * <em>running</em> each renderer's own chain rather than describing it. A partial model's
 * coordinates do not mean what the model file says — a renderer transforms it every frame, and
 * the model format has no way to record that — so a tool reading the file alone shows it in the
 * wrong place while looking plausible.
 *
 * {@code TransformStack.of(new PoseStack())} cannot be used outside the game: Flywheel reaches a
 * PoseStack's matrix through an interface it <em>mixes into</em> the class at launch, and without
 * the mixin transformer that cast fails. {@link MatrixAffine} supplies {@link Affine}'s three
 * abstract methods over a JOML matrix instead, leaving every derived method as Flywheel's own
 * code. JOML's {@code translate}/{@code rotate}/{@code scale} post-multiply, which is PoseStack's
 * convention: the last call applies to the model first.
 *
 * Matrices are keyed by the <em>blockstate</em> facing rather than by a variable a renderer
 * derives from it, so every {@code getOpposite()} and angle helper stays on this side of the file
 * and a consumer does a pure lookup.
 *
 * Run via {@code ./gradlew dumpTransforms}; {@code checkTransforms} runs it into a temporary file
 * and fails the build if the committed table disagrees.
 */
public final class DumpTransforms {

    /** Model units per block. Model JSON is in these; transform stacks are not. */
    private static final float SCALE = 16f;

    private static final Direction[] FACINGS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST,
        Direction.WEST, Direction.UP, Direction.DOWN,
    };

    /**
     * The tray and the package slide as they animate; the table dumps the resting pose, and
     * {@code animated_along} in the output names the axis a consumer composes the slide along.
     */
    private static final float AT_REST = 0f;

    /**
     * The curtain's three poses, converted by the same method the game calls, since the sign of a
     * swing is the curtain's business. Everything between rest and either peak is a linear blend
     * of the angle, so a fixture at each end pins the whole range.
     */
    private static final float HANGING = 0f;
    private static final float PUSHED_OUT = TransitGateBlockEntity.CURTAIN_PUSHED_OUT;
    private static final float PUSHED_IN = TransitGateBlockEntity.CURTAIN_PUSHED_IN;

    private DumpTransforms() {
    }

    /** An {@link Affine} that accumulates into a matrix and nothing else. */
    private static final class MatrixAffine implements Affine<MatrixAffine> {
        private final Matrix4f matrix = new Matrix4f();

        @Override
        public MatrixAffine translate(float x, float y, float z) {
            matrix.translate(x, y, z);
            return this;
        }

        @Override
        public MatrixAffine rotate(Quaternionfc quaternion) {
            matrix.rotate(quaternion);
            return this;
        }

        @Override
        public MatrixAffine scale(float x, float y, float z) {
            matrix.scale(x, y, z);
            return this;
        }

        @Override
        public MatrixAffine self() {
            return this;
        }
    }

    /** A renderer's chain, replayed for one blockstate facing. */
    private interface Chain {
        void apply(MatrixAffine stack, Direction facing);
    }

    private record Entry(String renderer, String doc, String animatedAlong, Chain chain) {
    }

    private static final Map<String, Entry> TRANSFORMS = new LinkedHashMap<>();

    static {
        String packager = "com.simibubi.create.content.logistics.packager.PackagerRenderer";

        // PackagerRenderer.renderSafe, the hatch partial model. Its `facing` is
        // the blockstate's FACING inverted, which is why every chain here starts
        // by inverting the argument.
        TRANSFORMS.put("packager_hatch", new Entry(packager,
            "The iris, or whatever replaces it. Straddles the opening: one unit "
                + "inside the block and two proud of it.",
            null,
            (stack, blockstateFacing) -> {
                Direction facing = blockstateFacing.getOpposite();
                stack.translate(Vec3.atLowerCornerOf(facing.getNormal())
                        .scale(.49999f))
                    .rotateYCenteredDegrees(AngleHelper.horizontalAngle(facing))
                    .rotateXCenteredDegrees(AngleHelper.verticalAngle(facing));
            }));

        // Same shape, but the distance is the animation's offset rather than a
        // constant, and it uses Direction.toYRot() directly instead of
        // AngleHelper -- the two disagree on the X axis, so they are not
        // interchangeable.
        TRANSFORMS.put("packager_tray", new Entry(packager,
            "The tray that slides out of the opening. Stored at rest; "
                + "getTrayOffset(partialTicks) drives it in game.",
            "opening",
            (stack, blockstateFacing) -> {
                Direction facing = blockstateFacing.getOpposite();
                stack.translate(Vec3.atLowerCornerOf(facing.getNormal())
                        .scale(AT_REST))
                    .rotateYCenteredDegrees(facing.toYRot());
            }));

        // The package itself, drawn as a FIXED item. Note the plain
        // rotateYDegrees rather than the centered form: the chain has already
        // moved to the block centre by then, so centering again would double the
        // offset.
        TRANSFORMS.put("packager_box", new Entry(packager,
            "The package on the tray, drawn as a FIXED item at 1.49 scale.",
            "opening",
            (stack, blockstateFacing) -> {
                Direction facing = blockstateFacing.getOpposite();
                stack.translate(Vec3.atLowerCornerOf(facing.getNormal())
                        .scale(AT_REST))
                    .translate(.5f, .5f, .5f)
                    .rotateYDegrees(facing.toYRot())
                    .translate(0, 2 / 16f, 0)
                    .scale(1.49f, 1.49f, 1.49f);
            }));

        // This mod's own, but each opens with Create's hatch placement before it steps and
        // swings, so they are as exposed to Create moving the hatch as the entry above.
        // TransitGateCurtain.place is the same method the game calls.
        String curtain = "me.xiaoeyun.createtransit.client.TransitGateCurtain";
        for (int strip = 0; strip < TransitGateCurtain.STRIPS; strip++) {
            int column = strip;
            TRANSFORMS.put("transit_gate_flap_" + strip, new Entry(curtain,
                "Strip " + strip + " of " + TransitGateCurtain.STRIPS
                    + " in a transit gate's curtain, hanging still.",
                null,
                (stack, blockstateFacing) ->
                    TransitGateCurtain.place(stack, blockstateFacing, column, HANGING)));
            TRANSFORMS.put("transit_gate_flap_" + strip + "_out", new Entry(curtain,
                "The same strip at the peak of an arrival, when the loaded tray "
                    + "is travelling out through the curtain.",
                null,
                (stack, blockstateFacing) ->
                    TransitGateCurtain.place(stack, blockstateFacing, column, PUSHED_OUT)));
            TRANSFORMS.put("transit_gate_flap_" + strip + "_in", new Entry(curtain,
                "And at the peak of a departure, when the loaded tray is coming "
                    + "back in through it. The mirror of the pose above, because "
                    + "the strips follow whichever way the box is riding.",
                null,
                (stack, blockstateFacing) ->
                    TransitGateCurtain.place(stack, blockstateFacing, column, PUSHED_IN)));
        }
    }

    private static float[][] matrixFor(Chain chain, Direction facing) {
        MatrixAffine stack = new MatrixAffine();
        chain.apply(stack, facing);

        float[][] rows = new float[4][4];
        for (int row = 0; row < 4; row++)
            for (int col = 0; col < 4; col++)
                rows[row][col] = stack.matrix.get(col, row);

        // The chain works in block space, so Create's .49999f is half a block.
        // Model JSON counts in sixteenths, so the translation column converts;
        // the rest of the matrix -- rotation and scale -- is unitless.
        for (int row = 0; row < 3; row++)
            rows[row][3] *= SCALE;
        return rows;
    }

    /** Round away float noise so the file is stable across runs and diffable. */
    private static String number(float v) {
        double rounded = Math.round(v * 1e6d) / 1e6d;
        if (Math.abs(rounded) < 1e-9d)
            rounded = 0d;
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e15d)
            return String.format("%.1f", rounded);
        return java.math.BigDecimal.valueOf(rounded)
            .stripTrailingZeros()
            .toPlainString();
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\")
            .replace("\"", "\\\"") + '"';
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("usage: DumpTransforms <output.json> <create-version>");
            System.exit(2);
        }
        Path out = Paths.get(args[0]);
        String version = args[1];

        StringBuilder b = new StringBuilder();
        b.append("{\n");
        b.append("  \"mod\": \"create\",\n");
        b.append("  \"version\": ")
            .append(quote(version))
            .append(",\n");
        b.append("  \"units\": \"model\",\n");
        b.append("  \"note\": \"Matrices are keyed by the BLOCKSTATE facing, not "
            + "the renderer's internal facing variable, so a consumer needs no "
            + "semantics of its own. Row-major 4x4, model units (16 per block).\",\n");
        b.append("  \"producer\": ")
            .append(quote("./gradlew dumpTransforms (" + DumpTransforms.class.getName()
                + ") -- replays each renderer's own chain through Flywheel's "
                + "Affine default methods, so it cannot drift from Create"))
            .append(",\n");
        b.append("  \"transforms\": {\n");

        int n = 0;
        for (Map.Entry<String, Entry> e : TRANSFORMS.entrySet()) {
            Entry spec = e.getValue();
            b.append("    ")
                .append(quote(e.getKey()))
                .append(": {\n");
            b.append("      \"renderer\": ")
                .append(quote(spec.renderer()))
                .append(",\n");
            b.append("      \"doc\": ")
                .append(quote(spec.doc()))
                .append(",\n");
            b.append("      \"animated_along\": ")
                .append(spec.animatedAlong() == null ? "null" : quote(spec.animatedAlong()))
                .append(",\n");
            b.append("      \"matrices\": {\n");
            for (int f = 0; f < FACINGS.length; f++) {
                float[][] m = matrixFor(spec.chain(), FACINGS[f]);
                b.append("        ")
                    .append(quote(FACINGS[f].getSerializedName()))
                    .append(": [");
                for (int row = 0; row < 4; row++) {
                    b.append(row == 0 ? "[" : ", [");
                    for (int col = 0; col < 4; col++)
                        b.append(col == 0 ? "" : ", ")
                            .append(number(m[row][col]));
                    b.append(']');
                }
                b.append(']')
                    .append(f == FACINGS.length - 1 ? "\n" : ",\n");
            }
            b.append("      }\n");
            b.append("    }")
                .append(++n == TRANSFORMS.size() ? "\n" : ",\n");
        }
        b.append("  }\n}\n");

        try {
            Path parent = out.getParent();
            if (parent != null)
                Files.createDirectories(parent);
            Files.write(out, b.toString()
                .getBytes(StandardCharsets.UTF_8));
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
        System.out.println("wrote " + out.toAbsolutePath());
        for (Map.Entry<String, Entry> e : TRANSFORMS.entrySet()) {
            float[][] m = matrixFor(e.getValue()
                .chain(), Direction.SOUTH);
            System.out.printf("  %-16s facing=south translation [%s, %s, %s]%n",
                e.getKey(), number(m[0][3]), number(m[1][3]), number(m[2][3]));
        }
    }
}
