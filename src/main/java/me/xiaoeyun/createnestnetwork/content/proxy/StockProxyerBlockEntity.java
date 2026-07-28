package me.xiaoeyun.createnestnetwork.content.proxy;

import java.util.List;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stock Proxyer: registers as a virtual stock link on a parent logistics
 * network, exposing the child network's inventory summary upstream.
 *
 * Design constraints (see README):
 * - Upstream-only: the child network cannot browse or request from the parent.
 * - Promise chain: requests promised to this proxy are forwarded as requests
 *   into the child network, with a local in-flight table to avoid double-sends.
 * - Recursion: any proxy cycle disables every proxy in the cycle.
 * - Chunk loading is explicitly out of scope; unloaded child networks are
 *   simply invisible.
 */
public class StockProxyerBlockEntity extends SmartBlockEntity {

    public StockProxyerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // TODO: bridge the child network's InventorySummary into the parent
        // network via LogisticsManager (see Create's PackagerLinkBlockEntity).
    }
}
