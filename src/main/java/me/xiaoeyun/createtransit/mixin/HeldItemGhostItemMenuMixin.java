package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.foundation.gui.menu.HeldItemGhostItemMenu;
import com.simibubi.create.foundation.gui.menu.MenuBase;

import me.xiaoeyun.createtransit.content.route.RouteEditSession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps a route's borrowed schedule screen open.
 *
 * <p>The menu this guards is meant for an item the player is holding, so it
 * stays valid only while that exact stack is the selected one:
 *
 * <pre>return playerInventory.getSelected() == contentHolder;</pre>
 *
 * <p>That is instance equality against a hotbar slot. A route is edited on a
 * stack that was made for the trip and never given to anyone, so it is in no
 * inventory and the answer is always no — the server would close the screen on
 * the tick after it opened. Answering yes for that one case is the whole change;
 * every schedule a player actually holds still gets Create's own check.
 */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = HeldItemGhostItemMenu.class, remap = false)
public class HeldItemGhostItemMenuMixin {

    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void createTransit$keepRouteEditorOpen(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (((MenuBase<?>) (Object) this).contentHolder instanceof ItemStack stack
            && RouteEditSession.isEditor(stack))
            cir.setReturnValue(true);
    }

}
