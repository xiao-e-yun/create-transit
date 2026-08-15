package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.trains.station.GlobalStation;

import me.xiaoeyun.createtransit.content.transit.TransitPackageItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Transit packages only board a train the dispatcher sent for them, refused at {@code runMailTransfer} since that's the only point the load is known. */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = GlobalStation.class, remap = false)
public abstract class GlobalStationMixin {

    @Redirect(method = "runMailTransfer",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/items/ItemHandlerHelper;insertItemStacked(Lnet/minecraftforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
            ordinal = 0),
        remap = false)
    private ItemStack createTransit$loadDispatchedOnly(IItemHandler carriage, ItemStack stack, boolean simulate) {
        // Returning the stack unchanged is Create's own "would not fit" signal, the same path a full train takes.
        if (stack.getItem() instanceof TransitPackageItem)
            return stack;
        return ItemHandlerHelper.insertItemStacked(carriage, stack, simulate);
    }

}
