package me.xiaoeyun.createtransit.client.ponder;

import static me.xiaoeyun.createtransit.client.ponder.TransitScenes.DESTINATION;
import static me.xiaoeyun.createtransit.client.ponder.TransitScenes.box;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PonderHilo;

import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.ParrotPose.FacePointOfInterestPose;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What a Transit Link declares that a Stock Link does not.
 *
 * {@link TransitScenes} carries the rules every storyboard here follows: why
 * each trigger is scripted, where the freight comes from, and why a caption can
 * never move into a helper.
 */
public class TransitLinkScenes {

    /** transit_link's own border, named after the warehouse the link stands on. */
    private static final String WAREHOUSE = "warehouse";
    /** Where this scene's shipments are headed. Local to it: the other scenes deliver to a drawer. */
    private static final String FACTORY = "factory";

    public static void transitLink(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("transit_link", "Declaring a border with the Transit Link");
        scene.configureBasePlate(0, 0, 7);
        scene.world()
            .showSection(util.select()
                .layer(0), Direction.UP);

        // Create's own stock_link diorama with our link on both stacks: a
        // warehouse behind one and an ordinary chest pair behind the other.
        // Nothing here moves and nothing rides a belt, so the lesson is carried
        // by the two stacks standing side by side and by what they are called.
        BlockPos namedPackager = util.grid()
            .at(4, 1, 3);
        BlockPos namedLink = util.grid()
            .at(4, 2, 3);
        BlockPos defaultPackager = util.grid()
            .at(2, 1, 3);
        BlockPos defaultLink = util.grid()
            .at(2, 2, 3);
        Selection warehouse = util.select()
            .fromTo(4, 1, 4, 5, 2, 5);
        Selection chests = util.select()
            .fromTo(2, 1, 4, 2, 1, 5);
        BlockPos lever = util.grid()
            .at(1, 2, 3);

        // The label is never drawn in a Ponder world -- no renderer reads
        // TransitLinkBlockEntity.getLabel -- so it is not baked into the
        // schematic either. The address on the package is real, though, and
        // comes from the same helper the mod uses.
        // The two stacks differ only in what their link stamps on the way out,
        // so the two boxes have to differ the same way -- a blank name is the
        // default lane, which pushEndpoint writes as a layer of its own.
        ItemStack namedBox = box(AddressLabels.push(WAREHOUSE, FACTORY),
            new ItemStack(Items.IRON_INGOT, 32));
        ItemStack defaultBox = box(AddressLabels.pushEndpoint("", FACTORY),
            new ItemStack(Items.IRON_INGOT, 32));

        scene.idle(10);
        scene.world()
            .showSection(util.select()
                .position(defaultPackager), Direction.NORTH);
        scene.idle(5);
        scene.world()
            .showSection(chests, Direction.NORTH);
        scene.idle(5);
        scene.world()
            .showSection(util.select()
                .position(defaultLink), Direction.DOWN);
        scene.idle(10);
        scene.world()
            .showSection(util.select()
                .position(namedPackager), Direction.NORTH);
        scene.idle(5);
        scene.world()
            .showSection(warehouse, Direction.NORTH);
        scene.idle(5);
        scene.world()
            .showSection(util.select()
                .position(namedLink), Direction.DOWN);
        scene.idle(10);
        scene.world()
            .showSection(util.select()
                .fromTo(4, 1, 1, 5, 1, 1), Direction.DOWN);
        scene.special()
            .createBirb(util.vector()
                .centerOf(util.grid()
                    .at(5, 1, 1)),
                FacePointOfInterestPose::new);
        scene.idle(20);

        scene.overlay()
            .showText(60)
            .attachKeyFrame()
            .text("Transit Links are Stock Links that also declare a border")
            .pointAt(util.vector()
                .topOf(namedLink))
            .placeNearTarget();
        scene.idle(60);

        scene.overlay()
            .showControls(util.vector()
                .topOf(namedLink), Pointing.DOWN, 40)
            .rightClick();
        scene.idle(20);
        scene.overlay()
            .showText(60)
            .attachKeyFrame()
            .text("Right-click the link to write its transit label")
            .pointAt(util.vector()
                .topOf(namedLink))
            .placeNearTarget();
        scene.idle(60);

        PonderHilo.linkEffect(scene, namedLink);
        PonderHilo.linkEffect(scene, defaultLink);
        PonderHilo.packagerCreate(scene, namedPackager, namedBox);
        PonderHilo.packagerCreate(scene, defaultPackager, defaultBox);
        scene.effects()
            .indicateSuccess(namedPackager);
        scene.effects()
            .indicateSuccess(defaultPackager);
        scene.idle(25);

        scene.overlay()
            .showText(50)
            .text("→ Warehouse → Factory")
            .colored(PonderPalette.OUTPUT)
            .pointAt(util.vector()
                .centerOf(namedPackager))
            .placeNearTarget();

        scene.overlay()
            .showText(50)
            .text("→ *default lane* → Factory")
            .colored(PonderPalette.BLUE)
            .pointAt(util.vector()
                .centerOf(defaultPackager))
            .placeNearTarget();
        scene.idle(70);

        // The lever is a prop for one beat. Create bakes it one cell off the
        // casing and slides it over; ours is baked where it lands, because the
        // analog-lever beat that occupies that cell upstream is not staged here.
        scene.world()
            .showSection(util.select()
                .position(1, 1, 3), Direction.EAST);
        scene.idle(10);
        ElementLink<WorldSectionElement> leverL = scene.world()
            .showIndependentSection(util.select()
                .position(lever), Direction.DOWN);
        scene.idle(20);

        scene.world()
            .toggleRedstonePower(util.select()
                .fromTo(lever, defaultLink));
        scene.effects()
            .indicateRedstone(lever);
        scene.idle(10);
        scene.overlay()
            .showControls(util.vector()
                .centerOf(defaultLink), Pointing.DOWN, 40)
            .withItem(new ItemStack(Items.BARRIER));
        scene.idle(20);

        scene.overlay()
            .showText(80)
            .attachKeyFrame()
            .text("Full redstone power will stop a link from broadcasting")
            .colored(PonderPalette.RED)
            .pointAt(util.vector()
                .centerOf(defaultLink))
            .placeNearTarget();
        scene.idle(70);

        scene.world()
            .toggleRedstonePower(util.select()
                .fromTo(lever, defaultLink));
        scene.idle(10);
        scene.world()
            .hideIndependentSection(leverL, Direction.SOUTH);
        scene.idle(20);
    }

}
