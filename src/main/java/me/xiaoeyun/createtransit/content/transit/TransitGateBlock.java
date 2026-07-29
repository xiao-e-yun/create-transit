package me.xiaoeyun.createtransit.content.transit;

import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;

import me.xiaoeyun.createtransit.registry.CtBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class TransitGateBlock extends WrenchableDirectionalBlock implements IBE<TransitGateBlockEntity> {

    public TransitGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null)
            return null;
        return state.setValue(FACING, context.getNearestLookingDirection()
            .getOpposite());
    }

    /** As with the Packager this borrows its model from, mobs do not path through. */
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<TransitGateBlockEntity> getBlockEntityClass() {
        return TransitGateBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TransitGateBlockEntity> getBlockEntityType() {
        return CtBlockEntities.TRANSIT_GATE.get();
    }

}
