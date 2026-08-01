package me.xiaoeyun.createtransit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;

import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import me.xiaoeyun.createtransit.content.transit.TransitLinkBlockEntity;
import me.xiaoeyun.createtransit.network.CtPackets;
import me.xiaoeyun.createtransit.network.TransitLinkLabelPacket;
import me.xiaoeyun.createtransit.registry.CtItems;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Single-field editor for a transit link's transit label, laid out as Create's
 * Package Filter config screen is: title plaque, one row of package icon plus
 * address box, and a trash/confirm pair. The player inventory that screen
 * carries is dropped — a link has no menu and nothing to show in one.
 */
public class TransitLinkScreen extends AbstractSimiScreen {

    private static final AllGuiTextures BACKGROUND = AllGuiTextures.PACKAGE_FILTER;

    private final BlockPos pos;
    private final String initialLabel;
    private final ItemStack icon;

    private AddressEditBox labelBox;

    public TransitLinkScreen(TransitLinkBlockEntity link) {
        super(Component.translatable("block.create_transit.transit_link"));
        pos = link.getBlockPos();
        initialLabel = link.getLabel();
        icon = new ItemStack(link.getBlockState()
            .getBlock());
    }

    public static void open(TransitLinkBlockEntity link) {
        Minecraft.getInstance()
            .setScreen(new TransitLinkScreen(link));
    }

    @Override
    protected void init() {
        setWindowSize(BACKGROUND.getWidth(), BACKGROUND.getHeight());
        super.init();
        clearWidgets();

        labelBox = new AddressEditBox(this, font, guiLeft + 44, guiTop + 28, 129, 9, false);
        labelBox.setValue(initialLabel);
        labelBox.setTextColor(0xFFFFFF);
        addRenderableWidget(labelBox);

        IconButton reset = new IconButton(guiLeft + BACKGROUND.getWidth() - 62,
            guiTop + BACKGROUND.getHeight() - 24, AllIcons.I_TRASH);
        reset.withCallback(() -> {
            labelBox.setValue("");
            setFocused(labelBox);
        });
        addRenderableWidget(reset);

        IconButton confirm = new IconButton(guiLeft + BACKGROUND.getWidth() - 33,
            guiTop + BACKGROUND.getHeight() - 24, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose);
        addRenderableWidget(confirm);

        setFocused(labelBox);
    }

    @Override
    public void tick() {
        super.tick();
        labelBox.tick();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        BACKGROUND.render(graphics, guiLeft, guiTop);
        graphics.drawString(font, title,
            guiLeft + (BACKGROUND.getWidth() - 8) / 2 - font.width(title) / 2, guiTop + 4, 0x3D3C48, false);

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(guiLeft + 16, guiTop + 23, 0);
        GuiGameElement.of(CtItems.TRANSIT_PACKAGE.asStack())
            .render(graphics);
        ms.popPose();

        GuiGameElement.of(icon)
            .<GuiGameElement.GuiRenderBuilder>at(guiLeft + BACKGROUND.getWidth() + 8,
                guiTop + BACKGROUND.getHeight() - 52, -200)
            .scale(4)
            .render(graphics);
    }

    @Override
    public void removed() {
        String value = labelBox.getValue();
        // * is the one thing a link cannot declare, so closing on it leaves the
        // link exactly as it was found.
        if (!AddressLabels.WILDCARD.equals(value.trim()))
            CtPackets.CHANNEL.sendToServer(new TransitLinkLabelPacket(pos, value));
        super.removed();
    }

}
