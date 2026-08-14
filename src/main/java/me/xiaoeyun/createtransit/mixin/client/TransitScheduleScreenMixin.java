package me.xiaoeyun.createtransit.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleMenu;
import com.simibubi.create.content.trains.schedule.ScheduleScreen;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;

import me.xiaoeyun.createtransit.content.freight.TransitTrain;
import me.xiaoeyun.createtransit.content.route.RouteEditSession;
import me.xiaoeyun.createtransit.registry.CtItems;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Adds the postal lane toggle to Create's schedule screen.
 *
 * <p>Which post a train runs is that train's business, not a route's — the
 * flag lives on the schedule holding a route reference, not on the route
 * itself — so the button belongs beside every other train-specific control
 * on this screen, and is hidden while a route itself is open for editing.
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
public abstract class TransitScheduleScreenMixin extends AbstractSimiContainerScreen<ScheduleMenu> {

    private TransitScheduleScreenMixin(ScheduleMenu menu, Inventory inventory, Component title) {
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

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void createTransit$addTransitTrainButton(CallbackInfo ci) {
        // Not on a route. A route is a run of stops that any number of trains
        // may follow, and which post a train runs is that train's business —
        // the flag lives on the schedule holding the reference, not on the
        // thing referenced. Asked of the menu rather than of the route mixin's
        // own state, because this mixin owns no assumption about when that one
        // runs relative to this one: the menu already knows whether this is a
        // route without needing to ask it.
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
