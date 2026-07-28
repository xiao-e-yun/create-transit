package me.xiaoeyun.createnestnetwork.content.proxy;

import com.simibubi.create.foundation.block.IBE;

import me.xiaoeyun.createnestnetwork.registry.CnnBlockEntities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class StockProxyerBlock extends Block implements IBE<StockProxyerBlockEntity> {

    public StockProxyerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<StockProxyerBlockEntity> getBlockEntityClass() {
        return StockProxyerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StockProxyerBlockEntity> getBlockEntityType() {
        return CnnBlockEntities.STOCK_PROXYER.get();
    }
}
