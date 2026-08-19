package me.xiaoeyun.createtransit.client.ponder;

import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PonderHilo;

import static me.xiaoeyun.createtransit.client.ponder.TransitScenes.DESTINATION;
import static me.xiaoeyun.createtransit.client.ponder.TransitScenes.box;
import me.xiaoeyun.createtransit.registry.CtBlocks;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ParrotPose.FacePointOfInterestPose;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;

/**
 * Lending a network out through a ticker, and walling one off behind a border.
 *
 * {@link TransitScenes} carries the rules every storyboard here follows: why
 * each trigger is scripted, where the freight comes from, and why a caption can
 * never move into a helper.
 */
public class TransitTickerScenes {

    public static void transitTicker(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("transit_ticker", "Sharing a network with the Transit Ticker");
        scene.configureBasePlate(0, 0, 7);
        scene.world()
            .showSection(util.select()
                .layer(0), Direction.UP);

        BlockPos childPackager = util.grid()
            .at(1, 1, 3);
        BlockPos childLink = util.grid()
            .at(1, 2, 3);
        BlockPos childTicker = util.grid()
            .at(4, 1, 3);
        BlockPos parentLink = util.grid()
            .at(4, 2, 3);
        BlockPos stockTicker = util.grid()
            .at(4, 1, 1);
        AABB child = new AABB(childPackager).minmax(new AABB(util.grid()
            .at(1, 2, 5)));

        // The child network is built on camera rather than switched on: the
        // packager arrives unlinked, the link appears on its lid, and only then
        // does the packager answer by linking up. Nothing moves to get there.
        scene.idle(10);
        scene.world()
            .cycleBlockProperty(childPackager, PackagerBlock.LINKED);
        scene.world()
            .showSection(util.select()
                .fromTo(1, 1, 3, 1, 1, 5), Direction.NORTH);
        scene.idle(15);
        scene.world()
            .showSection(util.select()
                .position(childLink), Direction.DOWN);
        scene.idle(15);
        scene.world()
            .cycleBlockProperty(childPackager, PackagerBlock.LINKED);
        scene.effects()
            .indicateSuccess(childPackager);
        scene.idle(20);

        scene.overlay()
            .chaseBoundingBoxOutline(PonderPalette.BLUE, child, child, 50);
        scene.overlay()
            .showText(50)
            .attachKeyFrame()
            .text("One logistics network, with a warehouse of its own")
            .colored(PonderPalette.BLUE)
            .pointAt(util.vector()
                .topOf(childLink))
            .placeNearTarget();
        scene.idle(60);

        scene.overlay()
            .showText(60)
            .attachKeyFrame()
            .text("Right-click a Stock link before placement to connect to its network")
            .colored(PonderPalette.BLUE)
            .pointAt(util.vector()
                .centerOf(childLink))
            .placeNearTarget();
        scene.idle(20);

        scene.overlay()
            .showControls(util.vector()
                .topOf(childLink), Pointing.DOWN, 50)
            .rightClick()
            .withItem(CtBlocks.TRANSIT_TICKER.asStack());
        scene.idle(5);
        AABB bb = new AABB(childLink);
        bb = bb.deflate(1 / 16f).contract(0, 8 / 16f, 0);
        scene.overlay()
            .chaseBoundingBoxOutline(PonderPalette.BLUE, childLink, bb, 50);
        scene.idle(60);

        scene.world()
            .showSection(util.select()
                .position(childTicker), Direction.DOWN);
        scene.idle(20);

        scene.world()
            .showSection(util.select()
                .fromTo(4, 1, 1, 5, 1, 1), Direction.DOWN);
        scene.special()
            .createBirb(util.vector()
                .centerOf(util.grid()
                    .at(5, 1, 1)),
                FacePointOfInterestPose::new);
        scene.idle(5);

        scene.world()
            .showSection(util.select()
                .position(parentLink), Direction.DOWN);
        scene.idle(15);

        PonderHilo.linkEffect(scene, childLink);
        PonderHilo.linkEffect(scene, parentLink);
        scene.idle(20);

        AABB pl = new AABB(parentLink);
        pl = pl.deflate(1 / 16f).contract(0, 8 / 16f, 0);
        scene.overlay()
            .chaseBoundingBoxOutline(PonderPalette.GREEN, parentLink, pl, 120);

        scene.overlay()
            .showText(50)
            .attachKeyFrame()
            .text("Then mount a link belonging to a second network on top")
            .colored(PonderPalette.GREEN)
            .pointAt(util.vector()
                .centerOf(parentLink))
            .placeNearTarget();

        scene.idle(60);

        scene.overlay()
            .chaseBoundingBoxOutline(PonderPalette.BLUE, child, child, 60);
        scene.overlay()
            .chaseBoundingBoxOutline(PonderPalette.BLUE, childTicker, new AABB(childTicker), 60);
        scene.overlay()
            .showText(60)
            .attachKeyFrame()
            .text("Child network")
            .colored(PonderPalette.BLUE)
            .pointAt(util.vector()
                .centerOf(childTicker))
            .placeNearTarget();

        scene.overlay()
            .chaseBoundingBoxOutline(PonderPalette.GREEN, stockTicker, new AABB(stockTicker), 60);
        scene.overlay()
            .showText(60)
            .text("Parent network")
            .colored(PonderPalette.GREEN)
            .pointAt(util.vector()
                .centerOf(parentLink))
            .placeNearTarget();

        scene.idle(60);

        // One order, staged the way StockTickerScenes stages its own: the
        // keeper is asked, each link answers in turn, and the child's packager
        // is the thing that actually produces the box. No server logic runs
        // here, so every one of those steps is an instruction.
        BlockPos keeper = util.grid()
            .at(5, 1, 1);
        scene.overlay()
            .showControls(util.vector()
                .topOf(keeper), Pointing.DOWN, 40)
            .rightClick();
        scene.idle(10);

        scene.overlay()
            .showText(70)
            .attachKeyFrame()
            .text("Order from the parent network as usual")
            .colored(PonderPalette.GREEN)
            .pointAt(util.vector()
                .centerOf(stockTicker))
            .placeNearTarget();
        scene.idle(70);

        scene.effects()
            .indicateSuccess(stockTicker);
        PonderHilo.linkEffect(scene, parentLink);
        scene.idle(10);
        PonderHilo.linkEffect(scene, childLink);
        scene.idle(10);
        PonderHilo.packagerCreate(scene, childPackager, box(DESTINATION, new ItemStack(Items.COPPER_INGOT)));
        scene.effects()
            .indicateSuccess(childPackager);
        scene.idle(20);

        scene.overlay()
            .showText(80)
            .attachKeyFrame()
            .text("The child network packs it, and the ticker hands it over")
            .colored(PonderPalette.BLUE)
            .pointAt(util.vector()
                .blockSurface(childPackager, Direction.NORTH))
            .placeNearTarget();
        scene.idle(90);

        // The other way to mount it. The schematic bakes both links as
        // face=floor,facing=north on their lids, so the swap keeps that state
        // and only changes which block wears it.
        PonderHilo.packagerClear(scene, childPackager);
        scene.world()
            .setBlock(parentLink, CtBlocks.TRANSIT_LINK.getDefaultState()
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACE, AttachFace.FLOOR)
                .setValue(FaceAttachedHorizontalDirectionalBlock.FACING, Direction.NORTH), true);
        scene.overlay()
            .showControls(util.vector()
                .topOf(parentLink), Pointing.DOWN, 40)
            .rightClick()
            .withItem(CtBlocks.TRANSIT_LINK.asStack());
        scene.idle(20);

        scene.overlay()
            .chaseBoundingBoxOutline(PonderPalette.RED, parentLink, pl, 80);
        scene.overlay()
            .showText(60)
            .attachKeyFrame()
            .text("Mount a Transit Link instead, and the child becomes foreign territory")
            .colored(PonderPalette.BLUE)
            .pointAt(util.vector()
                .topOf(parentLink))
            .placeNearTarget();
        scene.idle(60);
    }
}
