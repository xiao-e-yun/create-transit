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
 * Two chapters, on two dioramas: {@link Lane} and the beats that take one belong to crossing, and
 * merging addresses its blocks outright.
 *
 * {@link TransitScenes} carries the rules every storyboard here follows: why
 * each trigger is scripted, where the freight comes from, and why a caption can
 * never move into a helper.
 */
public class TransitGateScenes {

    /** A border this gate does not keep, and an address behind it: well formed, simply someone else's. */
    private static final String FOREIGN_BORDER = "harbor";
    private static final String FOREIGN_DESTINATION = "drawer 9";

    /**
     * The diorama's northern lane, the only one this chapter runs on: it is the one gate with a
     * face free for a sign, and a sign is half of what the chapter teaches.
     */
    private static final int LANE = 2;

    public static void transitGate(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("transit_gate", "Crossing a border with the Transit Gate");

        // The lane opens unsigned: the sign is a block of the diorama like any other, and
        // stage() leaves it out. The two gates behind it are the closing beat.
        Lane lane = new Lane(util);
        BlockPos sign = util.grid()
            .at(3, 2, 1);
        BlockPos middleGate = util.grid()
            .at(3, 2, 3);
        BlockPos farGate = util.grid()
            .at(3, 2, 4);
        stage(scene, util, lane);
        scene.idle(20);

        // Two borders deep, so the gate can strip its own layer and still leave an address behind.
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

        // The tray only comes out of the gate's front, where the outbound funnel is, so every box
        // shown is one drawn back out of the buffer.
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

        // One outline, issued twice under the same slot, so Ponder grows the one it already has
        // instead of drawing a second.
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
     * Merging, on Create's own stock ticker diorama with the belt corner turned into a customs
     * post. {@code findPackagersForRequest} fans one order across both of upstream's packagers,
     * and the near one stands two blocks of belt closer to the border, so the two parts leave at
     * once and arrive thirty ticks apart with nothing staging the gap.
     *
     * Every idle between a package joining a belt and being taken off it comes off upstream
     * numbers rather than a guess:
     *
     * <ul>
     * <li>{@code BeltBlockEntity.getBeltMovementSpeed} is {@code getSpeed()/480}, so Speed 32 is a
     * block every fifteen ticks;
     * <li>an inserted package lands at {@code index + .5f - signum(speed)/16f} -- the centre of the
     * segment it was handed to, not its far edge -- so the near packager is one block from the
     * corner and the far one three;
     * <li>{@code removeItemsFromBelt} reaches half a block either side of a segment's centre.
     * </ul>
     *
     * Which puts the near package in reach from tick 7 to 23 and the far one from 37 to 53; the
     * idles below land on 15 and 45. The slowdown multiplies rather than calling
     * {@code setKineticSpeed}, because the two belts turn a corner and run at opposite signs.
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

        // Belt, then the cogwheel on its pulley, then the large cogwheel meshing with it. A
        // Ponder world computes no kinetics, so there is no motor to leave out.
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

        // The order: the border is named, both links answer, and both packagers pack.
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

        // An eighth speed: eighty ticks of reading spend ten ticks of belt, so the second package
        // is still visibly on its way.
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
     * One of the diorama's three lanes: a row along x at a fixed z. A package rides west along the
     * belt at y=1, is taken off by the intake funnel and parked in the Item Vault, and comes back
     * out through the gate — what a gate faces away from is its own storage.
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
     * The opening every chapter shares, staged the way {@code TunnelScenes.andesite} stages a belt:
     * floor, drivetrain, the belt along its own length, then the things standing on it. The
     * diorama hands us two segments already cased, so the casing comes off before anything is
     * shown and each piece goes back on as its block lands.
     *
     * The base plate is five of the diorama's six columns; the sixth carries the drivetrain and
     * the belts' far pulleys, which shipped scenes keep off the checkerboard.
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
        // The diorama drives its three lanes in series -- cog, shaft, then each belt's far pulley
        // passing it on -- and the shaft ends where the northern lane begins, so a chapter staging
        // a lane further along would have to reveal the pulleys between.
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
     * The lever is a prop for one beat: it arrives on its own section and leaves again, the way
     * {@code StockLinkScenes} stages its redstone lesson. Pairs with {@link #unpowerLever}.
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
     * What {@code mergeOrder} would have handed back, assembled here because it runs on the server
     * and a Ponder world is client-side.
     */
    private static ItemStack merged(ItemStack... contents) {
        ItemStack box = PackageItem.containing(List.of(contents));
        PackageItem.addAddress(box, AddressLabels.stripHeadLabel(AddressLabels.push(BORDER, DESTINATION)));
        PackageItem.setOrder(box, PARENT_ORDER, 0, true, 0, true, null);
        return TransitPackaging.restyle(box);
    }

    /**
     * Puts a belt segment's andesite casing back on as the block standing on it lands, the way
     * {@code TunnelScenes.andesite} does. The block property draws a casing at all and the block
     * entity names which one, so both have to be said, and the trailing {@code true} is
     * {@code reDrawBlocks} — without it the segment keeps its old model and no casing appears.
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
