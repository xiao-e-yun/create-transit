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

import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Writes out the matrices a block entity renderer applies to its partial models.
 *
 * A block model's coordinates mean what they say, because the model is baked
 * into the chunk mesh. A partial model's do not: a renderer transforms it every
 * frame before drawing, and Minecraft's model format has no way to record that.
 * So geometry that looks right in Blockbench can land eight units away in game,
 * and any tool reading the model file alone will show it in the wrong place
 * while looking entirely plausible.
 *
 * This closes that gap by <em>running</em> each renderer's transform rather than
 * describing it. Nothing here reimplements Create's or Flywheel's arithmetic —
 * the chains below are the renderers' own chains, so the result cannot drift the
 * way a hand-ported copy would.
 *
 * <h2>Why a hand-rolled Affine and not a PoseStack</h2>
 *
 * The obvious way to replay a chain is {@code TransformStack.of(new PoseStack())}
 * — and it does not work outside the game, because Flywheel reaches a
 * PoseStack's matrix through an interface it <em>mixes into</em> the class at
 * launch. Without the mixin transformer that cast fails. The matrix math is
 * pure; the accessor is not.
 *
 * {@link MatrixAffine} below sidesteps that entirely, and is arguably the better
 * answer rather than a workaround. {@link Affine} is dozens of default methods
 * over exactly three abstract ones — translate, rotate, scale — so supplying
 * those three over a JOML matrix leaves every derived method
 * ({@code rotateYCenteredDegrees}, {@code rotateAround}, the lot) as Flywheel's
 * code, executed rather than transcribed. The three primitives are the one part
 * with no room for interpretation, and dropping PoseStack drops the dependency
 * on a running game with it.
 *
 * JOML's {@code translate}/{@code rotate}/{@code scale} post-multiply, which is
 * PoseStack's convention: the last call applies to the model first.
 * {@code Affine.rotateAround} is the proof that this is the contract, since
 * {@code translate(c).rotate(q).translateBack(c)} only means "rotate about c"
 * under post-multiplication.
 *
 * <h2>Keying</h2>
 *
 * Matrices are keyed by the <em>blockstate</em> facing rather than by whatever
 * local variable a renderer derives from it. That keeps every
 * {@code getOpposite()} and every angle helper on this side of the file, so a
 * consumer does a pure lookup and cannot get the semantics wrong — the worst it
 * can do is ask for a key that does not exist.
 *
 * Run via {@code ./gradlew dumpTransforms}; {@code checkTransforms} runs it into
 * a temporary file and fails the build if the committed table disagrees, which
 * is why the table cannot go quietly stale when Create updates.
 */
public final class DumpTransforms {

    /** Model units per block. Model JSON is in these; transform stacks are not. */
    private static final float SCALE = 16f;

    private static final Direction[] FACINGS = {
        Direction.NORTH, Direction.SOUTH, Direction.EAST,
        Direction.WEST, Direction.UP, Direction.DOWN,
    };

    /**
     * The tray and the package slide as they animate. Dumping the resting pose
     * keeps the table a pure function of the blockstate; a consumer wanting a
     * mid-animation frame composes the extra slide itself, which is what
     * {@code animated_along} in the output is there to tell it.
     */
    private static final float AT_REST = 0f;

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
