package me.xiaoeyun.createtransit.content.ticker;

import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;

import me.xiaoeyun.createtransit.registry.CtBlocks;
import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraftforge.event.level.BlockEvent;

/**
 * The Transit Ticker has no item of its own: it is only ever reached by
 * attaching a logistics link to a tuned vanilla Stock Ticker, which converts it
 * in place, and a sneaking wrench turns it back. Both directions carry the
 * frequency across, which is what makes the ticker's ordinary tuning UX double
 * as the proxyer's child-network binding.
 *
 * Any link in the PackagerLink family triggers it, the vanilla Stock Link
 * included. That combination is inert in the base game -- a stock ticker's
 * block entity is not a PackagerBlockEntity, so the link never resolves a
 * packager behind it -- so giving it a meaning takes nothing away, and the
 * in-game tutorial points tickers at a creature or a blaze burner rather than
 * at a link, so nobody arrives here by following it.
 *
 * Replacement is a plain {@code setBlockAndUpdate}. That routes through
 * {@code IBE.onRemove}, which calls {@code destroy()} on the outgoing block
 * entity for us — so the ticker's received payments and category filters drop
 * exactly as if it had been broken, and nothing may call {@code destroy()}
 * again here without duplicating those items.
 */
public final class TransitTickerConversion {

    private TransitTickerConversion() {}

    /**
     * Fires the conversion when a link is attached to a ticker. This hangs off
     * the Forge place event rather than an override on our own link block,
     * because the vanilla Stock Link has to trigger it too and the block Create
     * registers cannot be subclassed after the fact.
     */
    public static void onLinkPlaced(BlockEvent.EntityPlaceEvent event) {
        BlockState placed = event.getPlacedBlock();
        if (!(placed.getBlock() instanceof PackagerLinkBlock))
            return;
        if (!(event.getLevel() instanceof Level level) || level.isClientSide)
            return;

        BlockPos attachedTo = event.getPos()
            .relative(connectedDirection(placed).getOpposite());
        toProxyer(level, attachedTo, event.getEntity() instanceof Player player ? player : null);
    }

    /**
     * Whether any logistics link is currently mounted against this block.
     *
     * Deliberately answered from block states alone. The caller reverts a
     * proxyer when this comes back false, so a transient false -- which a block
     * entity lookup could return while neighbouring chunks are still waking up
     * -- would silently undo someone's build.
     */
    public static boolean hasAttachedLink(BlockGetter level, BlockPos pos) {
        for (Direction d : Iterate.directions) {
            BlockState neighbour = level.getBlockState(pos.relative(d));
            if (!(neighbour.getBlock() instanceof PackagerLinkBlock))
                continue;
            // A link points away from what it is mounted on, so one sitting to
            // our d side is ours exactly when it points back along d.
            if (connectedDirection(neighbour) == d)
                return true;
        }
        return false;
    }

    /**
     * FaceAttachedHorizontalDirectionalBlock keeps its own accessor protected,
     * so resolve the attachment exactly the way it does.
     */
    private static Direction connectedDirection(BlockState state) {
        AttachFace face = state.getValue(FaceAttachedHorizontalDirectionalBlock.FACE);
        if (face == AttachFace.CEILING)
            return Direction.DOWN;
        if (face == AttachFace.FLOOR)
            return Direction.UP;
        return state.getValue(HorizontalDirectionalBlock.FACING);
    }

    /** Turns a tuned Stock Ticker into a proxyer bound to the same network. */
    public static boolean toProxyer(Level level, BlockPos pos, @Nullable Player player) {
        if (level.isClientSide)
            return false;
        if (!(level.getBlockEntity(pos) instanceof StockTickerBlockEntity ticker))
            return false;

        // Converting rebinds someone's network and destroys the ticker, so it
        // needs the permission vanilla asks for. An unattributable placement is
        // refused rather than waved through: there is nobody to check.
        if (player == null)
            return false;
        if (!ticker.behaviour.mayAdministrate(player)) {
            player.displayClientMessage(
                Component.translatable("create_transit.transit_ticker.conversion_denied"), true);
            return false;
        }

        UUID frequency = ticker.behaviour.freqId;
        Direction facing = level.getBlockState(pos)
            .getOptionalValue(HorizontalDirectionalBlock.FACING)
            .orElse(Direction.NORTH);

        level.setBlockAndUpdate(pos, CtBlocks.TRANSIT_TICKER.getDefaultState()
            .setValue(HorizontalDirectionalBlock.FACING, facing));

        if (level.getBlockEntity(pos) instanceof TransitTickerBlockEntity proxyer)
            proxyer.setChildFrequency(frequency);

        player.displayClientMessage(Component.translatable("create_transit.transit_ticker.converted"), true);
        return true;
    }

    /** Restores a vanilla Stock Ticker, carrying the bound network back with it. */
    public static boolean toTicker(Level level, BlockPos pos, @Nullable Player player) {
        if (level.isClientSide)
            return false;
        if (!(level.getBlockEntity(pos) instanceof TransitTickerBlockEntity proxyer))
            return false;

        if (player != null && !proxyer.childLink.mayAdministrate(player)) {
            player.displayClientMessage(
                Component.translatable("create_transit.transit_ticker.conversion_denied"), true);
            return false;
        }

        UUID frequency = proxyer.getChildFrequency();
        Direction facing = level.getBlockState(pos)
            .getOptionalValue(HorizontalDirectionalBlock.FACING)
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
            player.displayClientMessage(Component.translatable("create_transit.transit_ticker.reverted"), true);
        return true;
    }

}
