package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.schedule.destination.FetchPackagesInstruction;

import me.xiaoeyun.createtransit.content.transit.TransitPackageItem;
import net.minecraft.world.item.ItemStack;

/**
 * Transit mail is not a fetch target: {@code TransitDispatch.mayLoad} refuses it to any train
 * off the timetable, so counting it here sent such trains to a postbox they always left empty.
 */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = FetchPackagesInstruction.class, remap = false)
public abstract class FetchPackagesInstructionMixin {

    @Redirect(method = "start",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/box/PackageItem;isPackage(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean createTransit$skipTransitMail(ItemStack stack) {
        return PackageItem.isPackage(stack) && !(stack.getItem() instanceof TransitPackageItem);
    }

}
