package me.xiaoeyun.createnestnetwork.content.customs;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;

import me.xiaoeyun.createnestnetwork.registry.CnnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * The block half of the customs link. Everything structural — placement, face
 * attachment, redstone, waterlogging, wrenching — comes from the vanilla Stock
 * Link; the only addition is right-clicking to edit the transit label.
 *
 * Converting an attached Stock Ticker into a proxyer is deliberately not done
 * here: the vanilla Stock Link has to do it too, so it lives on a place event
 * in {@link me.xiaoeyun.createnestnetwork.content.proxy.StockProxyerConversion}
 * that covers the whole link family at once.
 *
 * Note this cannot re-declare {@code IBE<CustomsLinkBlockEntity>}: the vanilla
 * superclass already binds the interface to PackagerLinkBlockEntity, so the
 * block entity type is narrowed through the overrides below instead.
 */
public class CustomsLinkBlock extends PackagerLinkBlock {

    public CustomsLinkBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
        BlockHitResult hit) {
        if (player == null)
            return InteractionResult.PASS;

        ItemStack itemInHand = player.getItemInHand(hand);
        if (AllItems.WRENCH.isIn(itemInHand))
            return InteractionResult.PASS;
        // Link items tune themselves against this block's behaviour instead
        if (itemInHand.getItem() instanceof LogisticallyLinkedBlockItem)
            return InteractionResult.PASS;

        if (!(level.getBlockEntity(pos) instanceof CustomsLinkBlockEntity link))
            return InteractionResult.PASS;
        if (!link.behaviour.mayInteractMessage(player))
            return InteractionResult.SUCCESS;

        if (level.isClientSide)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> CustomsLinkScreen.open(link));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public Class<PackagerLinkBlockEntity> getBlockEntityClass() {
        return PackagerLinkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends PackagerLinkBlockEntity> getBlockEntityType() {
        return CnnBlockEntities.CUSTOMS_LINK.get();
    }

}
