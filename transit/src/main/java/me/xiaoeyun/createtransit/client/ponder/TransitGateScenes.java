package me.xiaoeyun.createtransit.client.ponder;

import java.util.List;

import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PonderHilo;

import static me.xiaoeyun.createtransit.client.ponder.TransitScenes.BORDER;
import static me.xiaoeyun.createtransit.client.ponder.TransitScenes.DESTINATION;
import static me.xiaoeyun.createtransit.client.ponder.TransitScenes.PARENT_ORDER;
import static me.xiaoeyun.createtransit.client.ponder.TransitScenes.box;
import static me.xiaoeyun.createtransit.client.ponder.TransitScenes.fragment;
import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import me.xiaoeyun.createtransit.content.transit.TransitPackaging;
import net.createmod.catnip.nbt.NBTHelper;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Two chapters, on two dioramas.
 *
 * Crossing runs on {@code transit_gate.nbt}, three identical lanes of which this
 * chapter stages the northern -- the only gate with a face free for a sign --
 * revealing the other two only to show the label reaching them. Merging runs on
 * {@code transit_merge.nbt}, which is Create's own stock ticker diorama with its
 * belt corner turned into a customs post, because merging needs a network that
 * packs one request in two places and the gate diorama has no network at all.
 * {@link Lane} and the beats that take one belong to the first; the second
 * addresses its blocks outright.
 *
 * Caption strings are literals, and Ponder numbers them positionally: it counts
 * the {@code text(...)} calls in execution order into
 * {@code create_transit.ponder.<scene>.text_N}. So they stay one per beat at the
 * top level of their storyboard, never in a loop, a branch or a shared helper --
 * which is the one thing none of the helpers below may ever grow -- and the
 * storyboards stay in scene order with the helpers last.
 * {@link TransitScenes} carries the rest: why every trigger is scripted, and
 * where the freight comes from.
 */
public class TransitGateScenes {

    /**
     * A border this gate does not keep, and an address behind it. Chapter one
     * needs traffic the sign refuses, and refusing is only visible against a
     * label that is well formed and simply someone else's.
     */
    private static final String FOREIGN_BORDER = "harbor";
    private static final String FOREIGN_DESTINATION = "drawer 9";

    /**
     * The diorama's northern lane, which is the only one this chapter runs on:
     * the other two carry no sign, and a sign is half of what it teaches.
     */
    private static final int LANE = 2;

    public static void transitGate(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("transit_gate", "Crossing a border with the Transit Gate");

        // The northern lane, because it is the one gate with a face free for a
        // sign and this chapter hangs one halfway through. It opens unsigned:
        // the sign is a block of the diorama like any other, and stage() leaves
        // it out. The two gates behind it are the closing beat.
        Lane lane = new Lane(util);
        BlockPos sign = util.grid()
            .at(3, 2, 1);
        BlockPos middleGate = util.grid()
            .at(3, 2, 3);
        BlockPos farGate = util.grid()
            .at(3, 2, 4);
        stage(scene, util, lane);
        scene.idle(20);

        // Two borders deep, so the gate can strip its own layer and still leave
        // something addressed behind. The foreign box is the same shape one
        // border over -- well formed, simply not this gate's.
        String foreign = AddressLabels.push(FOREIGN_BORDER, FOREIGN_DESTINATION);
        ItemStack arrivingBox = box(AddressLabels.push(BORDER, DESTINATION), new ItemStack(Items.IRON_INGOT, 32));
        ItemStack clearedBox = box(DESTINATION, new ItemStack(Items.IRON_INGOT, 32));
        ItemStack foreignBox = box(foreign, new ItemStack(Items.COPPER_INGOT, 8));
        // pushEndpoint, not push: it is what attemptToSend calls, and the two
        // disagree about a blank name.
        ItemStack forwardedBox = box(AddressLabels.pushEndpoint(BORDER, foreign), new ItemStack(Items.COPPER_INGOT, 8));

        // Crossing

        scene.world()
            .createItemOnBelt(lane.beltStart(), Direction.WEST, arrivingBox);
        scene.idle(10);

        scene.overlay()
            .showText(40)
            .attachKeyFrame()
            .text("→ Depot → Drawer 4")
            .colored(PonderPalette.INPUT)
            .pointAt(util.vector()
                .blockSurface(lane.beltIn(), Direction.UP))
            .placeNearTarget();
        scene.idle(30);
        absorb(scene, lane);

        // The tray only ever comes out of the gate's front, which is the side
        // the outbound funnel is on, so every box the gate shows is one it has
        // taken back out of the buffer.
        present(scene, lane, clearedBox);
        scene.idle(25);

        scene.overlay()
            .showText(60)
            .attachKeyFrame()
            .text("The first label will be stripped off")
            .colored(PonderPalette.OUTPUT)
            .pointAt(util.vector()
                .blockSurface(lane.gate(), Direction.NORTH))
            .placeNearTarget();
        scene.idle(60);

        scene.overlay()
            .showText(40)
            .text("→ Drawer 4")
            .colored(PonderPalette.OUTPUT)
            .pointAt(util.vector()
                .blockSurface(lane.outlet(), Direction.NORTH))
            .placeNearTarget();
        scene.idle(10);

        depart(scene, lane, clearedBox);
        hopOff(scene, lane, clearedBox);

        // The sign, and its two jobs

        scene.world()
            .showSection(util.select()
                .position(sign), Direction.SOUTH);
        scene.idle(15);

        scene.overlay()
            .showText(60)
            .attachKeyFrame()
            .text("A sign's label filters what comes in, and stamps what goes out")
            .pointAt(util.vector()
                .blockSurface(sign, Direction.UP))
            .placeNearTarget();
        scene.idle(65);

        scene.world()
            .createItemOnBelt(lane.beltStart(), Direction.WEST, foreignBox);
        scene.idle(10);

        scene.overlay()
            .showText(40)
            .attachKeyFrame()
            .text("→ Harbor → Drawer 9")
            .colored(PonderPalette.RED)
            .pointAt(util.vector()
                .blockSurface(lane.beltIn(), Direction.UP))
            .placeNearTarget();
        scene.idle(30);
        absorb(scene, lane);

        // Departure, which does not consult the sign at all

        ElementLink<WorldSectionElement> leverL = powerLever(scene, util, lane);
        present(scene, lane, forwardedBox);
        scene.idle(25);

        scene.overlay()
            .showText(50)
            .attachKeyFrame()
            .text("Given redstone power, the gate stamps its own label on the way out")
            .pointAt(util.vector()
                .blockSurface(lane.gate(), Direction.NORTH))
            .placeNearTarget();
        scene.idle(60);

        scene.overlay()
            .showText(40)
            .text("→ Depot → Harbor → Drawer 9")
            .colored(PonderPalette.OUTPUT)
            .pointAt(util.vector()
                .blockSurface(lane.outlet(), Direction.NORTH))
            .placeNearTarget();
        scene.idle(10);

        depart(scene, lane, forwardedBox);
        hopOff(scene, lane, forwardedBox);
        unpowerLever(scene, util, lane, leverL);

        // One sign, three gates. Only that it reaches is said; the range, the
        // tie-break, that it never chains and that a gate with its own sign
        // keeps it are all left for the goggles, which name the donor's
        // coordinates outright.
        // One outline, issued twice under the same slot, so Ponder grows the one
        // it already has instead of drawing a second: the label's reach is the
        // thing moving, and it reads as reach only if the box was around one
        // gate first.
        scene.overlay()
            .showOutline(PonderPalette.GREEN, lane.gate(), util.select()
                .position(lane.gate()), 60);
        scene.idle(30);

        scene.world()
            .showSection(util.select()
                .fromTo(0, 1, 3, 5, 1, 4), Direction.SOUTH);
        scene.idle(5);
        scene.world()
            .showSection(util.select()
                .fromTo(1, 2, 3, 4, 2, 4), Direction.DOWN);
        scene.idle(20);

        scene.overlay()
            .showOutline(PonderPalette.GREEN, lane.gate(), util.select()
                .fromTo(3, 2, 2, 3, 2, 4), 75);
        scene.effects()
            .indicateSuccess(middleGate);
        scene.effects()
            .indicateSuccess(farGate);

        scene.overlay()
            .showText(65)
            .attachKeyFrame()
            .text("The label extends to the gates beside it")
            .pointAt(util.vector()
                .blockSurface(middleGate, Direction.UP))
            .placeNearTarget();
        scene.idle(75);
    }

    /**
     * Merging, on Create's own stock ticker diorama with the belt corner turned
     * into a customs post.
     *
     * Everything upstream of the corner is theirs and is left alone: one request
     * reaches two packagers, each packs part of it, and both parts ride the same
     * belt west. What changes is what the corner does with them -- and the two
     * packagers, which upstream put there for its own reasons, are exactly why
     * this chapter works. findPackagersForRequest fans an order out across
     * whichever packagers can fill it, so the parts are packed in two places,
     * and the near one stands two blocks of belt closer to the border than the
     * far one. Both leave at once and still arrive apart, with nothing staging
     * the gap.
     *
     * That gap is thirty ticks of belt, which is no time to read a line of text
     * in, so the world is slowed to an eighth for it. Multiplication rather than
     * setKineticSpeed, because the two belts turn a corner and run at opposite
     * signs -- multiplying keeps each one's sign, and one flat number would send
     * half the scene backwards.
     *
     * Every idle between a package joining a belt and being taken off it is
     * aimed at a window the belt decides, and both ends of that window come off
     * upstream rather than off a guess:
     *
     * <ul>
     * <li>{@code BeltBlockEntity.getBeltMovementSpeed} is {@code getSpeed()/480},
     * so Speed 32 is a block every fifteen ticks;
     * <li>an inserted package lands at {@code index + .5f - signum(speed)/16f} --
     * the centre of the segment it was handed to, not its far edge -- so the near
     * packager is one block from the corner and the far one three;
     * <li>{@code removeItemsFromBelt} reaches half a block either side of a
     * segment's centre.
     * </ul>
     *
     * Which puts the near package in reach from tick 7 to 23 and the far one from
     * 37 to 53. The idles below land on 15 and 45, the middle of each.
     */
    public static void transitGateMerge(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("transit_gate_merge", "Merging a shipment at the border");
        scene.configureBasePlate(0, 0, 7);

        BlockPos nearPackager = util.grid()
            .at(3, 2, 5);
        BlockPos farPackager = util.grid()
            .at(5, 2, 5);
        BlockPos nearLink = util.grid()
            .at(3, 3, 5);
        BlockPos farLink = util.grid()
            .at(5, 3, 5);
        BlockPos nearFunnel = util.grid()
            .at(3, 2, 4);
        BlockPos farFunnel = util.grid()
            .at(5, 2, 4);
        BlockPos nearBelt = util.grid()
            .at(3, 1, 4);
        BlockPos farBelt = util.grid()
            .at(5, 1, 4);
        // The corner: the last cell of the inbound belt, the funnel that lifts
        // off it, the barrel that holds what is lifted, and the gate reading it.
        BlockPos corner = util.grid()
            .at(2, 1, 4);
        BlockPos intake = util.grid()
            .at(2, 2, 4);
        BlockPos buffer = util.grid()
            .at(1, 2, 4);
        BlockPos gate = util.grid()
            .at(1, 2, 3);
        BlockPos outlet = util.grid()
            .at(1, 2, 2);
        BlockPos beltOut = util.grid()
            .at(1, 1, 2);
        BlockPos beltEnd = util.grid()
            .at(1, 1, 0);
        BlockPos border = util.grid()
            .at(3, 2, 1);

        // Belt, then the cogwheel on its pulley, then the large cogwheel that
        // meshes with it. There is no motor to leave out: a Ponder world computes
        // no kinetics, so the diorama carries none.
        Selection inbound = util.select()
            .fromTo(2, 1, 4, 6, 1, 4)
            .add(util.select()
                .position(6, 1, 3))
            .add(util.select()
                .position(7, 0, 3));
        Selection outbound = util.select()
            .fromTo(1, 1, 0, 1, 1, 6)
            .add(util.select()
                .position(2, 1, 6))
            .add(util.select()
                .position(2, 0, 7));
        // Only the belts, and one call each. The cogwheels keep the gearing baked
        // into them -- sweeping them up too would spin a large cogwheel at belt
        // speed.
        scene.world()
            .setKineticSpeed(util.select()
                .fromTo(2, 1, 4, 6, 1, 4), 32f);
        scene.world()
            .setKineticSpeed(util.select()
                .fromTo(1, 1, 0, 1, 1, 6), -32f);

        // Two fragments of one order, and what mergeOrder would hand back.
        ItemStack first = fragment(new ItemStack(Items.IRON_INGOT, 32), 0, false);
        ItemStack last = fragment(new ItemStack(Items.OAK_PLANKS, 16), 1, true);
        ItemStack whole = merged(new ItemStack(Items.IRON_INGOT, 32), new ItemStack(Items.OAK_PLANKS, 16));

        scene.world()
            .showSection(util.select()
                .layer(0), Direction.UP);
        scene.idle(5);
        scene.world()
            .showSection(util.select()
                .fromTo(3, 1, 5, 5, 3, 6), Direction.DOWN);
        scene.idle(10);
        scene.world()
            .showSection(inbound, Direction.SOUTH);
        scene.idle(5);
        scene.world()
            .showSection(util.select()
                .fromTo(3, 2, 4, 5, 2, 4), Direction.DOWN);
        scene.idle(10);
        scene.world()
            .showSection(outbound, Direction.EAST);
        scene.idle(5);
        scene.world()
            .showSection(util.select()
                .fromTo(1, 2, 2, 2, 2, 4), Direction.DOWN);
        scene.idle(10);
        scene.world()
            .showSection(util.select()
                .fromTo(3, 1, 1, 3, 2, 1), Direction.DOWN);
        scene.idle(20);

        scene.overlay()
            .showText(70)
            .attachKeyFrame()
            .text("One request can be packed in more than one place")
            .colored(PonderPalette.INPUT)
            .pointAt(util.vector()
                .of(4.5, 3.5, 5.5))
            .placeNearTarget();
        scene.idle(80);

        // The order, which is the only wireless part of this: the border is
        // named, both links answer, and both packagers pack.
        PonderHilo.linkEffect(scene, border);
        scene.idle(10);
        PonderHilo.linkEffect(scene, nearLink);
        PonderHilo.linkEffect(scene, farLink);
        scene.idle(10);
        PonderHilo.packagerCreate(scene, nearPackager, first);
        PonderHilo.packagerCreate(scene, farPackager, last);
        scene.effects()
            .indicateSuccess(nearPackager);
        scene.effects()
            .indicateSuccess(farPackager);
        scene.idle(30);

        // Both at once. Everything that happens after this is the belt.
        scene.world()
            .createItemOnBelt(nearBelt, Direction.UP, first);
        PonderHilo.packagerClear(scene, nearPackager);
        scene.world()
            .flapFunnel(nearFunnel, true);
        scene.world()
            .createItemOnBelt(farBelt, Direction.UP, last);
        PonderHilo.packagerClear(scene, farPackager);
        scene.world()
            .flapFunnel(farFunnel, true);
        scene.idle(15);

        scene.world()
            .removeItemsFromBelt(corner);
        scene.world()
            .flapFunnel(intake, false);
        scene.effects()
            .indicateSuccess(buffer);
        scene.idle(5);

        // An eighth speed, so the second package is still visibly on its way
        // while the line explaining why the first one is not moving is read.
        // Eighty ticks of reading spend ten ticks of belt.
        scene.world()
            .multiplyKineticSpeed(util.select()
                .everywhere(), 1 / 8f);
        scene.overlay()
            .showText(70)
            .attachKeyFrame()
            .text("A transit shipment only passes the gate once every part has arrived")
            .pointAt(util.vector()
                .blockSurface(gate, Direction.WEST))
            .placeNearTarget();
        scene.idle(80);
        scene.world()
            .multiplyKineticSpeed(util.select()
                .everywhere(), 8f);
        scene.idle(15);

        scene.world()
            .removeItemsFromBelt(corner);
        scene.world()
            .flapFunnel(intake, false);
        scene.effects()
            .indicateSuccess(buffer);
        PonderHilo.packagerCreate(scene, gate, whole);
        scene.effects()
            .indicateSuccess(gate);
        scene.idle(30);

        scene.world()
            .flapFunnel(outlet, true);
        PonderHilo.packagerClear(scene, gate);
        scene.world()
            .createItemOnBelt(beltOut, Direction.UP, whole);
        scene.idle(30);
        PonderHilo.packageHopsOffBelt(scene, beltEnd, Direction.NORTH, whole);
        scene.idle(25);
    }

    /**
     * One of the diorama's three lanes: a row along x at a fixed z, every
     * position in it following from that one number.
     *
     * A package rides west along the belt at y=1, is taken off it by the intake
     * funnel and parked in the Item Vault, and comes back out through the gate —
     * what a gate faces away from is its own storage.
     */
    private record Lane(SceneBuildingUtil util) {

        BlockPos gate() {
            return at(3, 2);
        }

        /** The vault cell behind this lane's gate: arrivals wait here, departures are drawn back out of here. */
        BlockPos buffer() {
            return at(2, 2);
        }

        BlockPos intake() {
            return at(1, 2);
        }

        BlockPos outlet() {
            return at(4, 2);
        }

        BlockPos beltStart() {
            return at(0, 1);
        }

        BlockPos beltIn() {
            return at(1, 1);
        }

        /** The two segments the diorama bakes cased, because the vault and the gate stand on them. */
        BlockPos beltBuffer() {
            return at(2, 1);
        }

        BlockPos beltGate() {
            return at(3, 1);
        }

        BlockPos beltOut() {
            return at(4, 1);
        }

        BlockPos beltEnd() {
            return at(5, 1);
        }

        /** Not part of the machine: a prop two of the chapters show for one beat. */
        BlockPos lever() {
            return at(3, 3);
        }

        private BlockPos at(int x, int y) {
            return util.grid()
                .at(x, y, LANE);
        }
    }

    /**
     * The opening every chapter shares: base plate, kinetics, then one lane
     * revealed.
     *
     * Staged the way {@code TunnelScenes.andesite} stages a belt: floor, then
     * the drivetrain, then the belt along its own length, and only then the
     * things that stand on it, one at a time. The diorama hands us two segments
     * already cased, so the casing comes off before anything is shown and each
     * piece goes back on as its block lands.
     *
     * The base plate is five of the diorama's six columns: the sixth carries the
     * drivetrain and the belts' far pulleys, which every shipped scene keeps off
     * its checkerboard, and is where a cleared package rides off the end.
     */
    private static void stage(CreateSceneBuilder scene, SceneBuildingUtil util, Lane lane) {
        scene.configureBasePlate(0, 1, 6);
        scene.world()
            .setKineticSpeed(util.select()
                .everywhere(), -16f);

        scene.world()
            .cycleBlockProperty(lane.beltGate(), BeltBlock.CASING);
        scene.world()
            .cycleBlockProperty(lane.beltBuffer(), BeltBlock.CASING);

        scene.world()
            .showSection(util.select()
                .fromTo(0, 0, 1, 5, 0, 5), Direction.UP);
        scene.idle(5);
        scene.world()
            .showSection(util.select()
                .position(4, 0, 0), Direction.UP);
        // The diorama drives its three lanes in series -- cog, shaft, then each
        // belt's far pulley passing it to the next -- and the shaft ends where
        // the northern lane begins, so the drive reads whole with nothing after
        // it. A chapter staging a lane further along would have to reveal the
        // pulleys between.
        scene.world()
            .showSection(util.select()
                .fromTo(5, 1, 0, 5, 1, 1), Direction.UP);
        scene.idle(5);
        scene.world()
            .showSection(util.select()
                .fromTo(5, 1, LANE, 0, 1, LANE), Direction.SOUTH);
        scene.idle(10);
        scene.world()
            .showSection(util.select()
                .fromTo(lane.intake(), lane.outlet()), Direction.DOWN);
        caseBelt(scene, util, lane.beltGate());
        caseBelt(scene, util, lane.beltBuffer());
    }

    /** The intake funnel takes what is waiting on the belt, and the vault behind it holds it. */
    private static void absorb(CreateSceneBuilder scene, Lane lane) {
        scene.world()
            .removeItemsFromBelt(lane.beltIn());
        scene.world()
            .flapFunnel(lane.intake(), false);
        scene.effects()
            .indicateSuccess(lane.buffer());
        scene.idle(20);
    }

    /** The gate holds a box out on its tray, which is always one it has drawn back out of the buffer. */
    private static void present(CreateSceneBuilder scene, Lane lane, ItemStack box) {
        PonderHilo.packagerCreate(scene, lane.gate(), box);
        scene.effects()
            .indicateSuccess(lane.gate());
    }

    /** The tray goes back in and the box leaves through the outbound funnel. */
    private static void depart(CreateSceneBuilder scene, Lane lane, ItemStack box) {
        scene.world()
            .flapFunnel(lane.outlet(), true);
        PonderHilo.packagerClear(scene, lane.gate());
        scene.world()
            .createItemOnBelt(lane.beltOut(), Direction.WEST, box);
    }

    /** The rest of the outbound trip: to the end of the belt and off it. */
    private static void hopOff(CreateSceneBuilder scene, Lane lane, ItemStack box) {
        scene.idle(40);
        PonderHilo.packageHopsOffBelt(scene, lane.beltEnd(), Direction.EAST, box);
        scene.idle(20);
    }

    /**
     * The lever is a prop for one beat, not part of the machine: it arrives on
     * its own section and leaves again, the way {@code StockLinkScenes} stages
     * its own redstone lesson. Pairs with {@link #unpowerLever}, which takes it
     * back off.
     */
    private static ElementLink<WorldSectionElement> powerLever(CreateSceneBuilder scene, SceneBuildingUtil util,
        Lane lane) {
        ElementLink<WorldSectionElement> leverL = scene.world()
            .showIndependentSection(util.select()
                .position(lane.lever()), Direction.DOWN);
        scene.idle(15);
        scene.world()
            .toggleRedstonePower(util.select()
                .fromTo(3, 2, LANE, 3, 3, LANE));
        scene.effects()
            .indicateRedstone(lane.lever());
        return leverL;
    }

    private static void unpowerLever(CreateSceneBuilder scene, SceneBuildingUtil util, Lane lane,
        ElementLink<WorldSectionElement> leverL) {
        scene.world()
            .toggleRedstonePower(util.select()
                .fromTo(3, 2, LANE, 3, 3, LANE));
        scene.idle(10);
        scene.world()
            .hideIndependentSection(leverL, Direction.SOUTH);
        scene.idle(20);
    }

    /**
     * What {@code mergeOrder} would have handed back, assembled here because it
     * runs on the server and a Ponder world is client-side. The one seam in this
     * file: its inputs are real fragments, declarations and all, and the address
     * it leaves under is the arriving one a border lighter.
     */
    private static ItemStack merged(ItemStack... contents) {
        ItemStack box = PackageItem.containing(List.of(contents));
        PackageItem.addAddress(box, AddressLabels.stripHeadLabel(AddressLabels.push(BORDER, DESTINATION)));
        PackageItem.setOrder(box, PARENT_ORDER, 0, true, 0, true, null);
        return TransitPackaging.restyle(box);
    }

    /**
     * Puts a belt segment's andesite casing back on as the block standing on it
     * lands, the way {@code TunnelScenes.andesite} does it.
     *
     * The block property is what draws a casing at all, and the block entity is
     * what names which casing it is, so both have to be said. The trailing
     * {@code true} is {@code reDrawBlocks}: without it the segment keeps the
     * model it already had and the casing never appears.
     */
    private static void caseBelt(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos belt) {
        scene.world()
            .cycleBlockProperty(belt, BeltBlock.CASING);
        scene.world()
            .modifyBlockEntityNBT(util.select()
                .position(belt), BeltBlockEntity.class,
                nbt -> NBTHelper.writeEnum(nbt, "Casing", BeltBlockEntity.CasingType.ANDESITE), true);
    }
}
