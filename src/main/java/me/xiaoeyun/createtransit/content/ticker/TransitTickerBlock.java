package me.xiaoeyun.createtransit.content.ticker;

import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import me.xiaoeyun.createtransit.registry.CtBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The proxyer has no item form. It is reached only by converting a tuned Stock
 * Ticker (see {@link TransitTickerConversion}) and it impersonates one in every
 * player-visible item context — picked, dropped or queried, it hands back a
 * vanilla Stock Ticker — so the conversion reads as a reversible state of that
 * block rather than as a separate thing to craft and carry.
 *
 * Horizontal facing only, matching the Stock Ticker it converts from, reverts
 * to and hands back as an item. The facing is purely cosmetic here — the
 * inherited target inventory is filtered off, so it never selects a container —
 * and allowing a vertical one would only create an orientation the ticker
 * cannot represent, which reverting would then have to silently discard.
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
     * Borrowing the ticker's model means borrowing its shape too. Neighbouring
     * faces are culled against this, not against the model, so leaving it as
     * the default full cube tells the floor to stop drawing its top face while
     * the ticker's inset base leaves that corner uncovered -- a hole around the
     * seam.
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.STOCK_TICKER;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    /**
     * Losing the last attached link reverts the proxyer, mirroring the
     * placement that created it: without one it is a block that impersonates a
     * Stock Ticker while having none of a ticker's behaviour, which is a state
     * no player asked to be left in.
     *
     * This hangs off neighbour updates rather than the inherited link recheck
     * on purpose. The recheck also runs on initialize and on a timer, where a
     * neighbouring link that has not finished loading reads as absent, and
     * reverting on that would rewrite builds every time a chunk loads.
     * Neighbour updates only fire for changes that actually happened.
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
        boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide || isMoving)
            return;
        // Replacing ourselves here would mean mutating the world from inside
        // the update cascade that is still running -- an explosion clearing the
        // link would have us rewriting blocks midway through its own removal
        // loop. A scheduled tick lands after that has settled.
        level.scheduleTick(pos, this, 1);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (!TransitTickerConversion.hasAttachedLink(level, pos))
            TransitTickerConversion.toTicker(level, pos, null);
    }

    /** Sneak-wrenching reverts to the Stock Ticker instead of breaking. */
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof TransitTickerBlockEntity))
            return IWrenchable.super.onSneakWrenched(state, context);
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        TransitTickerConversion.toTicker(level, pos, context.getPlayer());
        return InteractionResult.SUCCESS;
    }

    // Item identity: always a Stock Ticker

    @Override
    public Item asItem() {
        return AllBlocks.STOCK_TICKER.asItem();
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos,
        Player player) {
        return AllBlocks.STOCK_TICKER.asStack();
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(AllBlocks.STOCK_TICKER.asStack());
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
