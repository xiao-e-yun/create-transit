package me.xiaoeyun.createnestnetwork.content.proxy;

import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;

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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.HitResult;

/**
 * The proxyer has no item form. It is reached only by converting a tuned Stock
 * Ticker (see {@link StockProxyerConversion}) and it impersonates one in every
 * player-visible item context — picked, dropped or queried, it hands back a
 * vanilla Stock Ticker — so the conversion reads as a reversible state of that
 * block rather than as a separate thing to craft and carry.
 */
public class StockProxyerBlock extends WrenchableDirectionalBlock implements IBE<StockProxyerBlockEntity> {

    public StockProxyerBlock(Properties properties) {
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

    /** Sneak-wrenching reverts to the Stock Ticker instead of breaking. */
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof StockProxyerBlockEntity))
            return super.onSneakWrenched(state, context);
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
