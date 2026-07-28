package me.xiaoeyun.createnestnetwork.registry;

import static me.xiaoeyun.createnestnetwork.CreateNestNetwork.registrate;

import com.tterrag.registrate.util.entry.BlockEntityEntry;

import me.xiaoeyun.createnestnetwork.content.proxy.StockProxyerBlockEntity;

public class CnnBlockEntities {

    public static final BlockEntityEntry<StockProxyerBlockEntity> STOCK_PROXYER = registrate()
        .blockEntity("stock_proxyer", StockProxyerBlockEntity::new)
        .validBlocks(CnnBlocks.STOCK_PROXYER)
        .register();

    public static void register() {}
}
