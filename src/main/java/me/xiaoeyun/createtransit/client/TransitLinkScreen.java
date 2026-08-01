package me.xiaoeyun.createtransit.client;

import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;

import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import me.xiaoeyun.createtransit.content.transit.TransitLinkBlockEntity;
import me.xiaoeyun.createtransit.network.CtPackets;
import me.xiaoeyun.createtransit.network.TransitLinkLabelPacket;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Single-field editor for a transit link's transit label, laid out as the
 * package port's address filter is: the same header plaque, the same centred
 * borderless address box, and the block's own name shown while it is empty.
 */
public class TransitLinkScreen extends AbstractSimiScreen {

    private static final AllGuiTextures BACKGROUND = AllGuiTextures.FROGPORT_BG;
    private static final AllGuiTextures HEADER = AllGuiTextures.POSTBOX_HEADER;

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

        labelBox = new AddressEditBox(this, new NoShadowFontWrapper(font), guiLeft + 23, guiTop - 11,
            BACKGROUND.getWidth() - 20, 10, false);
        labelBox.setValue(initialLabel);
        labelBox.setTextColor(0x3D3C48);
        // Re-centres as it is typed into; AddressEditBox chains this behind its
        // own suggestion responder rather than replacing it.
        labelBox.setResponder(s -> labelBox.setX(labelBoxX(s)));
        labelBox.setX(labelBoxX(labelBox.getValue()));
        addRenderableWidget(labelBox);

        IconButton confirm = new IconButton(guiLeft + BACKGROUND.getWidth() - 33,
            guiTop + BACKGROUND.getHeight() - 24, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose);
        addRenderableWidget(confirm);
    }

    private int labelBoxX(String text) {
        return guiLeft + BACKGROUND.getWidth() / 2 - (Math.min(font.width(text), labelBox.getWidth()) + 10) / 2;
    }

    @Override
    public void tick() {
        super.tick();
        labelBox.tick();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        HEADER.render(graphics, guiLeft, guiTop - HEADER.getHeight());
        BACKGROUND.render(graphics, guiLeft, guiTop);

        String text = labelBox.getValue();
        if (!labelBox.isFocused()) {
            if (text.isEmpty()) {
                text = title.getString();
                graphics.drawString(font, text, labelBoxX(text), guiTop - 11, 0x3D3C48, false);
            }
            AllGuiTextures.FROGPORT_EDIT_NAME.render(graphics, labelBoxX(text) + font.width(text) + 5, guiTop - 14);
        }

        GuiGameElement.of(icon)
            .<GuiGameElement.GuiRenderBuilder>at(guiLeft + BACKGROUND.getWidth() + 6,
                guiTop + BACKGROUND.getHeight() - 56, -200)
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
