package me.xiaoeyun.createtransit.content.transit;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.items.IItemHandler;

/**
 * Transit on a domain boundary: packages passing through lose exactly one head
 * label, LIFO, and nothing else about the address is touched.
 *
 * The gate does no routing whatsoever — routing stays 100% vanilla hardware.
 * It only rewrites the address of packages handed to it, and it accepts exactly
 * the packages it can act on:
 *
 * <ul>
 * <li>with a sign (a <em>named gate</em>): only packages whose head label
 * equals the sign's text, so gates can share one belt without stealing each
 * other's traffic;</li>
 * <li>without a sign (a <em>wildcard exit</em>): any package that carries a
 * head label.</li>
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

    /** Radius in blocks over which an unsigned gate may adopt a signed gate's label. */
    public static final int LABEL_SHARING_RANGE = 16;

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
        ownLabel = readLabel(signBasedAddress);

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
     * Accepts either a bare name or a fully delimited label on the sign, so a
     * player copying an address verbatim off a package still gets what they
     * meant.
     */
    private static String readLabel(String signText) {
        String text = signText == null ? "" : signText.trim();
        String name = AddressLabels.headLabelName(text);
        return AddressLabels.sanitizeName(name != null ? name : text);
    }

    /**
     * Nearest gate carrying its own sign within {@link #LABEL_SHARING_RANGE},
     * ties broken by block position so the result is stable across reloads.
     *
     * Only directly signed gates are eligible: adoption never chains, which
     * would otherwise let one sign propagate along a line of gates and could
     * form cycles. Scanning walks the block entity maps of the few chunks in
     * range rather than the ~36k positions the radius spans, and never forces a
     * chunk to load.
     */
    @Nullable
    private TransitGateBlockEntity findNearestSignedGate() {
        ChunkPos min = new ChunkPos(worldPosition.offset(-LABEL_SHARING_RANGE, 0, -LABEL_SHARING_RANGE));
        ChunkPos max = new ChunkPos(worldPosition.offset(LABEL_SHARING_RANGE, 0, LABEL_SHARING_RANGE));

        TransitGateBlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        int range = LABEL_SHARING_RANGE * LABEL_SHARING_RANGE;

        for (int cx = min.x; cx <= max.x; cx++) {
            for (int cz = min.z; cz <= max.z; cz++) {
                LevelChunk chunk = level.getChunkSource()
                    .getChunkNow(cx, cz);
                if (chunk == null)
                    continue;

                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities()
                    .entrySet()) {
                    if (!(entry.getValue() instanceof TransitGateBlockEntity gate) || gate == this)
                        continue;
                    if (gate.ownLabel.isEmpty())
                        continue;

                    BlockPos pos = entry.getKey();
                    double distance = pos.distSqr(worldPosition);
                    if (distance > range)
                        continue;
                    if (distance > bestDistance)
                        continue;
                    if (distance == bestDistance && best != null && pos.compareTo(best.getBlockPos()) >= 0)
                        continue;

                    best = gate;
                    bestDistance = distance;
                }
            }
        }

        return best;
    }

    // Label stripping

    /**
     * Admitting a package: the label comes off on the way in, and the stripped
     * package lands in the buffer the gate faces. Refusing before anything moves
     * keeps traffic this gate has no business with on its original route.
     */
    @Override
    public boolean unwrapBox(ItemStack box, boolean simulate) {
        if (animationTicks > 0)
            return false;
        if (!PackageItem.isPackage(box))
            return false;

        String address = PackageItem.getAddress(box);
        String headLabel = AddressLabels.headLabelName(address);
        if (headLabel == null)
            return false;
        if (!effectiveLabel.isEmpty() && !effectiveLabel.equals(headLabel))
            return false;

        IItemHandler storage = getStorage();
        if (storage == null)
            return false;

        ItemStack stripped = box.copyWithCount(1);
        String remaining = AddressLabels.stripHeadLabel(address);
        if (remaining.isBlank())
            PackageItem.clearAddress(stripped);
        else
            PackageItem.addAddress(stripped, remaining);

        boolean anySpace = false;
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            if (!storage.insertItem(slot, stripped, simulate)
                .isEmpty())
                continue;
            anySpace = true;
            break;
        }
        if (!anySpace)
            return false;
        if (simulate)
            return true;

        // The box drawn travelling inward is the one that arrived, labelled.
        previouslyUnwrapped = box;
        animationInward = true;
        animationTicks = CYCLE;
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
        // A gate with no label of its own and no donor in range has nothing to
        // stamp, so it is an entrance only. Wildcard means "any label" on the
        // way in, which does not name one on the way out.
        if (effectiveLabel.isEmpty())
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
            // would address it right back here, and this gate would strip it
            // and put it back in the same container it came out of.
            if (effectiveLabel.equals(AddressLabels.headLabelName(address)))
                continue;

            ItemStack sent = storage.extractItem(slot, 1, false);
            if (sent.isEmpty())
                continue;

            PackageItem.addAddress(sent, AddressLabels.push(effectiveLabel, address));
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
