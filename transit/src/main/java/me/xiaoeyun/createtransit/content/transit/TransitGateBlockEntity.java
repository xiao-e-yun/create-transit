package me.xiaoeyun.createtransit.content.transit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
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

/**
 * Transit on a domain boundary: packages passing through lose exactly one head
 * label, LIFO, and the gate is the customs office where a forwarded child order
 * is handed back to the parent order it fulfils.
 *
 * Arrivals go into the buffer the gate faces exactly as they came, and most
 * leave again on the next pass stripped of one label. The exception is boxes
 * carrying a {@link TransitCustoms} declaration for this border: they wait for
 * their siblings, then leave repacked and re-stamped with the parent identity,
 * exactly the link slot the destination's defragmenter is waiting on. An
 * incomplete declared order holds the buffer until it completes, which is the
 * vanilla Repackager's own behaviour.
 *
 * Nothing about a shipment is remembered here: completeness is read off the
 * boxes the way vanilla reads {@code IsFinal}, identity off the declaration
 * they carry, so a reload is a non-event.
 *
 * The gate does no routing — routing stays vanilla hardware. It only rewrites
 * packages handed to it, accepting a package whose head label its sign claims;
 * a sign reading {@code *} claims every label and an unsigned gate is the
 * default lane, which also stamps the unnamed label on departure. Unlabelled
 * packages are always refused.
 *
 * A gate runs in both directions on the Packager's own terms: fed a package it
 * delivers into the container it faces cleared of one label, pulsed with
 * redstone it takes one back out and pushes its label on. Both directions share
 * that container, so two gates facing two containers is the arrangement that
 * means anything; the only guard here is against stamping a label already on
 * the head.
 *
 * Extends the Repackager rather than the Packager because vanilla excludes a
 * RepackagerBlockEntity from {@code PackagerLinkBlockEntity.getPackager()} by
 * name, so a gate cannot be mistaken for a stock source. The sending half is
 * replaced rather than inherited, since a repackager stamps its sign onto
 * traffic as an address and a gate's sign holds a label.
 */
public class TransitGateBlockEntity extends RepackagerBlockEntity implements IHaveGoggleInformation {

    /** How far along a line of gates a sign carries, so a long row stays cheap to walk. */
    private static final int LABEL_SHARING_RANGE = 16;

    /**
     * The tray travel, in blocks, over which a package is inside the curtain.
     * Measured: the tray front reaches the back of the strips 0.038 of a block
     * into its travel and clears their front at 0.100.
     */
    private static final float CURTAIN_REACHED = 0.038f;
    private static final float CURTAIN_CLEARED = 0.100f;

    /**
     * The renderer's swing sign convention: negative swings the strips out
     * through the mouth, positive back into the block. Here rather than with the
     * geometry because a dedicated server has no Flywheel and this class ticks on one.
     */
    public static final float CURTAIN_PUSHED_OUT = -1f;
    public static final float CURTAIN_PUSHED_IN = 1f;

    /** How fast a pushed curtain settles, as a fraction of the remaining swing per tick. */
    private static final float CURTAIN_SETTLE = .15f;

    /** Label written on this gate's own sign; blank means wildcard. */
    private String ownLabel = "";

    /** Label actually in force, possibly adopted from a nearby signed gate. */
    private String effectiveLabel = "";

    @Nullable
    private BlockPos adoptedFrom;

    /** How far the curtain is pushed, and which way. Client side only, derived from the synced animationTicks. */
    private final LerpedFloat curtain = LerpedFloat.linear()
        .startWithValue(0)
        .chase(0, CURTAIN_SETTLE, Chaser.EXP);

    /** Where the tray was last tick; both the crossing and its direction come out of it. */
    private float lastTrayOffset;

    public TransitGateBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** A push and a settle, like Create's tunnel flaps: the tray is only at the strips for about two ticks. */
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

    /**
     * Whether this gate's sign claims a package's head label. An unsigned gate
     * is the default lane and takes any label; {@link AddressLabels#WILDCARD}
     * takes any label too, as a port filter does.
     */
    private boolean claimsLabel(@Nullable String headLabel) {
        return headLabel != null && (effectiveLabel.isEmpty() || AddressLabels.WILDCARD.equals(effectiveLabel)
            || effectiveLabel.equals(headLabel));
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
     * ties broken by block position so the result is stable across reloads. Only
     * directly signed gates are eligible, so adoption never chains.
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
                // asking across an unloaded border would generate terrain.
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
     * gate faces — exactly as it arrived, because what release looks like
     * depends on the box. Refusing before anything moves keeps traffic this gate
     * has no business with on its original route.
     */
    @Override
    public boolean unwrapBox(ItemStack box, boolean simulate) {
        if (animationTicks > 0)
            return false;
        if (!PackageItem.isPackage(box))
            return false;
        if (!claimsLabel(AddressLabels.headLabelName(PackageItem.getAddress(box))))
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

        computerBehaviour.prepareComputerEvent(new PackageEvent(box, "package_received"));
        previouslyUnwrapped = box;
        animationInward = true;
        animationTicks = CYCLE;
        notifyUpdate();
        return true;
    }

    // Customs

    /**
     * One action per pass, on the packager family's usual cadence: either one
     * box that owes nothing here is released, or one complete child order is
     * repacked and re-stamped. Boxes declaring a parent order wait in the buffer
     * for their siblings; everything else passes through stripped of one label.
     */
    private void processCustoms() {
        if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0)
            return;
        if (!queuedExitingPackages.isEmpty())
            return;
        IItemHandler storage = getStorage();
        if (storage == null)
            return;

        repackageHelper.clear();
        Map<Integer, List<ItemStack>> declared = new HashMap<>();
        int completedOrderId = -1;

        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.extractItem(slot, 1, true);
            if (stack.isEmpty() || !PackageItem.isPackage(stack))
                continue;
            String address = PackageItem.getAddress(stack);
            if (!claimsLabel(AddressLabels.headLabelName(address)))
                continue;

            if (!repackageHelper.isFragmented(stack)) {
                release(storage, slot, stack);
                return;
            }
            // A declaration answering to some other border — a different name,
            // or the same name at a different depth — belongs to a hop further
            // along and is cargo here, exactly as an outer label is.
            if (TransitCustoms.head(stack, address) == null) {
                release(storage, slot, stack);
                return;
            }
            declared.computeIfAbsent(PackageItem.getOrderId(stack), $ -> new ArrayList<>())
                .add(stack);
            completedOrderId = repackageHelper.addPackageFragment(stack);
            if (completedOrderId != -1)
                break;
        }

        if (completedOrderId == -1)
            return;
        List<ItemStack> members = declared.get(completedOrderId);
        // A disagreeing order waits in the buffer like an incomplete one: the jam
        // is visible and traceable in place, and pulling the offender heals it.
        if (agree(members))
            mergeOrder(storage, completedOrderId, members);
    }

    /**
     * The merged boxes carry one address and one set of declarations onward, so
     * a shipment whose members disagree about either is not a shipment.
     */
    private static boolean agree(List<ItemStack> members) {
        String address = PackageItem.getAddress(members.get(0));
        List<TransitCustoms> declarations = TransitCustoms.on(members.get(0));
        for (ItemStack box : members)
            if (!address.equals(PackageItem.getAddress(box)) || !declarations.equals(TransitCustoms.on(box)))
                return false;
        return true;
    }

    /**
     * Passing a single box through: one label off, nothing else touched, so a
     * fragment forwarded natively by a Transit Link reaches the destination
     * defragmenter with its identity intact.
     */
    private void release(IItemHandler storage, int slot, ItemStack box) {
        ItemStack sent = storage.extractItem(slot, 1, false);
        if (sent.isEmpty())
            return;
        String address = PackageItem.getAddress(sent);
        // The label is about to be gone, so a declaration answering to it is spent.
        TransitCustoms.store(sent, TransitCustoms.pop(sent, address));
        String remaining = AddressLabels.stripHeadLabel(address);
        if (remaining.isBlank())
            PackageItem.clearAddress(sent);
        else
            PackageItem.addAddress(sent, remaining);
        // The box a package wears follows the address it now carries.
        heldBox = TransitPackaging.restyle(sent);
        animationInward = false;
        animationTicks = CYCLE;
        notifyUpdate();
    }

    /**
     * The whole child order becomes the parent link slot it was ordered as:
     * upstream repacks it — recipe-aware when the order carried crafts — one
     * label comes off the shared address, identity comes from the customs
     * declaration the forwarding ticker wrote onto the goods, and the numbering
     * is rebuilt 0..n with IsFinal on the last.
     */
    private void mergeOrder(IItemHandler storage, int orderId, List<ItemStack> members) {
        ItemStack sample = members.get(0);
        String address = PackageItem.getAddress(sample);
        TransitCustoms declaration = TransitCustoms.head(sample, address);
        List<TransitCustoms> onward = TransitCustoms.pop(sample, address);

        // Upstream returns recipe boxes as one entry per stack of identical
        // copies; the parent slot numbers every physical box.
        List<ItemStack> boxes = new ArrayList<>();
        for (BigItemStack entry : repackageHelper.repack(orderId, level.getRandom()))
            for (int copy = 0; copy < entry.count; copy++)
                boxes.add(entry.stack.copy());
        // Merging to nothing would make the shipment vanish; leave the oddity untouched.
        if (boxes.isEmpty())
            return;

        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.extractItem(slot, 1, true);
            if (!stack.isEmpty() && PackageItem.isPackage(stack) && PackageItem.getOrderId(stack) == orderId)
                storage.extractItem(slot, 1, false);
        }

        String remaining = AddressLabels.stripHeadLabel(address);
        List<BigItemStack> merged = new ArrayList<>();
        for (int index = 0; index < boxes.size(); index++) {
            ItemStack box = boxes.get(index);
            if (remaining.isBlank())
                PackageItem.clearAddress(box);
            else
                PackageItem.addAddress(box, remaining);
            boolean last = index == boxes.size() - 1;
            // setOrder rewrites Fragment wholesale, and repack already put the right
            // context on each box — a single recipe on every one it split by recipe.
            PackageOrderWithCrafts context = PackageItem.getOrderContext(box);
            PackageItem.setOrder(box, declaration.parentOrderId(), declaration.parentLinkIndex(),
                declaration.parentIsFinalLink(), index, last, context);
            // After setOrder, which is what clears them.
            TransitCustoms.store(box, onward);
            // After the tag is written, since restyling copies it across.
            merged.add(new BigItemStack(TransitPackaging.restyle(box), 1));
        }

        queuedExitingPackages.addAll(merged);
        notifyUpdate();
    }

    /**
     * Departure: a redstone pulse takes one package out of the storage the gate
     * faces and pushes the gate's label onto the head of its address, which is
     * exactly what {@link AddressLabels#stripHeadLabel} pops at the far end.
     */
    @Override
    public void attemptToSend(List<PackagingRequest> queuedRequests) {
        // A gate is not a stock source; this covers any path that hands us
        // requests despite vanilla already refusing to see a repackager as one.
        if (queuedRequests != null) {
            queuedRequests.clear();
            return;
        }

        if (level == null || level.isClientSide())
            return;
        if (animationTicks > 0 || !heldBox.isEmpty())
            return;

        // A Packager rereads its sign on every pulse, and resolution here is a
        // step longer than that: an unsigned gate looks for a donor too.
        refreshLabels();

        // A wildcard sign is a filter, not a place: it claims every label and so
        // has none of its own to stamp, the same reason a Transit Link refuses it.
        if (AddressLabels.WILDCARD.equals(effectiveLabel))
            return;

        IItemHandler storage = getStorage();
        if (storage == null)
            return;

        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            if (!PackageItem.isPackage(stack))
                continue;
            String address = PackageItem.getAddress(stack);
            // Stamping our own label onto a package that already carries it
            // would address it right back into the container it came out of.
            if (effectiveLabel.equals(AddressLabels.headLabelName(address)))
                continue;

            ItemStack sent = storage.extractItem(slot, 1, false);
            if (sent.isEmpty())
                continue;

            // Departure adds a label rather than consuming one, so every
            // declaration on the box is still owed and none is touched.
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
        // Derived from the sign, recomputed on initialize; only the client needs it sent.
        if (!clientPacket)
            return;
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
            .append(AddressLabels.WILDCARD.equals(effectiveLabel)
                ? Component.translatable("create_transit.transit_gate.goggles.any")
                    .withStyle(ChatFormatting.GOLD)
                : Component.translatable("create_transit.transit_gate.goggles.strips",
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
