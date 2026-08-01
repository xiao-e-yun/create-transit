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

    /** Create's own placeholder grey for a light-on-dark field (StationScreen). */
    private static final int HINT_COLOUR = 0xA6A6A6;

    /** Create's dark-on-light secondary text (PackagePortScreen's placeholder). */
    private static final int CAPTION_COLOUR = 0x3D3C48;

    /** The footer: caption starts here, the trash button ends it. */
    private static final int CAPTION_X = 12;
    private static final int BUTTON_ROW_X = AllGuiTextures.PACKAGE_FILTER.getWidth() - 62;

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

        // Drawn here rather than through EditBox#setHint, whose focus-dependent
        // visibility would fight the box being focused on open.
        if (labelBox.getValue()
            .isEmpty())
            graphics.drawString(font, Component.translatable("create_transit.transit_link.label_empty"),
                labelBox.getX(), labelBox.getY(), HINT_COLOUR, false);

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(guiLeft + 16, guiTop + 23, 0);
        // The first style, as Create's own getDefaultBox picks it.
        GuiGameElement.of(CtItems.TRANSIT_PACKAGES.get(0)
            .asStack())
            .render(graphics);
        ms.popPose();

        GuiGameElement.of(icon)
            .<GuiGameElement.GuiRenderBuilder>at(guiLeft + BACKGROUND.getWidth() + 8,
                guiTop + BACKGROUND.getHeight() - 52, -200)
            .scale(4)
            .render(graphics);

        // In the footer's empty half, shrunk to whatever room the buttons leave
        // so a longer translation cannot run underneath them.
        Component caption = Component.translatable("create_transit.transit_link.label_hint");
        float scale = Math.min(1f, (BUTTON_ROW_X - CAPTION_X - 4) / (float) font.width(caption));
        ms.pushPose();
        ms.translate(guiLeft + CAPTION_X,
            guiTop + BACKGROUND.getHeight() - 24 + (18 - 8 * scale) / 2f, 0);
        ms.scale(scale, scale, 1);
        graphics.drawString(font, caption, 0, 0, CAPTION_COLOUR, false);
        ms.popPose();
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
