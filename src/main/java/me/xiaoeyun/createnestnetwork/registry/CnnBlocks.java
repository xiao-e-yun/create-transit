package me.xiaoeyun.createnestnetwork.registry;

import static me.xiaoeyun.createnestnetwork.CreateNestNetwork.registrate;

import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;

import me.xiaoeyun.createnestnetwork.content.proxy.StockProxyerBlock;
import net.minecraft.world.level.material.MapColor;

public class CnnBlocks {

    public static final BlockEntry<StockProxyerBlock> STOCK_PROXYER = registrate()
        .block("stock_proxyer", StockProxyerBlock::new)
        .initialProperties(SharedProperties::stone)
        .properties(p -> p.mapColor(MapColor.TERRACOTTA_BROWN))
        .simpleItem()
        .register();

    public static void register() {}
}
