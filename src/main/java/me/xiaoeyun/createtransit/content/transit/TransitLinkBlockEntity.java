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
import net.minecraft.core.HolderLookup;
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
 * The label is what routes the shipment to a border gate and what the customs
 * machinery keys on. Clearing it disables the link: it stamps nothing, declares
 * no border, and behaves like a plain Stock Link, with the bulb dark to say so.
 * Everything else — tuning, protection, keepAlive, redstone priority — is
 * inherited, so lowering this link's priority with redstone means "spend local
 * stock first, only reach across the border when short".
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

    /** Whether this link declares a border at all; a blank label is the disabled state. */
    public boolean isActive() {
        return !label.isBlank();
    }

    /** Stores a label, refusing only the wildcard: "every border" is not a place a shipment can be sent. */
    public void setLabel(String label) {
        String sanitized = AddressLabels.sanitizeName(label);
        if (AddressLabels.WILDCARD.equals(sanitized))
            return;
        this.label = sanitized;
        notifyUpdate();
    }

    /** Dark while disabled: the blink means a request went through, and through a disabled link none does. */
    @Override
    public float getGlow(float partialTicks) {
        return isActive() ? super.getGlow(partialTicks) : 0;
    }

    @Override
    public Pair<PackagerBlockEntity, PackagingRequest> processRequest(ItemStack stack, int amount, String address,
        int linkIndex, MutableBoolean finalLink, int orderId, @Nullable PackageOrderWithCrafts context,
        @Nullable IdentifiedInventory ignoredHandler) {
        return super.processRequest(stack, amount, AddressLabels.push(label, address), linkIndex, finalLink, orderId,
            context, ignoredHandler);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putString("Label", label);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        label = tag.getString("Label");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
            .append(Component.translatable("block.create_transit.transit_link")
                .withStyle(ChatFormatting.WHITE)));

        if (!isActive())
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_link.goggles.disabled")
                    .withStyle(ChatFormatting.GRAY)));
        else
            tooltip.add(Component.literal("    ")
                .append(Component.translatable("create_transit.transit_link.goggles.label",
                    Component.literal(label)
                        .withStyle(ChatFormatting.WHITE))
                    .withStyle(ChatFormatting.GOLD)));
        return true;
    }

}
