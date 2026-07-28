package me.xiaoeyun.createnestnetwork.registry;

import static me.xiaoeyun.createnestnetwork.CreateNestNetwork.registrate;

import com.simibubi.create.content.logistics.packager.PackagerRenderer;
import com.simibubi.create.content.logistics.packager.PackagerVisual;
import com.simibubi.create.content.redstone.displayLink.LinkBulbRenderer;
import com.tterrag.registrate.util.entry.BlockEntityEntry;

import me.xiaoeyun.createnestnetwork.content.customs.CustomsGateBlockEntity;
import me.xiaoeyun.createnestnetwork.content.customs.CustomsLinkBlockEntity;
import me.xiaoeyun.createnestnetwork.content.proxy.StockProxyerBlockEntity;

public class CnnBlockEntities {

    public static final BlockEntityEntry<StockProxyerBlockEntity> STOCK_PROXYER = registrate()
        .blockEntity("stock_proxyer", StockProxyerBlockEntity::new)
        .validBlocks(CnnBlocks.STOCK_PROXYER)
        .register();

    // Create draws a packager's tray, iris and the box passing through it from
    // a renderer, not from the block model, so borrowing the model alone leaves
    // the gate frozen. Both entries below reuse Create's own, which are typed
    // against the base classes ours extend. Mirrors AllBlockEntityTypes.
    public static final BlockEntityEntry<CustomsGateBlockEntity> CUSTOMS_GATE = registrate()
        .blockEntity("customs_gate", CustomsGateBlockEntity::new)
        .visual(() -> PackagerVisual::new, true)
        .validBlocks(CnnBlocks.CUSTOMS_GATE)
        .renderer(() -> PackagerRenderer::new)
        .register();

    public static final BlockEntityEntry<CustomsLinkBlockEntity> CUSTOMS_LINK = registrate()
        .blockEntity("customs_link", CustomsLinkBlockEntity::new)
        .validBlocks(CnnBlocks.CUSTOMS_LINK)
        .renderer(() -> LinkBulbRenderer::new)
        .register();

    public static void register() {}
}
