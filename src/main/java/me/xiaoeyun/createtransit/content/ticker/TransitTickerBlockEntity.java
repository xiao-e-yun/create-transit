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

import javax.annotation.Nullable;

import com.google.common.collect.Multimap;
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
 * Transit Ticker: a packager-shaped block that vanilla Stock Links can attach
 * to. It mounts a bound child logistics network onto the parent network, in
 * one of two modes decided by what is mounted:
 *
 * <ul>
 * <li><b>Domestic (Stock Link) — flattened mounting.</b> The child network's
 * links are re-registered under the parent frequency as shadow entries, so to
 * the parent's summaries and order assignment they simply are members of the
 * parent network: every child link takes its own link slot, its own packager
 * packs and ships physically, and identity, numbering, IsFinal, priorities
 * and inventory-identifier dedup are all vanilla's own. The ticker itself
 * reports no stock and takes no assignments — it is purely the membership
 * manager. Membership is one-way: the child's registry is untouched, so the
 * child browses only itself.</li>
 * <li><b>Cross-border (Transit Link)</b> — a request whose head label names a
 * Transit Link mounted here is foreign traffic. The ticker reports the
 * child's aggregate stock through that link, forwards the request into the
 * child network as a fresh order of the child's own, packed and shipped
 * physically under the labelled address, and a {@link TransitCustoms}
 * declaration rides along on that address telling the transit gate on the far
 * side which parent order to re-stamp the boxes for on arrival.</li>
 * </ul>
 *
 * Shadow lifecycle is the vanilla link registry's own: entries live by
 * {@code keepAlive} and expire twenty ticks after the last refresh, so a
 * broken link, an unloaded chunk, a retuned binding or a removed ticker all
 * unmount by simply no longer being refreshed — no bookkeeping to get wrong.
 *
 * Design constraints (see README):
 * - Upstream-only: the child network cannot browse or request from the parent.
 * - The mounting point never stores: {@link #unwrapBox} refuses everything —
 *   putting goods into the warehouse must travel physically.
 * - Proxy cycles are legal but inert: shadowing skips links that already live
 *   in the target network, and re-entrant summary/dispatch calls are cut off
 *   by a thread-local visited set.
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

    /** Live shadow registrations, one per (mount frequency, underlying child link). */
    private Map<UUID, Map<PackagerLinkBlockEntity, LogisticallyLinkedBehaviour>> shadowLinks = new HashMap<>();

    /** Parent frequencies the child network loops back around to. */
    private Set<UUID> cyclingFrequencies = Set.of();

    /** Whether the child network holds anything besides our own binding. */
    private boolean childConnected;

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

    public void setChildFrequency(UUID freqId) {
        LogisticallyLinkedBehaviour.remove(childLink);
        childLink.freqId = freqId;
        LogisticallyLinkedBehaviour.keepAlive(childLink);
        notifyUpdate();
        refreshNetworkState();
    }

    // Summary delegation

    @Override
    public InventorySummary getAvailableItems(boolean scanInputSlots) {
        if (level == null || level.isClientSide)
            return InventorySummary.EMPTY;
        // Flattened mounting: with a vanilla Stock Link mounted, the child's
        // links report into the parent network themselves through shadow
        // registration, and a summary from the ticker on top of that would
        // count the warehouse twice. The aggregate below serves Transit Links.
        if (!collectAdjacentMountFrequencies().isEmpty())
            return InventorySummary.EMPTY;
        UUID childFreqId = childLink.freqId;

        Set<UUID> visited = VISITED_NETWORKS.get();
        Set<UUID> parentFreqs = collectAdjacentParentFrequencies();
        if (visited.contains(childFreqId) || parentFreqs.contains(childFreqId))
            return InventorySummary.EMPTY;

        Set<UUID> added = enterNetworks(visited, childFreqId, parentFreqs);
        try {
            InventorySummary childStock = LogisticsManager.getSummaryOfNetwork(childFreqId, true);
            // Nothing shared means nothing to subtract, and the copy that would
            // have been subtracted from is the expensive part of this call: a
            // summary copy is one ItemStack copy per distinct item, NBT and
            // all, and this runs once per item type of every order that passes
            // through. Parent and child warehousing the same container is the
            // corner the subtraction exists for, not the usual shape, so the
            // usual shape should not pay for it. Handing back the cached
            // summary uncopied is vanilla's own contract — a packager returns
            // its cached field the same way, and callers only read it.
            List<LogisticallyLinkedBehaviour> shared = linksSharedWithParents(childFreqId, parentFreqs);
            if (shared.isEmpty())
                return childStock;

            InventorySummary summary = childStock.copy();
            for (LogisticallyLinkedBehaviour link : shared)
                for (BigItemStack entry : link.getSummary(null)
                    .getStacks())
                    subtractClamped(summary, entry.stack, entry.count);
            return summary;
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

        // Nothing else has business here: under flattened mounting the
        // child's own links take assignments directly and the ticker reports
        // no stock. What still lands here is a blank-labelled legacy Transit
        // Link's traffic, which the goggles already flag in red.
        queuedRequests.clear();
    }

    /**
     * A request is cross-border exactly when its head label names a Transit
     * Link mounted on this ticker: that label was stamped by the link that
     * carried the request here, declaring the child network foreign. Any
     * other label belongs to an outer border and travels with the address —
     * a request may arrive wearing labels for borders it has yet to clear,
     * and those are cargo, not routing directives for this hop.
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

        // The queue can legitimately mix link slots — several Transit Links
        // on one ticker, or two broadcasts landing back to back — and each
        // slot must become its own child order with its own declaration.
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
     * One parent link slot becomes one whole child order. The broadcast mints
     * a fresh order id, the child network packs and ships under it exactly as
     * if a local player had ordered, and the customs declaration filed here is
     * what lets the transit gate on the far side hand the boxes back to the
     * parent order on arrival. No identity is smuggled into the child network:
     * to the child, this is an ordinary order to an ordinary address, and the
     * declaration exists only between this call and the boxes it produces.
     */
    private void forwardSlot(UUID childFreqId, List<PackagingRequest> requests) {
        PackagingRequest parent = requests.get(0);
        String address = parent.address();
        TransitCustoms declaration = TransitCustoms.of(address, parent.orderId(), parent.linkIndex(), parent.finalLink()
            .booleanValue());
        // An unlabelled request names no border, so no gate would ever answer
        // to a declaration about it. That is a blank-labelled legacy transit
        // link, which the goggles already flag in red.
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

        // The stacks are the child's own — they are what decides what gets
        // packed — but the crafts are the parent's. Vanilla only reads the
        // stacks to assign the order and carries the crafts as passenger data
        // on the context it stamps onto the first box, so handing the parent's
        // crafts to the child order is what puts them on the goods, where a
        // destination repackager still finds them to rebuild recipe boxes.
        List<CraftingEntry> crafts = parent.context() == null ? List.of()
            : parent.context()
                .orderedCrafts();
        PackageOrderWithCrafts childOrder = new PackageOrderWithCrafts(new PackageOrder(feasible), crafts);

        Multimap<PackagerBlockEntity, PackagingRequest> assignment =
            LogisticsManager.findPackagersForRequest(childFreqId, childOrder, null, address);
        if (assignment.isEmpty())
            return;
        for (PackagerBlockEntity packager : assignment.keySet())
            if (packager.isTooBusyFor(RequestType.REDSTONE))
                return;

        // Borders chain here. A ticker forwarding across an inner border is one
        // of the packagers driven by the outer border's own broadcast, so the
        // outer filing is still open and its declarations ride onto this order
        // behind ours — the same stack the boxes will be stamped with, built by
        // the call chain rather than reconstructed from anything.
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
     * frequency, on the registry's own keepAlive cadence. A shadow is a plain
     * {@link LogisticallyLinkedBehaviour} whose block entity is the real child
     * link — summaries and request processing delegate to it natively — with
     * only the frequency swapped, a link id of its own, and the child link's
     * redstone priority mirrored so disabling and de-prioritising carry over.
     *
     * Shadows a nested ticker injected into the child network are enumerated
     * like any other link, so mounting chains flatten transitively, one
     * lazyTick per layer. Two guards keep cycles inert: a link that already
     * lives in the target network is never shadowed back into it, and each
     * underlying link gets at most one shadow per mount frequency no matter
     * how many paths reach it.
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
        // Anything dropped here simply stops being refreshed and expires out
        // of the registry within twenty ticks.
        shadowLinks = refreshed;
    }

    /**
     * Frequencies of the vanilla Stock Links mounted on this ticker — the
     * networks the child's links are flattened into. Transit Links are never
     * mount points: a border stays opaque, that being the point of one. A
     * link silenced by full redstone unmounts, mirroring how vanilla links
     * leave their own network.
     */
    private Set<UUID> collectAdjacentMountFrequencies() {
        Set<UUID> result = new HashSet<>();
        for (Direction d : Iterate.directions) {
            BlockPos pos = worldPosition.relative(d);
            if (!level.isLoaded(pos))
                continue;
            BlockState adjacentState = level.getBlockState(pos);
            if (!(adjacentState.getBlock() instanceof PackagerLinkBlock))
                continue;
            if (PackagerLinkBlock.getConnectedDirection(adjacentState) != d)
                continue;
            if (!(level.getBlockEntity(pos) instanceof PackagerLinkBlockEntity plbe))
                continue;
            if (plbe instanceof TransitLinkBlockEntity)
                continue;
            if (plbe.behaviour != null && plbe.behaviour.redstonePower != 15)
                result.add(plbe.behaviour.freqId);
        }
        return result;
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

    /**
     * Labels of the Transit Links mounted on this ticker — the set of borders
     * this mounting point is declared to sit on. A blank label contributes
     * nothing: it stamps nothing, so no request can carry it here.
     */
    private Set<String> collectAdjacentTransitLabels() {
        Set<String> result = new HashSet<>();
        for (Direction d : Iterate.directions) {
            BlockPos pos = worldPosition.relative(d);
            if (!level.isLoaded(pos))
                continue;
            BlockState adjacentState = level.getBlockState(pos);
            if (!(adjacentState.getBlock() instanceof PackagerLinkBlock))
                continue;
            if (PackagerLinkBlock.getConnectedDirection(adjacentState) != d)
                continue;
            if (level.getBlockEntity(pos) instanceof TransitLinkBlockEntity tlbe && !tlbe.getLabel()
                .isBlank())
                result.add(tlbe.getLabel());
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
        refreshNetworkState();
    }

    /** Whether any proxy cycle runs through this mounting point. */
    public boolean isCycleDetected() {
        return !cyclingFrequencies.isEmpty();
    }

    /**
     * Whether a cycle runs through the parent network on {@code freq}.
     *
     * Asked per frequency rather than per mounting point because a ticker can
     * wear several links at once, and a loop closing through one of them says
     * nothing about the others: a network the child cannot reach is a healthy
     * mounting however badly its neighbour is tangled. The walk below already
     * knew which network it had come back around to; only the answer used to
     * throw that away.
     *
     * Readable on the client: the set is written into every update tag, and a
     * link's own frequency is already synced by Create, so the bulb renderer
     * can ask without a lookup of its own and without anything being synced
     * for its sake.
     */
    public boolean isCycling(UUID freq) {
        return cyclingFrequencies.contains(freq);
    }

    /**
     * Whether the binding reaches anything at all.
     *
     * Answered on the server and synced, which is the whole point of the field.
     * The link registry has a client half, but it is filled by links calling
     * keepAlive from their own lazyTick, so it only ever holds what the player
     * has loaded — a warehouse across the world contributes nothing to it, and
     * anything answering this there would call a healthy network unbound. That
     * half exists for the tuning item's sixty-four block highlight, which asks
     * a different question: what is near me, not whether the binding is alive.
     */
    public boolean isChildConnected() {
        return childConnected;
    }

    private void refreshNetworkState() {
        Set<UUID> cycling = computeCyclingFrequencies();
        boolean connected = computeChildConnected();
        if (cycling.equals(cyclingFrequencies) && connected == childConnected)
            return;
        cyclingFrequencies = cycling;
        childConnected = connected;
        notifyUpdate();
    }

    /**
     * Whether anything else is on the child frequency. A boolean rather than a
     * count on purpose: the count moves whenever a chunk somewhere in the child
     * network loads or unloads, and every move would be an update packet for a
     * number nothing reads. Create keeps the same distinction — it tracks
     * {@code contributingLinks} internally but only ever shows the player
     * {@code activeLinks == 0}.
     */
    private boolean computeChildConnected() {
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(childLink.freqId, false))
            if (link != childLink)
                return true;
        return false;
    }

    /**
     * Walks downward from the bound child network through every loaded proxyer
     * and reports which of our attached parent networks the walk comes back
     * around to. Each of those is a closed loop through this mounting point;
     * a parent the child cannot reach is not implicated and is left out.
     *
     * The walk runs to exhaustion rather than stopping at the first hit, which
     * is the whole of the cost of naming the networks instead of merely
     * counting one. It is bounded by the number of distinct frequencies that
     * have a proxyer on them, and it touches only the in-memory link registry.
     *
     * Advisory only — the thread-local guard remains the actual correctness
     * mechanism.
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

    // Duplicate-count protection: inventories visible to both the parent
    // network (directly) and the child network (through us) only count once.

    /**
     * The child links whose inventory the parent network can already see for
     * itself, one per shared inventory.
     *
     * Answered before anything is copied, so that the common topology — parent
     * and child warehousing separate containers — costs a scan and no
     * allocation at all. The scan was being paid for anyway; only the copy it
     * used to be handed was avoidable.
     */
    private List<LogisticallyLinkedBehaviour> linksSharedWithParents(UUID childFreqId, Set<UUID> parentFreqs) {
        if (parentFreqs.isEmpty())
            return List.of();

        Set<InventoryIdentifier> parentInventories = new HashSet<>();
        for (UUID parentFreq : parentFreqs)
            for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(parentFreq, false)) {
                InventoryIdentifier identifier = inventoryIdentifierOf(link);
                if (identifier != null)
                    parentInventories.add(identifier);
            }
        if (parentInventories.isEmpty())
            return List.of();

        List<LogisticallyLinkedBehaviour> shared = new ArrayList<>();
        Set<InventoryIdentifier> seen = new HashSet<>();
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(childFreqId, false)) {
            InventoryIdentifier identifier = inventoryIdentifierOf(link);
            if (identifier == null || !parentInventories.contains(identifier))
                continue;
            // The child-side aggregation already de-duplicated per identifier,
            // so subtract each shared inventory at most once.
            if (seen.add(identifier))
                shared.add(link);
        }
        return shared;
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
        Set<UUID> cycling = new HashSet<>();
        for (Tag entry : tag.getList("CyclingFrequencies", Tag.TAG_INT_ARRAY))
            cycling.add(NbtUtils.loadUUID(entry));
        cyclingFrequencies = cycling;
        childConnected = tag.getBoolean("ChildConnected");
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        ListTag cycling = new ListTag();
        for (UUID freq : cyclingFrequencies)
            cycling.add(NbtUtils.createUUID(freq));
        tag.put("CyclingFrequencies", cycling);
        tag.putBoolean("ChildConnected", childConnected);
    }

    // Goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
            .append(Component.translatable("block.create_transit.transit_ticker")
                .withStyle(ChatFormatting.WHITE)));

        // Only the faults get a line, which is Create's own shape for this:
        // StockKeeperRequestScreen answers with a troubleshooting message and
        // otherwise shows the stock, so a healthy network is never announced,
        // it is simply what you see. Here the bulb is what you see -- a white
        // heartbeat is the mounting point saying it is bound and polling -- so
        // a line repeating that would be the tooltip talking over the block.
        //
        // The synced field, never a fresh count. This runs on the client, where
        // the link registry holds only what the player has loaded, so asking
        // here would call a warehouse across the world unreachable, and would
        // disagree with the bulb, which reads the same field.
        if (!isChildConnected())
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_ticker.goggles.disconnected")
                    .withStyle(ChatFormatting.GRAY)));

        if (isCycleDetected())
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_ticker.goggles.cycle")
                    .withStyle(ChatFormatting.RED)));
        return true;
    }

}
