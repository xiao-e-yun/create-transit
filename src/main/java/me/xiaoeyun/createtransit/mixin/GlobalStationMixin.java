package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;

import me.xiaoeyun.createtransit.content.freight.TransitTrain;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Keeps the two posts out of each other's wagons by refusing the load at {@code runMailTransfer}, since that's the only point a package's lane is known. */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = GlobalStation.class, remap = false)
public abstract class GlobalStationMixin {

    @Shadow
    public abstract Train getPresentTrain();

    @Redirect(method = "runMailTransfer",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/items/ItemHandlerHelper;insertItemStacked(Lnet/minecraftforge/items/IItemHandler;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
            ordinal = 0),
        remap = false)
    private ItemStack createTransit$loadOwnLaneOnly(IItemHandler carriage, ItemStack stack, boolean simulate) {
        Train train = getPresentTrain();
        // Returning the stack unchanged is Create's own "would not fit" signal, the same path a full train takes.
        if (train != null && !TransitTrain.carries(train, stack))
            return stack;
        return ItemHandlerHelper.insertItemStacked(carriage, stack, simulate);
    }

}
