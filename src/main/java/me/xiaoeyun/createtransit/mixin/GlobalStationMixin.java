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

/**
 * Keeps the two posts out of each other's wagons.
 *
 * <p>{@code runMailTransfer} loads every package a station holds into whatever
 * train is standing there, asking nothing about why it came — that is the whole
 * of Create's postal system, and the instructions only ever choose where to go.
 * So the only place a train can be told a package is not its business is the
 * moment the package is handed over.
 *
 * <p>Refusing is done by handing the stack back. Vanilla already reads a
 * non-empty result as "it would not fit" and leaves the package where it is, so
 * a lane that does not want a box takes the same path a full train does, and
 * nothing downstream has to learn a new outcome.
 *
 * <p>Only the loading call is touched — the first of the two, the export back
 * to a station being the second. Filtering what a train may put down would
 * strand whatever it was already carrying.
 *
 * <p>One inherited wart: Create fires its computer event before checking the
 * result, so a refused package still reports {@code package_sent}. That is true
 * of a full train today, so it is Create's answer to the question rather than
 * one this introduces.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
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
        if (train != null && !TransitTrain.carries(train, stack))
            return stack;
        return ItemHandlerHelper.insertItemStacked(carriage, stack, simulate);
    }

}
