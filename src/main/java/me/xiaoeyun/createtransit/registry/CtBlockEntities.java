package me.xiaoeyun.createtransit.registry;

import static me.xiaoeyun.createtransit.CreateTransit.registrate;

import com.simibubi.create.content.redstone.displayLink.LinkBulbRenderer;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import me.xiaoeyun.createtransit.client.TransitGateRenderer;
import me.xiaoeyun.createtransit.client.TransitGateVisual;
import me.xiaoeyun.createtransit.content.transit.TransitGateBlockEntity;
import me.xiaoeyun.createtransit.content.transit.TransitLinkBlockEntity;
import me.xiaoeyun.createtransit.content.ticker.TransitTickerBlockEntity;

public class CtBlockEntities {

    public static final BlockEntityEntry<TransitTickerBlockEntity> TRANSIT_TICKER = registrate()
        .blockEntity("transit_ticker", TransitTickerBlockEntity::new)
        .validBlocks(CtBlocks.TRANSIT_TICKER)
        .register();

    // Create draws a packager's tray, iris and the box passing through it from
    // a renderer, not from the block model, so borrowing the model alone leaves
    // the gate frozen. Both entries below reuse Create's own, which are typed
    // against the base classes ours extend. Mirrors AllBlockEntityTypes.
    //
    // The gate's pair are subclasses of Create's, because its curtain moves per
    // strip and a hatch is one model with one pose. Being our own block entity
    // type is what makes that a registration rather than a mixin: no other
    // packager in the world is reached by it.
    public static final BlockEntityEntry<TransitGateBlockEntity> TRANSIT_GATE = registrate()
        .blockEntity("transit_gate", TransitGateBlockEntity::new)
        .visual(() -> TransitGateVisual::new, true)
        .validBlocks(CtBlocks.TRANSIT_GATE)
        .renderer(() -> TransitGateRenderer::new)
        .register();

    public static final BlockEntityEntry<TransitLinkBlockEntity> TRANSIT_LINK = registrate()
        .blockEntity("transit_link", TransitLinkBlockEntity::new)
        .validBlocks(CtBlocks.TRANSIT_LINK)
        .renderer(() -> LinkBulbRenderer::new)
        .register();

    public static void register() {}
}
