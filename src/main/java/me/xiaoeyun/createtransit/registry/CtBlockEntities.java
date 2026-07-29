package me.xiaoeyun.createtransit.registry;

import static me.xiaoeyun.createtransit.CreateTransit.registrate;

import com.simibubi.create.content.logistics.packager.PackagerRenderer;
import com.simibubi.create.content.logistics.packager.PackagerVisual;
import com.simibubi.create.content.redstone.displayLink.LinkBulbRenderer;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

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
    public static final BlockEntityEntry<TransitGateBlockEntity> TRANSIT_GATE = registrate()
        .blockEntity("transit_gate", TransitGateBlockEntity::new)
        .visual(() -> PackagerVisual::new, true)
        .validBlocks(CtBlocks.TRANSIT_GATE)
        .renderer(() -> PackagerRenderer::new)
        .register();

    public static final BlockEntityEntry<TransitLinkBlockEntity> TRANSIT_LINK = registrate()
        .blockEntity("transit_link", TransitLinkBlockEntity::new)
        .validBlocks(CtBlocks.TRANSIT_LINK)
        .renderer(() -> LinkBulbRenderer::new)
        .register();

    public static void register() {}
}
