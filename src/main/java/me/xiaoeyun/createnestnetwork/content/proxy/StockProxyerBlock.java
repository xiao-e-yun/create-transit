package me.xiaoeyun.createnestnetwork.content.proxy;

import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import me.xiaoeyun.createnestnetwork.registry.CnnBlockEntities;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;

/**
 * The proxyer has no item form. It is reached only by converting a tuned Stock
 * Ticker (see {@link StockProxyerConversion}) and it impersonates one in every
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
public class StockProxyerBlock extends HorizontalDirectionalBlock implements IBE<StockProxyerBlockEntity>, IWrenchable {

    public StockProxyerBlock(Properties properties) {
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

    /** Sneak-wrenching reverts to the Stock Ticker instead of breaking. */
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof StockProxyerBlockEntity))
            return IWrenchable.super.onSneakWrenched(state, context);
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        StockProxyerConversion.toTicker(level, pos, context.getPlayer());
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
    public Class<StockProxyerBlockEntity> getBlockEntityClass() {
        return StockProxyerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends StockProxyerBlockEntity> getBlockEntityType() {
        return CnnBlockEntities.STOCK_PROXYER.get();
    }
}
