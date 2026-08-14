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
 * Keeps a route's borrowed schedule screen open: Create's own check is instance equality against a hotbar slot
 * — {@code return playerInventory.getSelected() == contentHolder;} — and a route's stack is in no inventory.
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
