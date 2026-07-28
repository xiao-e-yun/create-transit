package me.xiaoeyun.createnestnetwork.content.customs;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

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

/**
 * Customs on a domain boundary: packages passing through lose exactly one head
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
 * cycle on domestic traffic that has no customs business with it.
 *
 * Structurally this is the same mould as Create's Repackager — a packager that
 * takes packages in and puts modified packages out — so the entire item
 * pipeline, exit queue, animation, sign reading and drop-on-break behaviour are
 * inherited rather than reimplemented.
 */
public class CustomsGateBlockEntity extends PackagerBlockEntity implements IHaveGoggleInformation {

    /** Radius in blocks over which an unsigned gate may adopt a signed gate's label. */
    public static final int LABEL_SHARING_RANGE = 16;

    /** Label written on this gate's own sign; blank means wildcard. */
    private String ownLabel = "";

    /** Label actually in force, possibly adopted from a nearby signed gate. */
    private String effectiveLabel = "";

    @Nullable
    private BlockPos adoptedFrom;

    public CustomsGateBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // A gate is not a storage endpoint: it must never present an inventory
        // to a Stock Link, which would otherwise treat it as a stock source
        // (vanilla only hardcodes the Repackager out of that path).
        targetInventory.withFilter($ -> false);
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
            CustomsGateBlockEntity donor = findNearestSignedGate();
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
    private CustomsGateBlockEntity findNearestSignedGate() {
        ChunkPos min = new ChunkPos(worldPosition.offset(-LABEL_SHARING_RANGE, 0, -LABEL_SHARING_RANGE));
        ChunkPos max = new ChunkPos(worldPosition.offset(LABEL_SHARING_RANGE, 0, LABEL_SHARING_RANGE));

        CustomsGateBlockEntity best = null;
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
                    if (!(entry.getValue() instanceof CustomsGateBlockEntity gate) || gate == this)
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

    @Override
    public boolean unwrapBox(ItemStack box, boolean simulate) {
        if (animationTicks > 0 || !heldBox.isEmpty())
            return false;
        if (!PackageItem.isPackage(box))
            return false;

        String address = PackageItem.getAddress(box);
        String headLabel = AddressLabels.headLabelName(address);
        if (headLabel == null)
            return false;
        if (!effectiveLabel.isEmpty() && !effectiveLabel.equals(headLabel))
            return false;
        if (simulate)
            return true;

        ItemStack stripped = box.copyWithCount(1);
        String remaining = AddressLabels.stripHeadLabel(address);
        if (remaining.isBlank())
            PackageItem.clearAddress(stripped);
        else
            PackageItem.addAddress(stripped, remaining);

        queuedExitingPackages.add(new BigItemStack(stripped, 1));
        notifyUpdate();
        return true;
    }

    // Not a logistics endpoint

    @Override
    public InventorySummary getAvailableItems(boolean scanInputSlots) {
        return InventorySummary.EMPTY;
    }

    @Override
    public void attemptToSend(List<PackagingRequest> queuedRequests) {
        if (queuedRequests != null)
            queuedRequests.clear();
    }

    @Override
    public void recheckIfLinksPresent() {}

    @Override
    public boolean redstoneModeActive() {
        return true;
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
            .append(Component.translatable("block.create_nest_network.customs_gate")
                .withStyle(ChatFormatting.WHITE)));

        if (effectiveLabel.isEmpty()) {
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_nest_network.customs_gate.goggles.wildcard")
                    .withStyle(ChatFormatting.GRAY)));
            return true;
        }

        tooltip.add(Component.literal("    ")
            .append(Component.translatable("create_nest_network.customs_gate.goggles.strips",
                Component.literal(AddressLabels.OPEN + effectiveLabel + AddressLabels.CLOSE)
                    .withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GOLD)));

        // Adoption acts at a distance, so always name the sign responsible
        if (adoptedFrom != null)
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_nest_network.customs_gate.goggles.adopted",
                    adoptedFrom.getX(), adoptedFrom.getY(), adoptedFrom.getZ())
                    .withStyle(ChatFormatting.DARK_GRAY)));
        return true;
    }

}
