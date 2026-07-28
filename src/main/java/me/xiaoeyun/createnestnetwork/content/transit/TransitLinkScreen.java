package me.xiaoeyun.createnestnetwork.content.transit;

import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;

import me.xiaoeyun.createnestnetwork.network.CnnPackets;
import me.xiaoeyun.createnestnetwork.network.TransitLinkLabelPacket;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Single-field editor for a transit link's transit label. Leaving it blank
 * makes the link a plain forwarder, which is why the placeholder spells that
 * out rather than showing an empty box.
 */
@OnlyIn(Dist.CLIENT)
public class TransitLinkScreen extends AbstractSimiScreen {

    private static final int WIDTH = 184;
    private static final int HEIGHT = 68;

    private final BlockPos pos;
    private final String initialLabel;

    private AddressEditBox labelBox;

    public TransitLinkScreen(TransitLinkBlockEntity link) {
        super(Component.translatable("block.create_nest_network.transit_link"));
        pos = link.getBlockPos();
        initialLabel = link.getLabel();
    }

    public static void open(TransitLinkBlockEntity link) {
        Minecraft.getInstance()
            .setScreen(new TransitLinkScreen(link));
    }

    @Override
    protected void init() {
        setWindowSize(WIDTH, HEIGHT);
        super.init();
        clearWidgets();

        labelBox = new AddressEditBox(this, font, guiLeft + 14, guiTop + 30, WIDTH - 56, 12, false);
        labelBox.setValue(initialLabel);
        labelBox.setTextColor(0xEEEEEE);
        addRenderableWidget(labelBox);

        IconButton confirm = new IconButton(guiLeft + WIDTH - 30, guiTop + 26, AllIcons.I_CONFIRM);
        confirm.withCallback(this::onClose);
        addRenderableWidget(confirm);
    }

    @Override
    public void tick() {
        super.tick();
        labelBox.tick();
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fill(guiLeft - 1, guiTop - 1, guiLeft + WIDTH + 1, guiTop + HEIGHT + 1, 0xFF3A3A47);
        graphics.fill(guiLeft, guiTop, guiLeft + WIDTH, guiTop + HEIGHT, 0xFF23222B);
        graphics.fill(guiLeft + 12, guiTop + 27, guiLeft + WIDTH - 40, guiTop + 43, 0xFF13121A);

        graphics.drawString(font, title, guiLeft + 12, guiTop + 12, 0xC0BDD4, false);

        if (labelBox.getValue()
            .isBlank() && !labelBox.isFocused())
            graphics.drawString(font, Component.translatable("create_nest_network.transit_link.label_empty")
                .withStyle(ChatFormatting.DARK_GRAY), guiLeft + 16, guiTop + 31, 0x5A5766, false);

        graphics.drawString(font, Component.translatable("create_nest_network.transit_link.label_hint")
            .withStyle(ChatFormatting.DARK_GRAY), guiLeft + 12, guiTop + 50, 0x5A5766, false);
    }

    @Override
    public void removed() {
        CnnPackets.CHANNEL.sendToServer(new TransitLinkLabelPacket(pos, labelBox.getValue()));
        super.removed();
    }

}
