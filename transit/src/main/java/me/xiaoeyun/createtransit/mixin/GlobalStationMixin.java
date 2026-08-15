package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;

import me.xiaoeyun.createtransit.content.dispatch.TransitDispatch;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Asks the dispatcher before loading, refused at {@code runMailTransfer} since that's the only point train, station and stack meet. */
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
    private ItemStack createTransit$loadDispatchedOnly(IItemHandler carriage, ItemStack stack, boolean simulate) {
        Train train = getPresentTrain();
        // Returning the stack unchanged is Create's own "would not fit" signal, the same path a full train takes.
        if (train != null && !TransitDispatch.mayLoad(train, (GlobalStation) (Object) this, stack))
            return stack;
        return ItemHandlerHelper.insertItemStacked(carriage, stack, simulate);
    }

    @Inject(method = "trainDeparted", at = @At("TAIL"), remap = false)
    private void createTransit$bayFreed(Train train, CallbackInfo ci) {
        TransitDispatch.bayFreed((GlobalStation) (Object) this, train);
    }

}
