package me.xiaoeyun.createnestnetwork.registry;

import static me.xiaoeyun.createnestnetwork.CreateNestNetwork.registrate;

import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;

import me.xiaoeyun.createnestnetwork.content.proxy.StockProxyerBlock;
import net.minecraft.world.level.material.MapColor;

public class CnnBlocks {

    // No item: a proxyer only ever exists by converting a tuned Stock Ticker,
    // and it impersonates one whenever an item form is asked for.
    public static final BlockEntry<StockProxyerBlock> STOCK_PROXYER = registrate()
        .block("stock_proxyer", StockProxyerBlock::new)
        .initialProperties(SharedProperties::stone)
        .properties(p -> p.mapColor(MapColor.TERRACOTTA_BROWN))
        .register();

    public static void register() {}
}
