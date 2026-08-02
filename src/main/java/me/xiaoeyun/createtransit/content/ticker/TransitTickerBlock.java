package me.xiaoeyun.createtransit.content.ticker;

import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import me.xiaoeyun.createtransit.registry.CtBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An ordinary craftable block, mirroring the Stock Ticker it is shaped after:
 * its item is a {@code LogisticallyLinkedBlockItem}, so tuning it to a network
 * before placement is what binds the child network it mounts.
 *
 * Horizontal facing only, as the Stock Ticker is. The facing is purely
 * cosmetic — the inherited target inventory is filtered off, so it never
 * selects a container.
 */
public class TransitTickerBlock extends HorizontalDirectionalBlock implements IBE<TransitTickerBlockEntity>, IWrenchable {

    public TransitTickerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection()
            .getOpposite());
    }

    /**
     * Borrowing the ticker's model means borrowing its shape too: neighbouring
     * faces are culled against this, so a default full cube would leave a hole
     * around the inset base.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.STOCK_TICKER;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<TransitTickerBlockEntity> getBlockEntityClass() {
        return TransitTickerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TransitTickerBlockEntity> getBlockEntityType() {
        return CtBlockEntities.TRANSIT_TICKER.get();
    }
}
