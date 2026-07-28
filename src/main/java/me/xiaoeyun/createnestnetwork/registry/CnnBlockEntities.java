package me.xiaoeyun.createnestnetwork.registry;

import static me.xiaoeyun.createnestnetwork.CreateNestNetwork.registrate;

import com.tterrag.registrate.util.entry.BlockEntityEntry;

import me.xiaoeyun.createnestnetwork.content.customs.CustomsLinkBlockEntity;
import me.xiaoeyun.createnestnetwork.content.proxy.StockProxyerBlockEntity;

public class CnnBlockEntities {

    public static final BlockEntityEntry<StockProxyerBlockEntity> STOCK_PROXYER = registrate()
        .blockEntity("stock_proxyer", StockProxyerBlockEntity::new)
        .validBlocks(CnnBlocks.STOCK_PROXYER)
        .register();

    public static final BlockEntityEntry<CustomsLinkBlockEntity> CUSTOMS_LINK = registrate()
        .blockEntity("customs_link", CustomsLinkBlockEntity::new)
        .validBlocks(CnnBlocks.CUSTOMS_LINK)
        .register();

    public static void register() {}
}
