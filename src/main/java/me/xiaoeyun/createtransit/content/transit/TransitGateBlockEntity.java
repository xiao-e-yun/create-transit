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

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
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
 * exception is boxes carrying a {@link TransitCustoms} declaration for this
 * border: a ticker forwarding across it wrote onto the goods which parent
 * order the child order stands in for, and those boxes wait for their siblings
 * — the whole child order converges on this label by construction — then leave
 * merged, renumbered and re-stamped with the parent identity, exactly the link
 * slot the destination's defragmenter is waiting on. A box that declares
 * nothing for this border is plain strip-and-pass; a jam is never on the menu.
 *
 * Nothing about a shipment is remembered here or anywhere else. Completeness
 * is read off the boxes the way vanilla reads {@code IsFinal}, and identity is
 * read off the declaration they carry, so a reload is a non-event: the gate
 * looks at the buffer and knows everything there is to know.
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

    /**
     * The tray travel, in blocks, over which a package is inside the curtain.
     *
     * Measured rather than chosen. Create's tray comes to rest with its front face
     * at z 2.0 once the hatch transform has placed it, and the strips occupy z 0.4
     * to 1.4, so the front reaches the back of the curtain 0.038 of a block into
     * its travel and clears the front of it at 0.100.
     *
     * The window matters because the tray's travel is sampled once a tick and the
     * samples near it are 0.000, 0.062 and 0.214 — one of them, and only one, is
     * inside. Detecting a threshold being crossed instead reports the first sample
     * <em>past</em> the window, which on an arrival is 0.214: the curtain then
     * swings after the package is already clear of it, and looks late. Detecting
     * the window being entered puts the swing on the tick the package is actually
     * in the strips, which is the earliest a tick grid this coarse allows.
     *
     * Note what crosses. The tray plate spans y 2 to 4 and the strips hang from
     * y 15 down to y 4.5, so the plate passes underneath them and touches nothing
     * — it is the package, drawn centred at y 10, that goes through the curtain.
     * A crossing therefore only counts while there is a package to do it.
     */
    public static final float CURTAIN_REACHED = 0.038f;
    public static final float CURTAIN_CLEARED = 0.100f;

    /**
     * The two directions a package can shove the strips, and the sign convention
     * the renderer's swing angle reads: negative swings them out through the
     * mouth, positive back into the block. They live here rather than with the
     * geometry because a dedicated server has no Flywheel, and this class ticks
     * on one.
     */
    public static final float CURTAIN_PUSHED_OUT = -1f;
    public static final float CURTAIN_PUSHED_IN = 1f;

    /**
     * How fast a pushed curtain settles, as a fraction of the remaining swing per
     * tick. Create's flaps chase at .05, which is much slower, because their angle
     * formula oscillates inside a decaying envelope and needs a long tail to show
     * it; this swing is monotonic, so the same rate would read as sticky.
     */
    private static final float CURTAIN_SETTLE = .15f;

    /** Label written on this gate's own sign; blank means wildcard. */
    private String ownLabel = "";

    /** Label actually in force, possibly adopted from a nearby signed gate. */
    private String effectiveLabel = "";

    @Nullable
    private BlockPos adoptedFrom;

    /**
     * How far the curtain is pushed, and which way. Client side only, and derived
     * rather than synchronised: {@code animationTicks} is in the block entity's
     * client packet and counts down on the client too, so the crossing can be
     * spotted where it is needed and nothing has to be sent for it. Create's
     * tunnels do need a packet for the same effect, because an item passing a
     * tunnel is not otherwise visible to a client.
     */
    private final LerpedFloat curtain = LerpedFloat.linear()
        .startWithValue(0)
        .chase(0, CURTAIN_SETTLE, Chaser.EXP);

    /**
     * Where the tray was last tick. One field, because both facts the kick needs
     * come out of it: whether the package has just entered the curtain, and which
     * way it is travelling.
     */
    private float lastTrayOffset;

    public TransitGateBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * A push and a settle, rather than the curtain tracking the tray.
     *
     * The tray is only at the strips for about two ticks of a twenty tick cycle —
     * it spends the rest of it out beyond them — so following its position would
     * hold the curtain open the whole time the tray is away and give back two
     * twitches instead of one shove. Kicking a decaying value at the crossing
     * turns that two tick event into a movement of its own length, which is the
     * shape Create's tunnel flaps use for exactly the same reason.
     *
     * The direction comes from the travel itself, so nothing has to know which way
     * the traffic is going: a package on its way out passes through outward and
     * one being taken out of the buffer passes back in, because Create renders the
     * box on opposite halves of the tray's travel in the two cases.
     */
    @Override
    public void tick() {
        super.tick();
        if (level == null || !level.isClientSide())
            return;

        float offset = getTrayOffset(0);
        boolean entered = inCurtain(offset) && !inCurtain(lastTrayOffset);
        // A tick can be skipped, and the window is 6% of the travel, so also take
        // a sample that stepped clean over it. Both are the same event.
        boolean jumped = lastTrayOffset <= CURTAIN_REACHED && offset >= CURTAIN_CLEARED
            || lastTrayOffset >= CURTAIN_CLEARED && offset <= CURTAIN_REACHED;
        if ((entered || jumped) && !getRenderedBox().isEmpty())
            curtain.setValue(offset > lastTrayOffset ? CURTAIN_PUSHED_OUT : CURTAIN_PUSHED_IN);

        lastTrayOffset = offset;
        curtain.tickChaser();
    }

    private static boolean inCurtain(float trayOffset) {
        return trayOffset > CURTAIN_REACHED && trayOffset < CURTAIN_CLEARED;
    }

    /** How far the curtain is pushed, negative meaning out through the mouth. */
    public float curtainPush(float partialTicks) {
        return curtain.getValue(partialTicks);
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
     * what release looks like depends on the box: a child order that declares
     * this border waits for its siblings and leaves merged under its parent's
     * identity, anything else leaves stripped and otherwise untouched. Refusing
     * before anything moves keeps traffic this gate has no business with on
     * its original route.
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

    /**
     * A box the buffer scan has already vouched for: it declares this border,
     * and {@link #declaration} is the declaration it named it in. Carrying it
     * on the member is what lets the merge take the identity as given instead
     * of re-deriving it from the address a second time.
     */
    private record Member(int slot, ItemStack box, TransitCustoms declaration) {

        CompoundTag fragment() {
            return box.getOrCreateTag()
                .getCompound("Fragment");
        }
    }

    /**
     * One action per pass, on the packager family's usual cadence: either one
     * box that owes nothing here is released, or one complete child order is
     * merged and re-stamped. Boxes declaring a parent order wait in the buffer
     * for their siblings; everything else passes through immediately, stripped
     * of one label — which is what this gate did before it learned customs, and
     * remains the behaviour for traffic that declares nothing.
     */
    private void processCustoms() {
        if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0)
            return;
        if (!queuedExitingPackages.isEmpty())
            return;
        IItemHandler storage = getStorage();
        if (storage == null)
            return;

        // orderId -> linkIndex -> box index -> member; declared orders only
        Map<Integer, Map<Integer, Map<Integer, Member>>> held = new HashMap<>();
        // A duplicate box index means two shipments collided on one identity;
        // merging either would corrupt both.
        Set<Integer> broken = new HashSet<>();

        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.extractItem(slot, 1, true);
            if (stack.isEmpty() || !PackageItem.isPackage(stack))
                continue;
            String address = PackageItem.getAddress(stack);
            String headLabel = AddressLabels.headLabelName(address);
            if (headLabel == null)
                continue;
            if (!effectiveLabel.isEmpty() && !effectiveLabel.equals(headLabel))
                continue;

            if (!repackageHelper.isFragmented(stack)) {
                release(storage, slot, stack);
                return;
            }
            // A declaration answering to some other border — a different name,
            // or the same name at a different depth — belongs to a hop further
            // along and is cargo here, exactly as an outer label is.
            TransitCustoms declaration = TransitCustoms.head(stack, address);
            if (declaration == null) {
                release(storage, slot, stack);
                return;
            }
            int orderId = PackageItem.getOrderId(stack);
            Member member = new Member(slot, stack, declaration);
            CompoundTag fragment = member.fragment();
            Member previous = held.computeIfAbsent(orderId, $ -> new HashMap<>())
                .computeIfAbsent(fragment.getInt("LinkIndex"), $ -> new HashMap<>())
                .put(fragment.getInt("Index"), member);
            if (previous != null)
                broken.add(orderId);
        }

        for (Map.Entry<Integer, Map<Integer, Map<Integer, Member>>> order : held.entrySet()) {
            // A collided identity cannot be merged, and nothing about the
            // collision will ever resolve itself, so holding the boxes would
            // plug the buffer for good. They leave one by one instead,
            // stripped and under their own identity: a shipment that arrives
            // as itself rather than as the slot it stood in for, which the
            // destination can still settle, and never a jam.
            if (broken.contains(order.getKey())) {
                Member member = order.getValue()
                    .values()
                    .iterator()
                    .next()
                    .values()
                    .iterator()
                    .next();
                release(storage, member.slot(), member.box());
                return;
            }
            if (!isOrderComplete(order.getValue()))
                continue;
            List<Member> members = new ArrayList<>();
            order.getValue()
                .values()
                .forEach(shipment -> members.addAll(shipment.values()));
            if (mergeOrder(storage, members))
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
     * complete, and one address and one set of declarations across the board —
     * the whole child order is in the buffer, so its true total is known and
     * merging is sound.
     *
     * The declarations have to agree for the same reason the address does: the
     * merged boxes carry one of each onward, and a shipment whose boxes
     * disagree about where they are going, or about whom they stand in for, is
     * not a shipment.
     */
    private static boolean isOrderComplete(Map<Integer, Map<Integer, Member>> links) {
        int finalLink = -1;
        String address = null;
        List<TransitCustoms> declarations = null;
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
                List<TransitCustoms> memberDeclarations = TransitCustoms.on(member.box());
                if (declarations == null)
                    declarations = memberDeclarations;
                else if (!declarations.equals(memberDeclarations))
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
     * Passing a single box through: one label off, nothing else touched. For a
     * fragment that declares nothing here this preserves whatever identity it
     * carries — a Transit Link on an ordinary packager forwards the parent
     * identity natively, and such boxes must reach the destination
     * defragmenter intact.
     */
    private void release(IItemHandler storage, int slot, ItemStack box) {
        ItemStack sent = storage.extractItem(slot, 1, false);
        if (sent.isEmpty())
            return;
        String address = PackageItem.getAddress(sent);
        // A declaration for this border goes with the label it answers to even
        // here, where nothing was made of it — the label is about to be gone,
        // so anything claiming to belong to it is spent.
        TransitCustoms.store(sent, TransitCustoms.pop(sent, address));
        String remaining = AddressLabels.stripHeadLabel(address);
        if (remaining.isBlank())
            PackageItem.clearAddress(sent);
        else
            PackageItem.addAddress(sent, remaining);
        // The box a package wears follows the address it now carries, so a
        // shipment that just cleared its last border comes home in an ordinary
        // one and a shipment with borders left keeps the transit box.
        heldBox = TransitPackaging.restyle(sent);
        animationInward = false;
        animationTicks = CYCLE;
        notifyUpdate();
    }

    /**
     * The whole child order becomes the parent link slot it was ordered as.
     * One label comes off the shared address; identity comes from the customs
     * declaration the forwarding ticker wrote onto the goods, and the numbering
     * is rebuilt 0..n with IsFinal on the last — a complete slot, exactly as if
     * the ticker had packed it locally.
     */
    private boolean mergeOrder(IItemHandler storage, List<Member> members) {
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

        // isOrderComplete has established that every member declares the same
        // thing, so whichever one comes first answers for all of them.
        Member sample = members.get(0);
        TransitCustoms declaration = sample.declaration();
        List<TransitCustoms> onward = TransitCustoms.pop(sample.box(), address);

        for (Member member : members)
            storage.extractItem(member.slot(), 1, false);

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
            PackageItem.setOrder(box, declaration.parentOrderId(), declaration.parentLinkIndex(),
                declaration.parentIsFinalLink(), boxIndex, boxIndex == boxCount - 1, boxContext);
            // After setOrder, which is what clears them. The one place we stamp
            // an identity ourselves, and so the one place the order matters.
            // Borders further along are still owed their own paperwork.
            TransitCustoms.store(box, onward);
            // After the tag is written, since restyling copies it across.
            merged.add(new BigItemStack(TransitPackaging.restyle(box), 1));
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

            // Departure adds a label rather than consuming one, so the box's
            // declarations are all still owed and none of them is touched. They
            // stay answerable too: a declaration counts what is below it, and
            // this label goes on top.
            PackageItem.addAddress(sent, AddressLabels.pushEndpoint(effectiveLabel, address));
            heldBox = TransitPackaging.restyle(sent);
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
