package me.xiaoeyun.createnestnetwork.content.proxy;

import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import me.xiaoeyun.createnestnetwork.registry.CnnBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Stock Proxyer has no item of its own: it is only ever reached by
 * converting a tuned vanilla Stock Ticker in place, and a sneaking wrench turns
 * it back. Both directions carry the frequency across, which is what makes the
 * ticker's ordinary tuning UX double as the proxyer's child-network binding.
 *
 * Replacement is a plain {@code setBlockAndUpdate}. That routes through
 * {@code IBE.onRemove}, which calls {@code destroy()} on the outgoing block
 * entity for us — so the ticker's received payments and category filters drop
 * exactly as if it had been broken, and nothing may call {@code destroy()}
 * again here without duplicating those items.
 */
public final class StockProxyerConversion {

    private StockProxyerConversion() {}

    /** Turns a tuned Stock Ticker into a proxyer bound to the same network. */
    public static boolean toProxyer(Level level, BlockPos pos, @Nullable Player player) {
        if (level.isClientSide)
            return false;
        if (!(level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker))
            return false;

        // Converting rebinds someone's network; require the same permission
        // vanilla asks for before touching the ticker at all.
        if (player != null && !ticker.behaviour.mayAdministrate(player)) {
            player.displayClientMessage(
                Component.translatable("create_nest_network.stock_proxyer.conversion_denied"), true);
            return false;
        }

        UUID frequency = ticker.behaviour.freqId;
        Direction facing = level.getBlockState(pos)
            .getOptionalValue(HorizontalDirectionalBlock.FACING)
            .orElse(Direction.NORTH);

        level.setBlockAndUpdate(pos, CnnBlocks.STOCK_PROXYER.getDefaultState()
            .setValue(DirectionalBlock.FACING, facing));

        if (level.getBlockEntity(pos) instanceof StockProxyerBlockEntity proxyer)
            proxyer.setChildFrequency(frequency);

        if (player != null)
            player.displayClientMessage(Component.translatable("create_nest_network.stock_proxyer.converted"), true);
        return true;
    }

    /** Restores a vanilla Stock Ticker, carrying the bound network back with it. */
    public static boolean toTicker(Level level, BlockPos pos, @Nullable Player player) {
        if (level.isClientSide)
            return false;
        if (!(level.getBlockEntity(pos) instanceof StockProxyerBlockEntity proxyer))
            return false;

        if (player != null && !proxyer.childLink.mayAdministrate(player)) {
            player.displayClientMessage(
                Component.translatable("create_nest_network.stock_proxyer.conversion_denied"), true);
            return false;
        }

        UUID frequency = proxyer.getChildFrequency();
        // A proxyer may face up or down; the ticker only has horizontal facings.
        Direction facing = level.getBlockState(pos)
            .getOptionalValue(DirectionalBlock.FACING)
            .filter(d -> d.getAxis()
                .isHorizontal())
            .orElse(Direction.NORTH);

        level.setBlockAndUpdate(pos, AllBlocks.STOCK_TICKER.getDefaultState()
            .setValue(HorizontalDirectionalBlock.FACING, facing));

        if (level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker) {
            LogisticallyLinkedBehaviour.remove(ticker.behaviour);
            ticker.behaviour.freqId = frequency;
            LogisticallyLinkedBehaviour.keepAlive(ticker.behaviour);
            ticker.notifyUpdate();
        }

        if (player != null)
            player.displayClientMessage(Component.translatable("create_nest_network.stock_proxyer.reverted"), true);
        return true;
    }

}
