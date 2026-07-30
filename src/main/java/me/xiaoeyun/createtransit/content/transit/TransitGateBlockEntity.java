package me.xiaoeyun.createtransit.content.transit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Transit on a domain boundary: packages passing through lose exactly one head
 * label, LIFO, and the gate is the customs office where a forwarded child
 * order is handed back to the parent order it fulfils.
 *
 * Arrivals go into the buffer the gate faces exactly as they came. Most leave
 * again on the next pass, stripped of one label and otherwise untouched. The
 * exception is boxes of a child order registered in
 * {@link TransitOrderMappings}: a ticker forwarding across this border filed
 * which parent order the child order stands in for, and those boxes wait for
 * their siblings — the whole child order converges on this label by
 * construction — then leave merged, renumbered and re-stamped with the parent
 * identity, exactly the link slot the destination's defragmenter is waiting
 * on. An entry that expired while the boxes were at sea degrades to the
 * plain strip-and-pass behaviour; a jam is never on the menu.
 *
 * The gate does no routing whatsoever — routing stays 100% vanilla hardware.
 * It only rewrites packages handed to it, and it accepts exactly
 * the packages it can act on:
 *
 * <ul>
 * <li>with a sign (a <em>named gate</em>): only packages whose head label
 * equals the sign's text, so gates can share one belt without stealing each
 * other's traffic;</li>
 * <li>without a sign (the <em>default lane</em>): any package that carries a
 * head label at all, and on the way out it stamps the label nothing is named
 * by, which is the door unnamed border traffic is addressed to.</li>
 * </ul>
 *
 * Unlabelled packages are always refused, which keeps a gate from burning its
 * cycle on domestic traffic that has no business crossing here.
 *
 * A gate runs in both directions, on the Packager's own terms. Fed a package it
 * delivers into the container it faces, cleared of one label; pulsed with
 * redstone it takes a package back out of that container and pushes its label
 * on. One border post, arrivals and departures, and the tray animates the way
 * the traffic is actually going.
 *
 * The two directions share a container, which is what makes a gate wired to
 * both a loop: the package it just stripped is the package it will stamp. Two
 * gates facing two containers is the arrangement that means anything, and the
 * only guard here is against stamping a label that is already on the head.
 *
 * Extends the Repackager rather than the Packager because vanilla excludes a
 * RepackagerBlockEntity from {@code PackagerLinkBlockEntity.getPackager()} by
 * name. A gate therefore cannot be mistaken for a stock source without us
 * faking that exclusion, which previously cost an empty summary, a filtered
 * target inventory and a stubbed link recheck — and it was the filtered
 * inventory that left the gate with nowhere to put anything in the first place.
 *
 * The sending half is replaced rather than inherited. A repackager pulls
 * packages out to defragment split orders and stamps its sign onto them as an
 * address; a gate has no business changing the identity of traffic passing
 * through, and its sign holds a label, not an address.
 */
public class TransitGateBlockEntity extends RepackagerBlockEntity implements IHaveGoggleInformation {

    /**
     * How far along a line of gates a sign carries. This is only a guard
     * against somebody building a row hundreds long and making every gate in it
     * walk the whole thing; a bank wide enough to be worth building is nowhere
     * near it.
     */
    public static final int LABEL_SHARING_RANGE = 16;

    /** A vanilla package holds up to nine stacks. */
    private static final int BOX_SLOTS = 9;

    /** Label written on this gate's own sign; blank means wildcard. */
    private String ownLabel = "";

    /** Label actually in force, possibly adopted from a nearby signed gate. */
    private String effectiveLabel = "";

    @Nullable
    private BlockPos adoptedFrom;

    public TransitGateBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public String getEffectiveLabel() {
        return effectiveLabel;
    }

    @Nullable
    public BlockPos getAdoptedFrom() {
        return adoptedFrom;
    }

    // Label resolution

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide())
            return;
        refreshLabels();
        processCustoms();
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide())
            refreshLabels();
    }

    private void refreshLabels() {
        String previousLabel = effectiveLabel;
        BlockPos previousSource = adoptedFrom;

        updateSignAddress();
        ownLabel = AddressLabels.signLabel(signBasedAddress);

        if (!ownLabel.isEmpty()) {
            effectiveLabel = ownLabel;
            adoptedFrom = null;
        } else {
            TransitGateBlockEntity donor = findNearestSignedGate();
            effectiveLabel = donor == null ? "" : donor.ownLabel;
            adoptedFrom = donor == null ? null : donor.getBlockPos();
        }

        if (!effectiveLabel.equals(previousLabel) || !Objects.equals(adoptedFrom, previousSource))
            notifyUpdate();
    }

    /**
     * The nearest gate carrying its own sign along an unbroken line of gates,
     * ties broken by block position so the result is stable across reloads.
     *
     * Contiguous and straight, rather than everything inside a radius, because
     * a bank of gates is a line of gates: parallel gates have to share the
     * container they face, and the shape that puts several of them on one
     * inventory is a row along its wall. A radius also left a gate's meaning
     * depending on whatever happened to be nearby — an unsigned gate is the
     * default lane, and having one quietly adopted by a sign ten blocks away is
     * a mistake with nothing to look at. Out of line, or one block short of
     * touching, is now the entire isolation gesture.
     *
     * Only directly signed gates are eligible, so adoption never chains and no
     * cycle can form. The walk stops at the first block that is not a gate,
     * which is also what keeps it cheap.
     */
    @Nullable
    private TransitGateBlockEntity findNearestSignedGate() {
        TransitGateBlockEntity best = null;
        int bestSteps = 0;

        for (Direction direction : Direction.values()) {
            BlockPos pos = worldPosition;
            for (int steps = 1; steps <= LABEL_SHARING_RANGE; steps++) {
                pos = pos.relative(direction);
                // Level#getBlockEntity resolves its chunk with load = true, so
                // asking about a position across an unloaded border would
                // generate terrain. A line that leaves loaded ground simply
                // ends, and reconnects when the neighbour comes back.
                if (!level.hasChunkAt(pos))
                    break;
                if (!(level.getBlockEntity(pos) instanceof TransitGateBlockEntity gate))
                    break;
                if (gate.ownLabel.isEmpty())
                    continue;

                if (best == null || steps < bestSteps
                    || (steps == bestSteps && pos.compareTo(best.getBlockPos()) < 0)) {
                    best = gate;
                    bestSteps = steps;
                }
                // Walking outward, so the first signed gate along a direction is
                // the nearest one in it.
                break;
            }
        }

        return best;
    }

    // Intake

    /**
     * Admitting a package: it goes into the customs buffer — the container the
     * gate faces — exactly as it arrived. Stripping happens on release, because
     * what release looks like depends on the box: a registered child order
     * waits for its siblings and leaves merged under its parent's identity,
     * anything else leaves stripped and otherwise untouched. Refusing before
     * anything moves keeps traffic this gate has no business with on its
     * original route.
     */
    @Override
    public boolean unwrapBox(ItemStack box, boolean simulate) {
        if (animationTicks > 0)
            return false;
        if (!PackageItem.isPackage(box))
            return false;

        String headLabel = AddressLabels.headLabelName(PackageItem.getAddress(box));
        if (headLabel == null)
            return false;
        if (!effectiveLabel.isEmpty() && !effectiveLabel.equals(headLabel))
            return false;

        IItemHandler storage = getStorage();
        if (storage == null)
            return false;

        ItemStack buffered = box.copyWithCount(1);
        boolean anySpace = false;
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            if (!storage.insertItem(slot, buffered, simulate)
                .isEmpty())
                continue;
            anySpace = true;
            break;
        }
        if (!anySpace)
            return false;
        if (simulate)
            return true;

        previouslyUnwrapped = box;
        animationInward = true;
        animationTicks = CYCLE;
        notifyUpdate();
        return true;
    }

    // Customs

    private record Member(int slot, ItemStack box) {

        CompoundTag fragment() {
            return box.getOrCreateTag()
                .getCompound("Fragment");
        }
    }

    /**
     * One action per pass, on the packager family's usual cadence: either one
     * box that owes nothing here is released, or one complete child order is
     * merged and re-stamped. Boxes of a registered child order wait in the
     * buffer for their siblings; everything else passes through immediately,
     * stripped of one label — which is what this gate did before it learned
     * customs, and remains the behaviour for legacy traffic and for orders
     * whose entry has expired.
     */
    private void processCustoms() {
        if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0)
            return;
        if (!queuedExitingPackages.isEmpty())
            return;
        IItemHandler storage = getStorage();
        if (storage == null)
            return;

        TransitOrderMappings mappings = TransitOrderMappings.get((ServerLevel) level);
        long gameTime = level.getGameTime();

        // orderId -> linkIndex -> box index -> member; registered orders only
        Map<Integer, Map<Integer, Map<Integer, Member>>> held = new HashMap<>();
        // A duplicate box index means two shipments collided on one identity;
        // merging either would corrupt both. The order stays frozen until its
        // customs entry expires, at which point the boxes release one by one.
        Set<Integer> broken = new HashSet<>();

        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.extractItem(slot, 1, true);
            if (stack.isEmpty() || !PackageItem.isPackage(stack))
                continue;
            String headLabel = AddressLabels.headLabelName(PackageItem.getAddress(stack));
            if (headLabel == null)
                continue;
            if (!effectiveLabel.isEmpty() && !effectiveLabel.equals(headLabel))
                continue;

            if (!repackageHelper.isFragmented(stack)) {
                release(storage, slot, stack);
                return;
            }
            int orderId = PackageItem.getOrderId(stack);
            if (mappings.peek(orderId, gameTime) == null) {
                release(storage, slot, stack);
                return;
            }
            Member member = new Member(slot, stack);
            CompoundTag fragment = member.fragment();
            Member previous = held.computeIfAbsent(orderId, $ -> new HashMap<>())
                .computeIfAbsent(fragment.getInt("LinkIndex"), $ -> new HashMap<>())
                .put(fragment.getInt("Index"), member);
            if (previous != null)
                broken.add(orderId);
        }

        for (Map.Entry<Integer, Map<Integer, Map<Integer, Member>>> order : held.entrySet()) {
            if (broken.contains(order.getKey()))
                continue;
            if (!isOrderComplete(order.getValue()))
                continue;
            List<Member> members = new ArrayList<>();
            order.getValue()
                .values()
                .forEach(shipment -> members.addAll(shipment.values()));
            if (mergeOrder(storage, order.getKey(), members, mappings, gameTime))
                return;
        }
    }

    /** Box indices run 0..n with exactly the last one stamped IsFinal. */
    private static boolean isShipmentComplete(Map<Integer, Member> shipment) {
        int finalIndex = -1;
        for (Map.Entry<Integer, Member> member : shipment.entrySet()) {
            if (!member.getValue()
                .fragment()
                .getBoolean("IsFinal"))
                continue;
            if (finalIndex != -1)
                return false;
            finalIndex = member.getKey();
        }
        if (finalIndex == -1 || shipment.size() != finalIndex + 1)
            return false;
        for (int i = 0; i <= finalIndex; i++)
            if (!shipment.containsKey(i))
                return false;
        return true;
    }

    /**
     * Links run 0..n with exactly the last one flagged IsFinalLink, every link
     * complete, and one address across the board — the whole child order is in
     * the buffer, so its true total is known and merging is sound.
     */
    private static boolean isOrderComplete(Map<Integer, Map<Integer, Member>> links) {
        int finalLink = -1;
        String address = null;
        for (Map.Entry<Integer, Map<Integer, Member>> link : links.entrySet()) {
            if (!isShipmentComplete(link.getValue()))
                return false;
            for (Member member : link.getValue()
                .values()) {
                String memberAddress = PackageItem.getAddress(member.box());
                if (address == null)
                    address = memberAddress;
                else if (!address.equals(memberAddress))
                    return false;
            }
            if (!link.getValue()
                .get(0)
                .fragment()
                .getBoolean("IsFinalLink"))
                continue;
            if (finalLink != -1)
                return false;
            finalLink = link.getKey();
        }
        if (finalLink == -1 || links.size() != finalLink + 1)
            return false;
        for (int i = 0; i <= finalLink; i++)
            if (!links.containsKey(i))
                return false;
        return true;
    }

    /**
     * Passing a single box through: one label off, nothing else touched. For
     * unregistered fragments this preserves whatever identity they carry — a
     * Transit Link on an ordinary packager forwards the parent identity
     * natively, and such boxes must reach the destination defragmenter intact.
     */
    private void release(IItemHandler storage, int slot, ItemStack box) {
        ItemStack sent = storage.extractItem(slot, 1, false);
        if (sent.isEmpty())
            return;
        String remaining = AddressLabels.stripHeadLabel(PackageItem.getAddress(sent));
        if (remaining.isBlank())
            PackageItem.clearAddress(sent);
        else
            PackageItem.addAddress(sent, remaining);
        heldBox = sent;
        animationInward = false;
        animationTicks = CYCLE;
        notifyUpdate();
    }

    /**
     * The whole child order becomes the parent link slot it was ordered as.
     * One label comes off the shared address; identity comes from the customs
     * entry filed by the forwarding ticker, and the numbering is rebuilt 0..n
     * with IsFinal on the last — a complete slot, exactly as if the ticker had
     * packed it locally. If the entry expired while the boxes were at sea, the
     * merge still stands, under the child's own identity as a single-link
     * order a destination defragmenter can settle by itself.
     */
    private boolean mergeOrder(IItemHandler storage, int childOrderId, List<Member> members,
        TransitOrderMappings mappings, long gameTime) {
        String address = null;
        PackageOrderWithCrafts boxContext = null;
        InventorySummary contents = new InventorySummary();
        for (Member member : members) {
            if (address == null)
                address = PackageItem.getAddress(member.box());
            if (boxContext == null)
                boxContext = PackageItem.getOrderContext(member.box());
            ItemStackHandler boxContents = PackageItem.getContents(member.box());
            for (int slot = 0; slot < boxContents.getSlots(); slot++) {
                ItemStack stack = boxContents.getStackInSlot(slot);
                if (!stack.isEmpty())
                    contents.add(stack);
            }
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (BigItemStack entry : contents.getStacks()) {
            int remaining = entry.count;
            while (remaining > 0) {
                int taken = Math.min(remaining, entry.stack.getMaxStackSize());
                stacks.add(entry.stack.copyWithCount(taken));
                remaining -= taken;
            }
        }
        // Empty boxes merged to nothing would make the shipment vanish; leave
        // such an oddity untouched rather than swallow it.
        if (stacks.isEmpty())
            return false;

        for (Member member : members)
            storage.extractItem(member.slot(), 1, false);

        TransitOrderMappings.Mapping mapping = mappings.take(childOrderId, gameTime);
        int orderId = mapping != null ? mapping.parentOrderId() : childOrderId;
        int linkIndex = mapping != null ? mapping.parentLinkIndex() : 0;
        boolean isFinalLink = mapping == null || mapping.parentIsFinalLink();
        PackageOrderWithCrafts context = mapping != null && mapping.context() != null ? mapping.context() : boxContext;
        String remaining = AddressLabels.stripHeadLabel(address);

        int boxCount = (stacks.size() + BOX_SLOTS - 1) / BOX_SLOTS;
        List<BigItemStack> merged = new ArrayList<>();
        for (int boxIndex = 0; boxIndex < boxCount; boxIndex++) {
            ItemStackHandler handler = new ItemStackHandler(BOX_SLOTS);
            for (int slot = 0; slot < BOX_SLOTS; slot++) {
                int stackIndex = boxIndex * BOX_SLOTS + slot;
                if (stackIndex >= stacks.size())
                    break;
                handler.setStackInSlot(slot, stacks.get(stackIndex));
            }
            ItemStack box = PackageItem.containing(handler);
            if (!remaining.isBlank())
                PackageItem.addAddress(box, remaining);
            PackageItem.setOrder(box, orderId, linkIndex, isFinalLink, boxIndex, boxIndex == boxCount - 1, context);
            merged.add(new BigItemStack(box, 1));
        }

        queuedExitingPackages.addAll(merged);
        notifyUpdate();
        return true;
    }

    /**
     * Departure: a redstone pulse takes one package out of the storage the gate
     * faces and pushes the gate's label onto its address.
     *
     * The shape is the Packager's — a pulse turns the contents of the attached
     * container into something addressed and sends it out the front — with the
     * one difference that a sign here holds a label rather than an address. So
     * the address is pushed onto rather than replaced: a package leaves knowing
     * both where it was already going and which border it clears on the way.
     *
     * The label goes at the head because the head is the next hop. A gate can
     * only see its own boundary, so it has no standing to claim a place at the
     * end of an itinerary it cannot read; all it can say is "me next", and
     * {@link AddressLabels#stripHeadLabel} at the far end pops exactly what was
     * pushed. That makes departure and arrival a matched pair.
     */
    @Override
    public void attemptToSend(List<PackagingRequest> queuedRequests) {
        // A gate is not a stock source. Vanilla already refuses to see a
        // repackager as one; this covers any other path that hands us requests,
        // and it is what keeps the network from ordering out of a border post.
        if (queuedRequests != null) {
            queuedRequests.clear();
            return;
        }

        if (level == null || level.isClientSide())
            return;
        if (animationTicks > 0 || !heldBox.isEmpty())
            return;

        // A Packager rereads its sign on every pulse so a sign written a moment
        // ago is the one that takes effect. Resolution here is a step longer
        // than reading the sign -- an unsigned gate looks for a donor too --
        // and the pulse is the moment the answer has to be right.
        refreshLabels();

        IItemHandler storage = getStorage();
        if (storage == null)
            return;

        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            if (!PackageItem.isPackage(stack))
                continue;
            String address = PackageItem.getAddress(stack);
            // Stamping our own label onto a package that already carries it
            // would address it right back here, and this gate would strip it
            // and put it back in the same container it came out of. An unsigned
            // gate compares its empty name against the default lane's, so this
            // covers that case by itself; an unaddressed package answers null
            // and is stamped.
            if (effectiveLabel.equals(AddressLabels.headLabelName(address)))
                continue;

            ItemStack sent = storage.extractItem(slot, 1, false);
            if (sent.isEmpty())
                continue;

            PackageItem.addAddress(sent, AddressLabels.pushEndpoint(effectiveLabel, address));
            heldBox = sent;
            animationInward = false;
            animationTicks = CYCLE;
            notifyUpdate();
            return;
        }
    }

    /** The container the gate faces, or null when it is facing nothing usable. */
    @Nullable
    private IItemHandler getStorage() {
        IItemHandler storage = targetInventory.getInventory();
        // A packager's own handler is not storage; gates mouth to mouth would
        // otherwise look like a valid destination and deadlock.
        return storage instanceof PackagerItemHandler ? null : storage;
    }

    // Serialization

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putString("OwnLabel", ownLabel);
        tag.putString("EffectiveLabel", effectiveLabel);
        if (adoptedFrom != null)
            tag.put("AdoptedFrom", NbtUtils.writeBlockPos(adoptedFrom));
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        ownLabel = tag.getString("OwnLabel");
        effectiveLabel = tag.getString("EffectiveLabel");
        adoptedFrom = tag.contains("AdoptedFrom") ? NbtUtils.readBlockPos(tag.getCompound("AdoptedFrom")) : null;
    }

    // Goggles

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
            .append(Component.translatable("block.create_transit.transit_gate")
                .withStyle(ChatFormatting.WHITE)));

        if (effectiveLabel.isEmpty()) {
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_gate.goggles.wildcard")
                    .withStyle(ChatFormatting.GRAY)));
            return true;
        }

        tooltip.add(Component.literal("    ")
            .append(Component.translatable("create_transit.transit_gate.goggles.strips",
                Component.literal(effectiveLabel)
                    .withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GOLD)));

        // Adoption acts at a distance, so always name the sign responsible
        if (adoptedFrom != null)
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_gate.goggles.adopted",
                    adoptedFrom.getX(), adoptedFrom.getY(), adoptedFrom.getZ())
                    .withStyle(ChatFormatting.DARK_GRAY)));
        return true;
    }

}
