package me.xiaoeyun.createtransit.mixin.client;

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

import me.xiaoeyun.createtransit.client.RouteHost;
import me.xiaoeyun.createtransit.client.RouteScreen;
import me.xiaoeyun.createtransit.client.RouteView;
import me.xiaoeyun.createtransit.content.route.RouteEditSession;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Hangs the route layout on Create's schedule screen.
 *
 * <p>Nothing here decides anything. Every injection is the same two lines —
 * if this screen is a route, hand the call to {@link RouteView} and consume it —
 * and the shadows below exist only to satisfy {@link RouteHost}. The layout, the
 * hit testing and the map all live in ordinary classes that know nothing about
 * mixin, so a Create update is read against this file and no other.
 *
 * <p>Only for a route. A schedule a player wrote keeps the screen they know —
 * this is an addon, and silently redesigning the core interface of the mod it
 * extends is not ours to do. The view is null for an ordinary schedule, and that
 * one null is the whole of the gate.
 *
 * <p>Everything below {@code renderBg}'s early return is Create's instruction
 * editor and is deliberately untouched — it is where every addon's configuration
 * button lives, and those only work because this is really Create's screen.
 */
// remap = false: a Create class, so its names are never obfuscated.
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

    /**
     * The stack this screen was opened on, if it is one of ours — empty if this
     * is a schedule a player wrote.
     *
     * <p>Asked of {@code containerMenu} rather than the screen's own {@code menu}
     * field because that field is protected on a vanilla class in another
     * package; the open menu is the same object either way.
     *
     * <p>The stack rather than a yes or no, because it is also where the route's
     * name and default conditions came in.
     */
    @Unique
    private static ItemStack createTransit$editorStack() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof ScheduleMenu menu
            && RouteEditSession.isEditor(menu.contentHolder))
            return menu.contentHolder;
        return ItemStack.EMPTY;
    }

    /**
     * The only place Create's private state is handed over.
     *
     * <p>Anonymous rather than implemented by the mixin itself, so that none of
     * these seven methods end up on {@code ScheduleScreen} where every other mod
     * would see them.
     */
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

    /**
     * The schedule item drawn three times life size beside the panel.
     *
     * <p>It is a picture of what is being edited, and for a route that is a
     * stack made for the trip which the player does not have and will never see
     * anywhere else. An empty stack renders nothing, which is the whole
     * suppression — the call still happens, it just has nothing to draw.
     */
    @Redirect(method = "renderForeground", at = @At(value = "INVOKE",
        target = "Lnet/createmod/catnip/gui/element/GuiGameElement;of(Lnet/minecraft/world/item/ItemStack;)Lnet/createmod/catnip/gui/element/GuiGameElement$GuiRenderBuilder;"))
    private GuiGameElement.GuiRenderBuilder createTransit$hideHeldSchedule(ItemStack stack) {
        return GuiGameElement.of(createTransit$view == null ? stack : ItemStack.EMPTY);
    }

    /**
     * The card list, replaced by the whole layout.
     *
     * <p>Drawn here rather than where the panel was, because this is the call
     * that is handed the cursor and the frame fraction — and the map wants both.
     * The z-order is the same either way; they are two consecutive statements.
     */
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

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void createTransit$wheel(double mouseX, double mouseY, double delta,
        CallbackInfoReturnable<Boolean> cir) {
        if (createTransit$view != null && createTransit$view.wheel(mouseX, mouseY, delta))
            cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void createTransit$closeConditions(int keyCode, int scanCode, int modifiers,
        CallbackInfoReturnable<Boolean> cir) {
        if (createTransit$view != null && createTransit$view.escape(keyCode))
            cir.setReturnValue(true);
    }

    /**
     * Create's own {@code removed()} always sends {@code ScheduleEditPacket},
     * which would write the route's stops into whatever the player happens to
     * be holding. For a route this redirects that send into our own self-naming
     * save instead; an ordinary schedule's screen is untouched.
     */
    @Redirect(method = "removed", at = @At(value = "INVOKE",
        target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V"))
    private void createTransit$saveRoute(SimpleChannel channel, Object message) {
        if (createTransit$view != null)
            createTransit$view.close();
        else
            channel.sendToServer(message);
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
     * Drops the buttons that only mean something to a train's own schedule.
     *
     * <p>A route is not cyclic — the schedule referencing it is — and progress
     * belongs to each train following it, not to the route. The confirm goes too,
     * though closing still saves: the layout draws its own on the footer beside
     * the way back, so that the two exits are one pair rather than a widget and
     * a glyph that happen to be adjacent.
     */
    @Inject(method = "init", at = @At("TAIL"))
    private void createTransit$dropScheduleControls(CallbackInfo ci) {
        ItemStack stack = createTransit$editorStack();
        if (stack.isEmpty())
            return;

        // Kept across re-inits rather than rebuilt with them: this runs again on
        // a resize and after every stop added or deleted, and a fresh view would
        // send the map back to the player and forget the chosen row each time.
        if (createTransit$view == null)
            createTransit$view = new RouteView(createTransit$host(), stack);

        confirmButton.visible = false;
        cyclicButton.visible = false;
        cyclicIndicator.visible = false;
        resetProgress.visible = false;
        skipProgress.visible = false;
    }

}
