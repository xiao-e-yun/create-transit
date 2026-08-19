package me.xiaoeyun.createroutes.mixin.client;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Indicator;

import me.xiaoeyun.createroutes.client.RouteHost;
import me.xiaoeyun.createroutes.client.RouteScreen;
import me.xiaoeyun.createroutes.client.RouteView;
import me.xiaoeyun.createroutes.content.route.FollowRouteInstruction;
import me.xiaoeyun.createroutes.content.route.RouteEditSession;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.createmod.catnip.platform.services.NetworkHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Hangs the route layout on Create's schedule screen. Every injection but {@code createTransit$hideBorrowedConditions} does nothing unless a {@link RouteView} is up. */
// remap = false: NeoForge runs on official mappings, so nothing here is ever obfuscated -- the
// members inherited from Minecraft included. The Forge branch had to opt those back in, because
// production carried SRG names there and these ones alone would have needed translating.
@Mixin(value = ScheduleScreen.class, remap = false)
public abstract class ScheduleScreenTableMixin implements RouteScreen {

    @Shadow
    private Schedule schedule;

    @Shadow
    private ScheduleInstruction editingDestination;

    @Shadow
    private ScheduleWaitCondition editingCondition;

    @Shadow
    protected abstract void startEditing(IScheduleInput field, Consumer<Boolean> onClose,
        boolean allowDeletion);

    @Shadow
    protected abstract void init();

    @Shadow
    private IconButton confirmButton;

    @Shadow
    private IconButton cyclicButton;

    @Shadow
    private Indicator cyclicIndicator;

    @Shadow
    private IconButton resetProgress;

    @Shadow
    private IconButton skipProgress;

    /** The route layout, or null when this is a schedule a player wrote. */
    @Unique
    private RouteView createTransit$view;

    /** The stack this screen was opened on, if it's one of ours, else empty; read off {@code containerMenu} since {@code menu} is protected on a vanilla class in another package. */
    @Unique
    private static ItemStack createTransit$editorStack() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof ScheduleMenu menu
            && RouteEditSession.isEditor(menu.contentHolder))
            return menu.contentHolder;
        return ItemStack.EMPTY;
    }

    /** Anonymous, so these methods do not land on {@code ScheduleScreen} itself the way an implemented interface would. */
    @Unique
    private RouteHost createTransit$host() {
        return new RouteHost() {

            @Override
            public Screen screen() {
                return (Screen) (Object) ScheduleScreenTableMixin.this;
            }

            @Override
            public List<ScheduleEntry> entries() {
                return schedule.entries;
            }

            @Override
            public boolean editorOpen() {
                return editingCondition != null || editingDestination != null;
            }

            @Override
            public void startEditing(IScheduleInput field, Consumer<Boolean> onClose,
                boolean allowDeletion) {
                ScheduleScreenTableMixin.this.startEditing(field, onClose, allowDeletion);
            }

            @Override
            public ScheduleInstruction editedInstruction() {
                return editingDestination;
            }

            @Override
            public ScheduleWaitCondition editedCondition() {
                return editingCondition;
            }

            @Override
            public void rebuild() {
                init();
            }
        };
    }

    /** The panel texture, which a layout using the whole screen has no use for. */
    @Redirect(method = "renderBg", at = @At(value = "INVOKE", ordinal = 0,
        target = "Lcom/simibubi/create/foundation/gui/AllGuiTextures;render(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void createTransit$panel(AllGuiTextures texture, GuiGraphics graphics, int x, int y) {
        if (createTransit$view == null)
            texture.render(graphics, x, y);
    }

    /** The schedule item drawn beside the panel; an empty stack renders nothing, which is the whole suppression. */
    @Redirect(method = "renderForeground", at = @At(value = "INVOKE",
        target = "Lnet/createmod/catnip/gui/element/GuiGameElement;of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/catnip/gui/element/GuiGameElement$GuiRenderBuilder;"))
    private GuiGameElement.GuiRenderBuilder createTransit$hideHeldSchedule(ItemStack stack) {
        return GuiGameElement.of(createTransit$view == null ? stack : ItemStack.EMPTY);
    }

    /** The card list, replaced by the whole layout; drawn here since this is the call handed the cursor and frame fraction the map needs. */
    @Inject(method = "renderSchedule", at = @At("HEAD"), cancellable = true)
    private void createTransit$list(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
        CallbackInfo ci) {
        if (createTransit$view == null)
            return;
        createTransit$view.render(graphics, mouseX, mouseY, partialTicks);
        ci.cancel();
    }

    @Inject(method = "action", at = @At("HEAD"), cancellable = true)
    private void createTransit$tableAction(GuiGraphics graphics, double mouseX, double mouseY, int click,
        CallbackInfoReturnable<Boolean> cir) {
        if (createTransit$view != null)
            cir.setReturnValue(createTransit$view.action(graphics, mouseX, mouseY, click));
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void createTransit$grabMap(double mouseX, double mouseY, int button,
        CallbackInfoReturnable<Boolean> cir) {
        if (createTransit$view != null && createTransit$view.grab(mouseX, mouseY, button))
            cir.setReturnValue(true);
    }

    // 1.21 splits the wheel in two; the view only ever read the vertical one.
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void createTransit$wheel(double mouseX, double mouseY, double scrollX, double scrollY,
        CallbackInfoReturnable<Boolean> cir) {
        if (createTransit$view != null && createTransit$view.wheel(mouseX, mouseY, scrollY))
            cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void createTransit$closeConditions(int keyCode, int scanCode, int modifiers,
        CallbackInfoReturnable<Boolean> cir) {
        if (createTransit$view != null && createTransit$view.escape(keyCode))
            cir.setReturnValue(true);
    }

    /** Create's own {@code removed()} always sends {@code ScheduleEditPacket}, which would write the route's stops into whatever the player is holding; redirected to our own save for a route. */
    @Redirect(method = "removed", at = @At(value = "INVOKE",
        target = "Lnet/createmod/catnip/platform/services/NetworkHelper;"
            + "sendToServer(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
    private void createTransit$saveRoute(NetworkHelper network, CustomPacketPayload message) {
        if (createTransit$view != null)
            createTransit$view.close();
        else
            network.sendToServer(message);
    }

    @Inject(method = "getExtraAreas", at = @At("HEAD"), cancellable = true)
    private void createTransit$claimScreen(CallbackInfoReturnable<List<Rect2i>> cir) {
        if (createTransit$view != null)
            cir.setReturnValue(createTransit$view.areas());
    }

    @Override
    public UUID editingRoute() {
        return createTransit$view == null ? null : createTransit$view.route();
    }

    /**
     * Hides a route follower's borrowed conditions in any schedule screen, a train's own included: the card
     * layout is driven by that one flag. The instruction still reports true everywhere else, since it does
     * wait and its conditions must still reach disk.
     */
    @Redirect(method = { "renderSchedule", "renderScheduleEntry", "action" },
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/schedule/destination/ScheduleInstruction;supportsConditions()Z"))
    private boolean createTransit$hideBorrowedConditions(ScheduleInstruction instruction) {
        return instruction.supportsConditions() && !(instruction instanceof FollowRouteInstruction);
    }

    /** Drops the buttons that only mean something to a train's own schedule — a route is not cyclic, and progress belongs to each train following it. */
    @Inject(method = "init", at = @At("TAIL"))
    private void createTransit$dropScheduleControls(CallbackInfo ci) {
        ItemStack stack = createTransit$editorStack();
        if (stack.isEmpty())
            return;

        // Kept across re-inits — this runs again on resize and every stop change, and a fresh view would
        // reset the map and forget the chosen row.
        if (createTransit$view == null)
            createTransit$view = new RouteView(createTransit$host(), stack);

        confirmButton.visible = false;
        cyclicButton.visible = false;
        cyclicIndicator.visible = false;
        resetProgress.visible = false;
        skipProgress.visible = false;
    }

}
