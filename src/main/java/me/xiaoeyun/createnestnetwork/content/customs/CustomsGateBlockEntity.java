package me.xiaoeyun.createnestnetwork.content.customs;

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
 * Extends the Repackager rather than the Packager, which buys two things. The
 * first is that vanilla excludes a RepackagerBlockEntity from
 * {@code PackagerLinkBlockEntity.getPackager()} by name, so a gate can never be
 * mistaken for a stock source and the guards that used to fake that exclusion
 * are gone. The second is the buffer: a gate holds its packages in the
 * container it faces, so the tray animation depicts a box that really is moving
 * in and out of storage rather than miming over an empty block.
 *
 * Two pieces of the Repackager are deliberately not inherited. It waits for a
 * redstone pulse before pushing anything out, whereas a customs gate is always
 * open, so the send is driven from the tick instead. And it stamps its sign's
 * text onto outgoing packages as an address — here the sign carries a label,
 * which is a different thing entirely and must not end up in the address.
 */
public class CustomsGateBlockEntity extends RepackagerBlockEntity implements IHaveGoggleInformation {

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

    /**
     * A gate is always open. Vanilla only pushes a repackager's contents out on
     * a redstone pulse, which for a border checkpoint would mean packages piling
     * up in the buffer until someone flicked a lever. The send guards itself on
     * held box, animation and queue state, so calling it every tick costs
     * nothing and throttles itself.
     */
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide())
            return;
        attemptToSend(null);
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

    /**
     * Clearing customs: the label comes off on the way in, and the stripped
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

        IItemHandler buffer = getBuffer();
        if (buffer == null)
            return false;

        ItemStack stripped = box.copyWithCount(1);
        String remaining = AddressLabels.stripHeadLabel(address);
        if (remaining.isBlank())
            PackageItem.clearAddress(stripped);
        else
            PackageItem.addAddress(stripped, remaining);

        boolean anySpace = false;
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            if (!buffer.insertItem(slot, stripped, simulate)
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
     * Releases one cleared package from the buffer. Deliberately not the
     * Repackager's version: that one defragments split orders and stamps its
     * sign onto the outgoing address, and this sign holds a label, not an
     * address.
     */
    @Override
    public void attemptToSend(List<PackagingRequest> queuedRequests) {
        // A gate is never a stock source, so a link's request list is not ours
        // to fill. Vanilla already refuses to see us as one; this is what would
        // happen if some other path ever handed us requests anyway.
        if (queuedRequests != null) {
            queuedRequests.clear();
            return;
        }
        if (!heldBox.isEmpty() || animationTicks != 0 || buttonCooldown > 0)
            return;
        if (!queuedExitingPackages.isEmpty())
            return;

        IItemHandler buffer = getBuffer();
        if (buffer == null)
            return;

        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            ItemStack extracted = buffer.extractItem(slot, 1, true);
            if (extracted.isEmpty() || !PackageItem.isPackage(extracted))
                continue;
            buffer.extractItem(slot, 1, false);
            heldBox = extracted.copy();
            animationInward = false;
            animationTicks = CYCLE;
            notifyUpdate();
            return;
        }
    }

    /** The container the gate faces, or null when it is facing nothing usable. */
    @Nullable
    private IItemHandler getBuffer() {
        IItemHandler buffer = targetInventory.getInventory();
        // A packager's own handler is not storage; chaining gates mouth to
        // mouth would otherwise look like a valid buffer and deadlock.
        return buffer instanceof PackagerItemHandler ? null : buffer;
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
