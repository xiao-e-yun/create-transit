package me.xiaoeyun.createtransit.content.transit;

import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;

import me.xiaoeyun.createtransit.registry.CtBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;

public class TransitGateBlock extends WrenchableDirectionalBlock implements IBE<TransitGateBlockEntity> {

    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public TransitGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(POWERED));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null)
            return null;
        return state.setValue(FACING, context.getNearestLookingDirection()
            .getOpposite())
            .setValue(POWERED, context.getLevel()
                .hasNeighborSignal(context.getClickedPos()));
    }

    /**
     * Sending is a rising edge, exactly as on the Packager this borrows its
     * model and its shape from.
     *
     * The state property is what remembers the edge. The block entity has a
     * {@code redstonePowered} field for it, but nothing writes that field to
     * NBT, so a gate left powered across a chunk unload would come back
     * believing it had never fired and send again on the first neighbour
     * update.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
        boolean isMoving) {
        boolean previouslyPowered = state.getValue(POWERED);
        if (previouslyPowered == level.hasNeighborSignal(pos))
            return;
        level.setBlock(pos, state.cycle(POWERED), Block.UPDATE_CLIENTS);
        if (!previouslyPowered)
            withBlockEntityDo(level, pos, TransitGateBlockEntity::activate);
    }

    @Override
    public boolean shouldCheckWeakPower(BlockState state, SignalGetter level, BlockPos pos, Direction side) {
        return false;
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
