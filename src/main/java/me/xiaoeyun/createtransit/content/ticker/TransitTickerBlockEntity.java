package me.xiaoeyun.createtransit.content.ticker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.packager.InventoryIdentifier;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Iterate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Transit Ticker: a packager-shaped block that vanilla Stock Links can attach
 * to. Instead of exposing a physical inventory, it delegates to a bound child
 * logistics network, mounting the child's stock onto the parent network.
 *
 * Design constraints (see README):
 * - Upstream-only: the child network cannot browse or request from the parent.
 * - Orders are forwarded synchronously into the child network with the parent
 *   requester's address kept intact (pure address forwarding).
 * - Proxy cycles are legal but inert: re-entrant summary/dispatch calls are
 *   cut off by a thread-local visited set and contribute an empty summary.
 * - No tick-driven logic.
 *
 * The child binding lives in a non-global {@link LogisticallyLinkedBehaviour},
 * making the proxyer a passive member of the child network: it contributes no
 * stock there (the behaviour's summary hook only serves PackagerLink block
 * entities) but participates in the vanilla tuning-item highlight both ways.
 */
public class TransitTickerBlockEntity extends PackagerBlockEntity implements IHaveGoggleInformation {

    /** Frequencies currently being aggregated on this thread; cycles contribute empty summaries. */
    private static final ThreadLocal<Set<UUID>> VISITED_NETWORKS = ThreadLocal.withInitial(HashSet::new);

    public LogisticallyLinkedBehaviour childLink;

    private boolean cycleDetected;

    public TransitTickerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // The proxy never exposes a physical inventory; keep the inherited
        // behaviour inert so parent links see no InventoryIdentifier for us.
        targetInventory.withFilter($ -> false);
        behaviours.add(childLink = new LogisticallyLinkedBehaviour(this, false));
    }

    public UUID getChildFrequency() {
        return childLink.freqId;
    }

    public void setChildFrequency(UUID freqId) {
        LogisticallyLinkedBehaviour.remove(childLink);
        childLink.freqId = freqId;
        LogisticallyLinkedBehaviour.keepAlive(childLink);
        notifyUpdate();
        refreshCycleDetected();
    }

    // Summary delegation

    @Override
    public InventorySummary getAvailableItems(boolean scanInputSlots) {
        if (level == null || level.isClientSide)
            return InventorySummary.EMPTY;
        UUID childFreqId = childLink.freqId;

        Set<UUID> visited = VISITED_NETWORKS.get();
        Set<UUID> parentFreqs = collectAdjacentParentFrequencies();
        if (visited.contains(childFreqId) || parentFreqs.contains(childFreqId))
            return InventorySummary.EMPTY;

        Set<UUID> added = enterNetworks(visited, childFreqId, parentFreqs);
        InventorySummary summary;
        try {
            summary = LogisticsManager.getSummaryOfNetwork(childFreqId, true)
                .copy();
            subtractInventoriesSharedWithParents(summary, childFreqId, parentFreqs);
        } finally {
            visited.removeAll(added);
        }
        return summary;
    }

    // Order forwarding

    @Override
    public void attemptToSend(List<PackagingRequest> queuedRequests) {
        // Redstone/manual packing paths pass null; the proxy has nothing to pack.
        if (queuedRequests == null || queuedRequests.isEmpty())
            return;
        try {
            if (level == null || level.isClientSide)
                return;
            UUID childFreqId = childLink.freqId;

            Set<UUID> visited = VISITED_NETWORKS.get();
            Set<UUID> parentFreqs = collectAdjacentParentFrequencies();
            if (visited.contains(childFreqId) || parentFreqs.contains(childFreqId))
                return;

            String address = queuedRequests.get(0)
                .address();
            List<BigItemStack> orderStacks = new ArrayList<>();
            for (PackagingRequest request : queuedRequests)
                if (!request.item()
                    .isEmpty() && request.getCount() > 0)
                    orderStacks.add(new BigItemStack(request.item()
                        .copy(), request.getCount()));
            if (orderStacks.isEmpty())
                return;

            Set<UUID> added = enterNetworks(visited, childFreqId, parentFreqs);
            try {
                // Two-phase ordering: clamp against the child's live stock
                // before committing to the synchronous broadcast.
                InventorySummary liveStock = LogisticsManager.getSummaryOfNetwork(childFreqId, true);
                List<BigItemStack> feasible = new ArrayList<>();
                for (BigItemStack entry : orderStacks) {
                    int count = Math.min(entry.count, liveStock.getCountOf(entry.stack));
                    if (count > 0)
                        feasible.add(new BigItemStack(entry.stack, count));
                }
                if (feasible.isEmpty())
                    return;

                LogisticsManager.broadcastPackageRequest(childFreqId, RequestType.REDSTONE,
                    PackageOrderWithCrafts.simple(feasible), null, address);
            } finally {
                visited.removeAll(added);
            }
        } finally {
            queuedRequests.clear();
        }
    }

    @Override
    public boolean unwrapBox(ItemStack box, boolean simulate) {
        return false;
    }

    // Cycle guard helpers

    private static Set<UUID> enterNetworks(Set<UUID> visited, UUID childFreqId, Set<UUID> parentFreqs) {
        Set<UUID> added = new HashSet<>(parentFreqs);
        added.add(childFreqId);
        added.removeAll(visited);
        visited.addAll(added);
        return added;
    }

    private Set<UUID> collectAdjacentParentFrequencies() {
        Set<UUID> result = new HashSet<>();
        for (Direction d : Iterate.directions) {
            BlockPos pos = worldPosition.relative(d);
            if (!level.isLoaded(pos))
                continue;
            BlockState adjacentState = level.getBlockState(pos);
            // Any link in the PackagerLink family counts, not just the vanilla
            // Stock Link: a Transit Link attached here is just as much a parent,
            // and missing it would silently disable the re-entrancy guard and
            // the duplicate-count subtraction below.
            if (!(adjacentState.getBlock() instanceof PackagerLinkBlock))
                continue;
            if (PackagerLinkBlock.getConnectedDirection(adjacentState) != d)
                continue;
            if (level.getBlockEntity(pos) instanceof PackagerLinkBlockEntity plbe && plbe.behaviour != null
                && plbe.behaviour.redstonePower != 15)
                result.add(plbe.behaviour.freqId);
        }
        return result;
    }

    // Cycle warning: recomputed from the proxy graph whenever the local
    // topology may have changed. recheckIfLinksPresent is invoked by vanilla
    // on our initialize, on every lazyTick, and by any adjacent Stock Link
    // when it initializes, so placing/removing links updates the warning
    // without anyone having to query the network. The walk only touches the
    // in-memory link registry (no inventory scans).

    @Override
    public void recheckIfLinksPresent() {
        super.recheckIfLinksPresent();
        if (level == null || level.isClientSide())
            return;
        refreshCycleDetected();
    }

    private void refreshCycleDetected() {
        boolean detected = computeCycleDetected();
        if (detected != cycleDetected) {
            cycleDetected = detected;
            notifyUpdate();
        }
    }

    /**
     * Walks downward from the bound child network through every loaded
     * proxyer; a cycle through this proxyer exists iff the walk reaches a
     * network one of our attached parent links belongs to. Advisory only —
     * the thread-local guard remains the actual correctness mechanism.
     */
    private boolean computeCycleDetected() {
        Set<UUID> parentFreqs = collectAdjacentParentFrequencies();
        if (parentFreqs.isEmpty())
            return false;

        Set<UUID> seen = new HashSet<>();
        Deque<UUID> pending = new ArrayDeque<>();
        pending.push(childLink.freqId);

        while (!pending.isEmpty()) {
            UUID freq = pending.pop();
            if (!seen.add(freq))
                continue;
            if (parentFreqs.contains(freq))
                return true;
            for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(freq, false))
                if (link.blockEntity instanceof PackagerLinkBlockEntity plbe
                    && plbe.getPackager() instanceof TransitTickerBlockEntity proxy)
                    pending.push(proxy.childLink.freqId);
        }
        return false;
    }

    // Duplicate-count protection: inventories visible to both the parent
    // network (directly) and the child network (through us) only count once.

    private void subtractInventoriesSharedWithParents(InventorySummary summary, UUID childFreqId,
        Set<UUID> parentFreqs) {
        if (parentFreqs.isEmpty())
            return;

        Set<InventoryIdentifier> parentInventories = new HashSet<>();
        for (UUID parentFreq : parentFreqs)
            for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(parentFreq, false)) {
                InventoryIdentifier identifier = inventoryIdentifierOf(link);
                if (identifier != null)
                    parentInventories.add(identifier);
            }
        if (parentInventories.isEmpty())
            return;

        Set<InventoryIdentifier> subtracted = new HashSet<>();
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(childFreqId, false)) {
            InventoryIdentifier identifier = inventoryIdentifierOf(link);
            if (identifier == null || !parentInventories.contains(identifier))
                continue;
            // The child-side aggregation already de-duplicated per identifier,
            // so subtract each shared inventory at most once.
            if (!subtracted.add(identifier))
                continue;
            for (BigItemStack entry : link.getSummary(null)
                .getStacks())
                subtractClamped(summary, entry.stack, entry.count);
        }
    }

    @Nullable
    private static InventoryIdentifier inventoryIdentifierOf(LogisticallyLinkedBehaviour link) {
        if (!(link.blockEntity instanceof PackagerLinkBlockEntity plbe))
            return null;
        PackagerBlockEntity packager = plbe.getPackager();
        if (packager == null || !packager.targetInventory.hasInventory())
            return null;
        IdentifiedInventory identified = packager.targetInventory.getIdentifiedInventory();
        return identified != null ? identified.identifier() : null;
    }

    private static void subtractClamped(InventorySummary summary, ItemStack stack, int count) {
        int present = summary.getCountOf(stack);
        int toRemove = Math.min(present, count);
        if (toRemove <= 0)
            return;
        if (toRemove == present)
            summary.erase(stack);
        else
            summary.add(stack, -toRemove);
    }

    // Serialization

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        // Legacy saves stored the binding outside the behaviour
        if (tag.hasUUID("ChildFreq"))
            childLink.freqId = tag.getUUID("ChildFreq");
        cycleDetected = tag.getBoolean("CycleDetected");
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putBoolean("CycleDetected", cycleDetected);
    }

    // Goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
            .append(Component.translatable("block.create_transit.transit_ticker")
                .withStyle(ChatFormatting.WHITE)));

        int loadedComponents = 0;
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(childLink.freqId, false,
            true))
            if (link != childLink)
                loadedComponents++;

        if (loadedComponents > 0)
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_ticker.goggles.connected",
                    loadedComponents)
                    .withStyle(ChatFormatting.GREEN)));
        else
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_ticker.goggles.disconnected")
                    .withStyle(ChatFormatting.GRAY)));

        if (cycleDetected)
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_ticker.goggles.cycle")
                    .withStyle(ChatFormatting.RED)));
        return true;
    }

}
