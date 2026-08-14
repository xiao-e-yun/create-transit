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
import me.xiaoeyun.createroutes.content.route.RouteEditSession;
import me.xiaoeyun.createtransit.registry.CtItems;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Adds the postal lane toggle to Create's schedule screen; hidden while a route itself is open for editing. */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = ScheduleScreen.class, remap = false)
// Extends the real superclass rather than shadowing it: a @Shadow of an inherited member has no obfuscation
// mapping outside a dev workspace, while extending normally it remaps like any other reference. The
// constructor exists only to satisfy javac; nothing ever builds this class.
public abstract class TransitScheduleScreenMixin extends AbstractSimiContainerScreen<ScheduleMenu> {

    private TransitScheduleScreenMixin(ScheduleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private static final String LANG_TRANSIT = "create_transit.schedule.transit_train";
    private static final String LANG_TRANSIT_OFF = "create_transit.schedule.transit_train.off";
    private static final String LANG_TRANSIT_ON = "create_transit.schedule.transit_train.on";

    /** Right end of the footer, before the tick at 214; the 29px gap matches the transit link screen's own reset-to-confirm spacing. */
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
        // Not on a route — the transit flag lives on the schedule holding the reference, not on the route itself.
        if (RouteEditSession.isEditor(menu.contentHolder))
            return;

        // The box itself, not an atlas glyph — nothing in Create's sheet means "still abroad".
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

    // HEAD, not TAIL: Create sets these flags at the top of both methods, and stopEditing returns early otherwise.
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
