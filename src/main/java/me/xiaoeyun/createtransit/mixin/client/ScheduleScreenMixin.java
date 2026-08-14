package me.xiaoeyun.createtransit.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;

import me.xiaoeyun.createtransit.content.freight.TransitTrain;
import me.xiaoeyun.createtransit.content.route.FollowRouteInstruction;
import me.xiaoeyun.createtransit.content.route.RouteEditSession;
import me.xiaoeyun.createtransit.registry.CtItems;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps a route follower's borrowed conditions out of the schedule editor.
 *
 * <p>A follower has to write the current stop's conditions onto its own entry,
 * because the runtime waits on the conditions of the entry it is sitting on and
 * nowhere else. Those conditions belong to the route, not to this piece of
 * paper: the player never set them, editing them achieves nothing, and the next
 * stop overwrites whatever they typed. An editable control that silently
 * reverts is worse than no control.
 *
 * <p>{@code supportsConditions} is the single fact the whole card is laid out
 * from — its height, its header texture, which strip icon it wears, whether the
 * wait row is drawn, and the height the click test walks past. Answering it
 * once, here, turns a follower into an action card exactly like a throttle
 * change, with every one of those consequences following on their own.
 *
 * <p>Only the screen is redirected. The instruction still reports true to
 * everything else, because there the answer is genuinely yes — it does wait,
 * and its conditions must still be written to disk.
 *
 * <p>There was a second injection here, replacing Create's station autocomplete
 * with a list of routes. It never ran: those suggestions are attached to an
 * {@code EditBox} found among the editor's sub-widgets, and a follower builds a
 * picker and a button instead — the route is chosen, not typed. Which routes may
 * be chosen is decided where that picker is built.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = ScheduleScreen.class, remap = false)
// Extending the real superclass rather than shadowing what it holds. leftPos,
// topPos and addRenderableWidget are Minecraft's, and a @Shadow of an inherited
// member is one the annotation processor cannot find in the target class — it
// compiles with a warning and then has no obfuscation mapping to resolve with
// outside a dev workspace. Inherited normally they are plain references, which
// reobf remaps like any other call. The constructor exists only to satisfy
// javac; nothing ever builds this class.
public abstract class ScheduleScreenMixin extends AbstractSimiContainerScreen<ScheduleMenu> {

    private ScheduleScreenMixin(ScheduleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private static final String LANG_TRANSIT = "create_transit.schedule.transit_train";
    private static final String LANG_TRANSIT_OFF = "create_transit.schedule.transit_train.off";
    private static final String LANG_TRANSIT_ON = "create_transit.schedule.transit_train.on";

    /**
     * At the right end of the footer, before the tick at 214.
     *
     * <p>It sat beside the cyclic button at 81 first, with the three that say
     * something about the schedule as a whole. Wrong company: those three are
     * about running the schedule — loop it, rewind it, skip a stop — where this
     * one says what the train is for. Next to the tick it reads as the last
     * thing decided before the paper is handed over, and the empty half of the
     * footer stops looking like a mistake.
     *
     * <p>The 29 is the transit link's own, where its reset sits 29 from its
     * confirm. Same pair of roles, same gap, so two screens of this mod space
     * a button off a tick the same way.
     */
    @Unique
    private static final int CREATE_TRANSIT$BUTTON_X = 214 - 29;

    @Unique
    private static final int CREATE_TRANSIT$BUTTON_Y = 196;

    @Shadow
    private Schedule schedule;

    @Unique
    private IconButton createTransit$transitTrain;

    @Redirect(method = { "renderSchedule", "renderScheduleEntry", "action" },
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/schedule/destination/ScheduleInstruction;supportsConditions()Z"))
    private boolean createTransit$hideBorrowedConditions(ScheduleInstruction instruction) {
        return instruction.supportsConditions() && !(instruction instanceof FollowRouteInstruction);
    }

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void createTransit$addTransitTrainButton(CallbackInfo ci) {
        // Not on a route. A route is a run of stops that any number of trains
        // may follow, and which post a train runs is that train's business —
        // the flag lives on the schedule holding the reference, not on the
        // thing referenced. Asked of the menu rather than of the view this
        // screen grows, because that view is built by another mixin's injection
        // into this same method and nothing decides which of the two runs first.
        if (RouteEditSession.isEditor(menu.contentHolder))
            return;

        // The box itself rather than a glyph from the atlas: it is the thing the
        // lane is named after, it is what the transit link's screen already
        // draws to mean the same word, and nothing in Create's sheet means
        // "still abroad".
        ItemStack box = CtItems.TRANSIT_PACKAGES.get(0)
            .asStack();
        ScreenElement icon = (g, x, y) -> g.renderItem(box, x, y);

        createTransit$transitTrain =
            new IconButton(leftPos + CREATE_TRANSIT$BUTTON_X, topPos + CREATE_TRANSIT$BUTTON_Y, icon);
        createTransit$transitTrainTip();
        createTransit$transitTrain.withCallback(() -> {
            TransitTrain flag = (TransitTrain) schedule;
            flag.createTransit$setTransitTrain(!flag.createTransit$isTransitTrain());
            createTransit$transitTrainTip();
        });
        addRenderableWidget(createTransit$transitTrain);
    }

    /** Green while on, the way Create's own cyclic button says the same thing. */
    @Unique
    private void createTransit$transitTrainTip() {
        boolean on = TransitTrain.of(schedule);
        createTransit$transitTrain.green = on;
        createTransit$transitTrain.getToolTip()
            .clear();
        createTransit$transitTrain.getToolTip()
            .add(Component.translatable(LANG_TRANSIT));
        createTransit$transitTrain.getToolTip()
            .add(Component.translatable(on ? LANG_TRANSIT_ON : LANG_TRANSIT_OFF));
    }

    // HEAD and not TAIL: Create sets these flags at the top of both methods, and
    // stopEditing returns early below that when nothing was being edited.
    @Inject(method = "startEditing", at = @At("HEAD"))
    private void createTransit$hideTransitTrain(CallbackInfo ci) {
        if (createTransit$transitTrain != null)
            createTransit$transitTrain.visible = false;
    }

    @Inject(method = "stopEditing", at = @At("HEAD"))
    private void createTransit$showTransitTrain(CallbackInfo ci) {
        if (createTransit$transitTrain != null)
            createTransit$transitTrain.visible = true;
    }

}
