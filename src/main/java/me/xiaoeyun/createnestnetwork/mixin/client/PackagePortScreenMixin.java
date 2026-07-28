package me.xiaoeyun.createnestnetwork.mixin.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.logistics.packagePort.PackagePortMenu;
import com.simibubi.create.content.logistics.packagePort.PackagePortScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;

import me.xiaoeyun.createnestnetwork.content.transit.AddressLabels;
import me.xiaoeyun.createnestnetwork.content.transit.PortLabelEditor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Adds transit label editing to both package ports — frogports and postboxes
 * share this one screen, so a single mixin covers them.
 *
 * Purely presentational: the address box below keeps holding the path alone,
 * the label panel holds the label, and the two are joined into the ordinary
 * wire string on the way to {@code PackagePortConfigurationPacket}. The packet
 * and everything server-side are untouched, and an address typed out by hand
 * remains exactly equivalent to one built here.
 */
@Mixin(PackagePortScreen.class)
public abstract class PackagePortScreenMixin extends AbstractSimiContainerScreen<PackagePortMenu> {

    @Shadow(remap = false)
    private EditBox addressBox;

    @Shadow(remap = false)
    private AllGuiTextures background;

    @Shadow(remap = false)
    private ItemStack icon;

    @Shadow(remap = false)
    private int nameBoxX(String text, EditBox nameBox) {
        throw new AssertionError();
    }

    /**
     * Absent until {@code init} has run to its end, which is later than it
     * sounds: Create ticks the screen from inside {@code init} itself, so the
     * editor is missing for that first tick however early it is built. Every
     * entry point below asks whether it is there rather than assuming it, and
     * the port behaves as an unmodified one until it is.
     */
    @Unique
    @Nullable
    private PortLabelEditor createNestNetwork$labels;

    private PackagePortScreenMixin(PackagePortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /**
     * Vanilla has already put the whole stored address into the box by now.
     * Taking the parts from {@code addressFilter} rather than from the box is
     * what keeps a long address intact — the box may have clamped what it was
     * given, and the value it holds is about to be replaced by the path anyway.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void createNestNetwork$addLabelEditor(CallbackInfo ci) {
        String address = menu.contentHolder.addressFilter;
        createNestNetwork$labels = new PortLabelEditor(font, address, getGuiLeft(), getGuiTop(),
            background.getWidth(), background.getHeight(), this::addWidget);
        addressBox.setValue(AddressLabels.path(address));
    }

    @Inject(method = "containerTick", at = @At("TAIL"))
    private void createNestNetwork$tickLabelEditor(CallbackInfo ci) {
        if (createNestNetwork$labels == null)
            return;
        createNestNetwork$labels.tick();
        EditBox pendingFocus = createNestNetwork$labels.takePendingFocus();
        if (pendingFocus != null)
            setFocused(pendingFocus);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void createNestNetwork$closePanelFirst(int keyCode, int scanCode, int modifiers,
        CallbackInfoReturnable<Boolean> cir) {
        if (keyCode == InputConstants.KEY_ESCAPE && createNestNetwork$labels != null
            && createNestNetwork$labels.escape())
            cir.setReturnValue(true);
    }

    @ModifyArg(method = "removed",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/packagePort/PackagePortConfigurationPacket;<init>"
                + "(Lnet/minecraft/core/BlockPos;Ljava/lang/String;Z)V",
            remap = false),
        index = 1)
    private String createNestNetwork$composeAddress(String path) {
        return createNestNetwork$labels == null ? path : createNestNetwork$labels.compose(path);
    }

    @Inject(method = "getExtraAreas", at = @At("RETURN"), cancellable = true, remap = false)
    private void createNestNetwork$reservePanelArea(CallbackInfoReturnable<List<Rect2i>> cir) {
        if (createNestNetwork$labels == null || !createNestNetwork$labels.isOpen())
            return;
        List<Rect2i> areas = new ArrayList<>(cir.getReturnValue());
        areas.add(createNestNetwork$labels.panelArea());
        cir.setReturnValue(areas);
    }

    /**
     * Drawn here rather than as an ordinary widget because the foreground pass
     * is the only one that runs after the menu's own slots.
     */
    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
        if (createNestNetwork$labels == null)
            return;
        createNestNetwork$renderHeaderChip(graphics);
        createNestNetwork$labels.render(graphics, mouseX, mouseY, partialTicks);
    }

    /**
     * The header shows the address box, which no longer carries the label, so
     * the label is spelled out beside it — a port's full address stays legible
     * without opening the panel. Skipped when the address is long enough that
     * the two would collide.
     */
    @Unique
    private void createNestNetwork$renderHeaderChip(GuiGraphics graphics) {
        Component chip = createNestNetwork$labels.headLabelChip();
        if (chip == null)
            return;

        String shown = addressBox.getValue();
        if (shown.isEmpty() && !addressBox.isFocused())
            shown = icon.getHoverName()
                .getString();

        int chipX = getGuiLeft() + 4;
        if (chipX + font.width(chip) + 4 > nameBoxX(shown, addressBox))
            return;

        graphics.drawString(font, chip, chipX, addressBox.getY(), 0x8A87A0, false);
    }

}
