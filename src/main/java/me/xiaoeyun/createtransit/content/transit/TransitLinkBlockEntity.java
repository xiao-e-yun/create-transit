package me.xiaoeyun.createtransit.content.transit;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Stock Link variant that stamps a transit label onto every request it
 * dispatches, declaring "this source is foreign".
 *
 * {@link PackagerLinkBlockEntity#processRequest} is the one point every request
 * passes through as it is assigned to a link, and where the
 * {@link PackagingRequest} — and with it the package's address — is created.
 * Rewriting the address here splits by source for free: within a single order,
 * local warehouses served by vanilla links stay unlabelled while foreign
 * sources served by this link get stamped.
 *
 * The label is mandatory: it is what routes the shipment to a border gate and
 * what the customs machinery keys on. A link left blank — possible only on
 * legacy saves, since the screen refuses to store blank — stamps nothing and
 * therefore behaves like a plain Stock Link; the goggle tooltip calls that
 * out in red. Everything else — tuning, protection, keepAlive, redstone
 * priority — is inherited, so lowering this link's priority with redstone
 * means "spend local stock first, only reach across the border when short".
 */
public class TransitLinkBlockEntity extends PackagerLinkBlockEntity implements IHaveGoggleInformation {

    private String label;

    public TransitLinkBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        label = "";
    }

    public String getLabel() {
        return label;
    }

    /**
     * Stores a label, refusing the wildcard name.
     *
     * A link stamps what it is given, and "every border" is not a place a
     * shipment can be sent — {@code *} only means anything to a port deciding
     * what to let in. The screen declines to send it as well; this is the guard
     * that does not take the packet's word for it.
     */
    public void setLabel(String label) {
        String sanitized = AddressLabels.sanitizeName(label);
        if (AddressLabels.WILDCARD.equals(sanitized))
            return;
        this.label = sanitized;
        notifyUpdate();
    }

    /**
     * Keeps the bulb dark while there is no label, instead of letting it blink.
     *
     * The blink means a request went through, and through an unlabelled link
     * nothing does: it stamps no border, so the ticker on the other side finds
     * no crossing to answer for and drops what arrives. Blinking would be the
     * link reporting work it did not do. Dark is the honest reading, and it is
     * the same one an unbound mounting point gives — both are blocks that
     * cannot act, and the bulb keeps red for the one fault a player has to go
     * and find.
     *
     * The cost is that a misconfigured link now looks like an idle one, since
     * Create draws no bulb at all below its glow threshold. The goggles keep
     * saying it in words.
     */
    @Override
    public float getGlow(float partialTicks) {
        return label.isBlank() ? 0 : super.getGlow(partialTicks);
    }

    @Override
    public Pair<PackagerBlockEntity, PackagingRequest> processRequest(ItemStack stack, int amount, String address,
        int linkIndex, MutableBoolean finalLink, int orderId, @Nullable PackageOrderWithCrafts context,
        @Nullable IdentifiedInventory ignoredHandler) {
        return super.processRequest(stack, amount, AddressLabels.push(label, address), linkIndex, finalLink, orderId,
            context, ignoredHandler);
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putString("Label", label);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        label = tag.getString("Label");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
            .append(Component.translatable("block.create_transit.transit_link")
                .withStyle(ChatFormatting.WHITE)));

        if (label.isBlank())
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_link.goggles.forwarding")
                    .withStyle(ChatFormatting.RED)));
        else
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_link.goggles.label",
                    Component.literal(label)
                        .withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GOLD)));
        return true;
    }

}
