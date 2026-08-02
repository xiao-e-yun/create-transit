package me.xiaoeyun.createtransit.content.ticker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.Multimap;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts.CraftingEntry;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import me.xiaoeyun.createtransit.content.transit.TransitCustoms;
import me.xiaoeyun.createtransit.content.transit.TransitLinkBlockEntity;
import net.createmod.catnip.data.Iterate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Transit Ticker: a packager-shaped block that vanilla Stock Links attach to,
 * mounting a bound child logistics network onto the parent network in one of
 * two modes decided by what is mounted.
 *
 * With a vanilla Stock Link mounted the child's links are re-registered under
 * the parent frequency as shadow entries, so vanilla sees them as ordinary
 * parent members and the ticker itself reports no stock and takes no
 * assignments. With a Transit Link mounted, a request whose head label names
 * that link is foreign traffic: the ticker reports the child's aggregate stock,
 * forwards the request into the child network as an order of the child's own,
 * and a {@link TransitCustoms} declaration rides along telling the far-side
 * transit gate which parent order to re-stamp the arriving boxes for.
 *
 * Membership is one-way — the child network can neither browse nor request from
 * the parent — the mounting point never stores ({@link #unwrapBox} refuses
 * everything, so goods must travel physically), and proxy cycles are legal but
 * inert: shadowing skips links already living in the target network and
 * re-entrant summary/dispatch calls are cut off by a thread-local visited set.
 */
public class TransitTickerBlockEntity extends PackagerBlockEntity implements IHaveGoggleInformation {

    /** Frequencies currently being aggregated on this thread; cycles contribute empty summaries. */
    private static final ThreadLocal<Set<UUID>> VISITED_NETWORKS = ThreadLocal.withInitial(HashSet::new);

    public LogisticallyLinkedBehaviour childLink;

    /** Live shadow registrations, one per (mount frequency, underlying child link). */
    private Map<UUID, Map<PackagerLinkBlockEntity, LogisticallyLinkedBehaviour>> shadowLinks = new HashMap<>();

    /** Parent frequencies the child network loops back around to. */
    private Set<UUID> cyclingFrequencies = Set.of();

    /** Whether the child network holds anything besides our own binding. */
    private boolean childConnected;

    /** Whether a Stock Link and a labelled Transit Link are mounted at once. */
    private boolean mountConflicted;

    public TransitTickerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // The ticker has no physical inventory of its own; keep the inherited
        // behaviour inert so parent links see no InventoryIdentifier for us.
        targetInventory.withFilter($ -> false);
        behaviours.add(childLink = new LogisticallyLinkedBehaviour(this, false));
    }

    public UUID getChildFrequency() {
        return childLink.freqId;
    }

    // Summary delegation

    @Override
    public InventorySummary getAvailableItems(boolean scanInputSlots) {
        if (level == null || level.isClientSide)
            return InventorySummary.EMPTY;
        // Under flattened mounting the child's links report into the parent
        // themselves, so a summary here would count the warehouse twice.
        if (!collectAdjacentMountFrequencies().isEmpty())
            return InventorySummary.EMPTY;
        // A disabled Transit Link declares no border, and the sending path
        // honours that; showing stock a query can never order would lie.
        if (collectAdjacentTransitLabels().isEmpty())
            return InventorySummary.EMPTY;

        UUID childFreqId = childLink.freqId;
        Set<UUID> visited = VISITED_NETWORKS.get();
        Set<UUID> parentFreqs = collectAdjacentParentFrequencies();
        if (visited.contains(childFreqId) || parentFreqs.contains(childFreqId))
            return InventorySummary.EMPTY;

        Set<UUID> added = enterNetworks(visited, childFreqId, parentFreqs);
        try {
            // Copied because the cached network summary belongs to the registry.
            return LogisticsManager.getSummaryOfNetwork(childFreqId, true)
                .copy();
        } finally {
            visited.removeAll(added);
        }
    }

    // Order handling

    @Override
    public void attemptToSend(List<PackagingRequest> queuedRequests) {
        // Redstone/manual packing paths pass null; a pulse must not be able
        // to drain the warehouse through the mounting point.
        if (queuedRequests == null || queuedRequests.isEmpty())
            return;
        if (level == null || level.isClientSide)
            return;

        List<PackagingRequest> crossBorder = extractCrossBorderRequests(queuedRequests);
        if (!crossBorder.isEmpty())
            forwardCrossBorder(crossBorder);

        // Under flattened mounting the child's own links take assignments
        // directly, and a disabled Transit Link declares no border, so nothing
        // left here names a crossing this ticker can answer for.
        queuedRequests.clear();
    }

    /**
     * A request is cross-border exactly when its head label names a Transit
     * Link mounted here; any other label belongs to an outer border and travels
     * with the address as cargo rather than as a directive for this hop.
     */
    private List<PackagingRequest> extractCrossBorderRequests(List<PackagingRequest> queuedRequests) {
        Set<String> transitLabels = collectAdjacentTransitLabels();
        if (transitLabels.isEmpty())
            return List.of();
        List<PackagingRequest> crossBorder = new ArrayList<>();
        for (Iterator<PackagingRequest> iterator = queuedRequests.iterator(); iterator.hasNext();) {
            PackagingRequest request = iterator.next();
            String headLabel = AddressLabels.headLabelName(request.address());
            if (headLabel == null || !transitLabels.contains(headLabel))
                continue;
            crossBorder.add(request);
            iterator.remove();
        }
        return crossBorder;
    }

    private void forwardCrossBorder(List<PackagingRequest> crossBorder) {
        UUID childFreqId = childLink.freqId;
        Set<UUID> visited = VISITED_NETWORKS.get();
        Set<UUID> parentFreqs = collectAdjacentParentFrequencies();
        if (visited.contains(childFreqId) || parentFreqs.contains(childFreqId))
            return;

        // The queue can legitimately mix link slots, and each slot must become
        // its own child order with its own declaration.
        Map<Long, List<PackagingRequest>> slots = new LinkedHashMap<>();
        for (PackagingRequest request : crossBorder)
            slots.computeIfAbsent(slotKey(request.orderId(), request.linkIndex()), $ -> new ArrayList<>())
                .add(request);

        Set<UUID> added = enterNetworks(visited, childFreqId, parentFreqs);
        try {
            for (List<PackagingRequest> slot : slots.values())
                forwardSlot(childFreqId, slot);
        } finally {
            visited.removeAll(added);
        }
    }

    /**
     * One parent link slot becomes one whole child order: the broadcast mints a
     * fresh order id, the child network packs and ships under it as if a local
     * player had ordered, and the customs declaration filed here is what lets
     * the far-side gate hand the boxes back to the parent order.
     */
    private void forwardSlot(UUID childFreqId, List<PackagingRequest> requests) {
        PackagingRequest parent = requests.get(0);
        String address = parent.address();
        TransitCustoms declaration = TransitCustoms.of(address, parent.orderId(), parent.linkIndex(), parent.finalLink()
            .booleanValue());
        // An unlabelled request names no border, so no gate would answer to a
        // declaration about it.
        if (declaration == null)
            return;

        List<BigItemStack> orderStacks = new ArrayList<>();
        for (PackagingRequest request : requests)
            if (!request.item()
                .isEmpty() && request.getCount() > 0)
                orderStacks.add(new BigItemStack(request.item()
                    .copy(), request.getCount()));
        if (orderStacks.isEmpty())
            return;

        // Two-phase ordering: clamp against the child's live stock before
        // committing to the synchronous broadcast.
        InventorySummary liveStock = LogisticsManager.getSummaryOfNetwork(childFreqId, true);
        List<BigItemStack> feasible = new ArrayList<>();
        for (BigItemStack entry : orderStacks) {
            int count = Math.min(entry.count, liveStock.getCountOf(entry.stack));
            if (count > 0)
                feasible.add(new BigItemStack(entry.stack, count));
        }
        if (feasible.isEmpty())
            return;

        // The stacks are the child's own, but the crafts are the parent's:
        // vanilla carries them as passenger data on the box context, which is
        // where a destination repackager still finds them.
        List<CraftingEntry> crafts = parent.context() == null ? List.of()
            : parent.context()
                .orderedCrafts();
        PackageOrderWithCrafts childOrder = new PackageOrderWithCrafts(new PackageOrder(feasible), crafts);

        Multimap<PackagerBlockEntity, PackagingRequest> assignment =
            LogisticsManager.findPackagersForRequest(childFreqId, childOrder, null, address);
        if (assignment.isEmpty())
            return;

        // Borders chain here: a ticker forwarding across an inner border is one
        // of the packagers driven by the outer border's broadcast, so the outer
        // filing is still open and its declarations ride onto this order behind
        // ours.
        List<TransitCustoms> declarations = new ArrayList<>();
        declarations.add(declaration);
        declarations.addAll(TransitCustoms.filedFor(parent.orderId()));

        int childOrderId = assignment.values()
            .iterator()
            .next()
            .orderId();
        TransitCustoms.file(childOrderId, declarations);
        try {
            LogisticsManager.performPackageRequests(assignment);
        } finally {
            TransitCustoms.close(childOrderId);
        }
    }

    private static long slotKey(int orderId, int linkIndex) {
        return ((long) orderId << 32) | (linkIndex & 0xFFFFFFFFL);
    }

    // Flattened mounting

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide())
            return;
        refreshShadowLinks();
    }

    /**
     * Re-registers every link of the child network under each mounted parent
     * frequency on the registry's own keepAlive cadence. A shadow is a plain
     * {@link LogisticallyLinkedBehaviour} wrapping the real child link — so
     * summaries and requests delegate to it natively — with the frequency
     * swapped, a link id of its own and the child link's redstone priority
     * mirrored. Nested tickers are enumerated like any other link, so mounting
     * chains flatten transitively, one lazyTick per layer.
     */
    private void refreshShadowLinks() {
        Map<UUID, Map<PackagerLinkBlockEntity, LogisticallyLinkedBehaviour>> refreshed = new HashMap<>();
        UUID childFreqId = childLink.freqId;
        for (UUID mountFreq : collectAdjacentMountFrequencies()) {
            if (mountFreq.equals(childFreqId))
                continue;
            Map<PackagerLinkBlockEntity, LogisticallyLinkedBehaviour> existing =
                shadowLinks.getOrDefault(mountFreq, Map.of());
            Map<PackagerLinkBlockEntity, LogisticallyLinkedBehaviour> current = new HashMap<>();
            for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(childFreqId, false)) {
                if (!(link.blockEntity instanceof PackagerLinkBlockEntity plbe))
                    continue;
                if (plbe.behaviour == null || mountFreq.equals(plbe.behaviour.freqId))
                    continue;
                if (current.containsKey(plbe))
                    continue;
                LogisticallyLinkedBehaviour shadow = existing.get(plbe);
                if (shadow == null) {
                    shadow = new LogisticallyLinkedBehaviour(plbe, false);
                    shadow.freqId = mountFreq;
                }
                shadow.redstonePower = plbe.behaviour.redstonePower;
                LogisticallyLinkedBehaviour.keepAlive(shadow);
                current.put(plbe, shadow);
            }
            refreshed.put(mountFreq, current);
        }

        // A dropped shadow has to be evicted, not merely left unrefreshed: the
        // per-frequency link cache holds its entries for 400 ticks.
        Set<LogisticallyLinkedBehaviour> surviving = new HashSet<>();
        for (Map<PackagerLinkBlockEntity, LogisticallyLinkedBehaviour> mount : refreshed.values())
            surviving.addAll(mount.values());
        for (Map<PackagerLinkBlockEntity, LogisticallyLinkedBehaviour> mount : shadowLinks.values())
            for (LogisticallyLinkedBehaviour shadow : mount.values())
                if (!surviving.contains(shadow))
                    LogisticallyLinkedBehaviour.remove(shadow);
        shadowLinks = refreshed;
    }

    @Override
    public boolean unwrapBox(ItemStack box, boolean simulate) {
        return false;
    }

    // Adjacency

    /** Links attached face-to-face with this ticker, in loaded chunks only. */
    private List<PackagerLinkBlockEntity> adjacentLinks() {
        List<PackagerLinkBlockEntity> result = new ArrayList<>();
        for (Direction d : Iterate.directions) {
            BlockPos pos = worldPosition.relative(d);
            if (!level.isLoaded(pos))
                continue;
            BlockState adjacentState = level.getBlockState(pos);
            if (!(adjacentState.getBlock() instanceof PackagerLinkBlock))
                continue;
            if (PackagerLinkBlock.getConnectedDirection(adjacentState) != d)
                continue;
            if (level.getBlockEntity(pos) instanceof PackagerLinkBlockEntity plbe)
                result.add(plbe);
        }
        return result;
    }

    /** A link silenced by full redstone has left its own network. */
    private static boolean isActive(PackagerLinkBlockEntity link) {
        return link.behaviour != null && link.behaviour.redstonePower != 15;
    }

    /**
     * Frequencies of the vanilla Stock Links mounted here — the networks the
     * child's links are flattened into. Transit Links are never mount points: a
     * border stays opaque, that being the point of one.
     */
    private Set<UUID> collectAdjacentMountFrequencies() {
        Set<UUID> result = new HashSet<>();
        for (PackagerLinkBlockEntity link : adjacentLinks())
            if (!(link instanceof TransitLinkBlockEntity) && isActive(link))
                result.add(link.behaviour.freqId);
        return result;
    }

    /** Every attached network, Transit Links included — the re-entrancy guard's view. */
    private Set<UUID> collectAdjacentParentFrequencies() {
        Set<UUID> result = new HashSet<>();
        for (PackagerLinkBlockEntity link : adjacentLinks())
            if (isActive(link))
                result.add(link.behaviour.freqId);
        return result;
    }

    /** Labels of the Transit Links mounted here; a disabled link declares no border and so contributes none. */
    private Set<String> collectAdjacentTransitLabels() {
        Set<String> result = new HashSet<>();
        for (PackagerLinkBlockEntity link : adjacentLinks())
            if (link instanceof TransitLinkBlockEntity tlbe && tlbe.isActive())
                result.add(tlbe.getLabel());
        return result;
    }

    /**
     * Both link kinds at once: the Stock Link switches the ticker into flattened
     * mounting, which reports no stock, so the Transit Link is starved.
     */
    private boolean computeMountConflicted() {
        boolean mount = false;
        boolean transit = false;
        for (PackagerLinkBlockEntity link : adjacentLinks()) {
            if (link instanceof TransitLinkBlockEntity tlbe)
                transit |= tlbe.isActive();
            else
                mount |= isActive(link);
        }
        return mount && transit;
    }

    // Cycle guard helpers

    private static Set<UUID> enterNetworks(Set<UUID> visited, UUID childFreqId, Set<UUID> parentFreqs) {
        Set<UUID> added = new HashSet<>(parentFreqs);
        added.add(childFreqId);
        added.removeAll(visited);
        visited.addAll(added);
        return added;
    }

    // Network state

    @Override
    public void recheckIfLinksPresent() {
        super.recheckIfLinksPresent();
        if (level == null || level.isClientSide())
            return;
        refreshNetworkState();
    }

    /** Whether any proxy cycle runs through this mounting point. */
    private boolean isCycleDetected() {
        return !cyclingFrequencies.isEmpty();
    }

    /** Whether a cycle runs through the parent network on {@code freq}; a ticker can wear several links at once. */
    public boolean isCycling(UUID freq) {
        return cyclingFrequencies.contains(freq);
    }

    /** Whether the binding reaches anything at all. Answered on the server, since the client link registry only holds loaded chunks. */
    public boolean isChildConnected() {
        return childConnected;
    }

    /** Whether a Stock Link and a labelled Transit Link are mounted at once, which starves the latter. */
    public boolean isMountConflicted() {
        return mountConflicted;
    }

    private void refreshNetworkState() {
        Set<UUID> cycling = computeCyclingFrequencies();
        boolean connected = computeChildConnected();
        boolean conflicted = computeMountConflicted();
        if (cycling.equals(cyclingFrequencies) && connected == childConnected && conflicted == mountConflicted)
            return;
        cyclingFrequencies = cycling;
        childConnected = connected;
        mountConflicted = conflicted;
        notifyUpdate();
    }

    /** A boolean rather than a count, so chunks loading anywhere in the child network do not spam update packets. */
    private boolean computeChildConnected() {
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(childLink.freqId, false))
            if (link != childLink)
                return true;
        return false;
    }

    /**
     * Walks downward from the child network through every loaded proxyer and
     * reports which attached parent networks the walk comes back around to.
     * Advisory only — the thread-local guard is the actual correctness mechanism.
     */
    private Set<UUID> computeCyclingFrequencies() {
        Set<UUID> parentFreqs = collectAdjacentParentFrequencies();
        if (parentFreqs.isEmpty())
            return Set.of();

        Set<UUID> reachable = new HashSet<>();
        Deque<UUID> pending = new ArrayDeque<>();
        pending.push(childLink.freqId);

        while (!pending.isEmpty()) {
            UUID freq = pending.pop();
            if (!reachable.add(freq))
                continue;
            for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(freq, false))
                if (link.blockEntity instanceof PackagerLinkBlockEntity plbe
                    && plbe.getPackager() instanceof TransitTickerBlockEntity proxy)
                    pending.push(proxy.childLink.freqId);
        }

        Set<UUID> cycling = new HashSet<>(parentFreqs);
        cycling.retainAll(reachable);
        return cycling;
    }

    // Serialization

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        Set<UUID> cycling = new HashSet<>();
        for (Tag entry : tag.getList("CyclingFrequencies", Tag.TAG_INT_ARRAY))
            cycling.add(NbtUtils.loadUUID(entry));
        cyclingFrequencies = cycling;
        childConnected = tag.getBoolean("ChildConnected");
        mountConflicted = tag.getBoolean("MountConflicted");
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        // Derived state, recomputed on initialize; only the client needs it sent.
        if (!clientPacket)
            return;
        ListTag cycling = new ListTag();
        for (UUID freq : cyclingFrequencies)
            cycling.add(NbtUtils.createUUID(freq));
        tag.put("CyclingFrequencies", cycling);
        tag.putBoolean("ChildConnected", childConnected);
        tag.putBoolean("MountConflicted", mountConflicted);
    }

    // Goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
            .append(Component.translatable("block.create_transit.transit_ticker")
                .withStyle(ChatFormatting.WHITE)));

        // Faults only, from synced fields: the client link registry sees only
        // loaded chunks, so a fresh count here would call a distant warehouse dead.
        if (!isChildConnected())
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_ticker.goggles.disconnected")
                    .withStyle(ChatFormatting.GRAY)));

        if (isMountConflicted())
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_ticker.goggles.mount_conflict")
                    .withStyle(ChatFormatting.RED)));

        if (isCycleDetected())
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_ticker.goggles.cycle")
                    .withStyle(ChatFormatting.RED)));
        return true;
    }

}
