package me.xiaoeyun.createtransit.content.transit;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;

import me.xiaoeyun.createtransit.client.TransitLinkScreen;
import me.xiaoeyun.createtransit.registry.CtBlockEntities;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The block half of the transit link. Everything structural — placement, face
 * attachment, redstone, waterlogging, wrenching — comes from the vanilla Stock
 * Link; the only addition is right-clicking to edit the transit label.
 *
 * Note this cannot re-declare {@code IBE<TransitLinkBlockEntity>}: the vanilla
 * superclass already binds the interface to PackagerLinkBlockEntity, so the
 * block entity type is narrowed through the overrides below instead.
 */
public class TransitLinkBlock extends PackagerLinkBlock {

    public TransitLinkBlock(Properties properties) {
        super(properties);
    }

    /**
     * 1.21 splits held-item clicks out of {@code use}: SKIP hands the wrench
     * and the tuning item straight to their own {@code useOn}, everything else
     * falls through to {@link #useWithoutItem} exactly as an empty hand does.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
        Player player, InteractionHand hand, BlockHitResult hit) {
        if (AllItems.WRENCH.isIn(stack))
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        // Link items tune themselves against this block's behaviour instead
        if (stack.getItem() instanceof LogisticallyLinkedBlockItem)
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
        BlockHitResult hit) {
        if (player == null)
            return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof TransitLinkBlockEntity link))
            return InteractionResult.PASS;
        if (!link.behaviour.mayInteractMessage(player))
            return InteractionResult.SUCCESS;

        CatnipServices.PLATFORM.executeOnClientOnly(() -> () -> TransitLinkScreen.open(link));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<PackagerLinkBlockEntity> getBlockEntityClass() {
        return PackagerLinkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PackagerLinkBlockEntity> getBlockEntityType() {
        return CtBlockEntities.TRANSIT_LINK.get();
    }

}
