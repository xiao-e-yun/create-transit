package me.xiaoeyun.createtransit.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.FetchPackagesInstruction;

import me.xiaoeyun.createtransit.content.freight.TransitTrain;
import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Stops Create's retrieval from setting out for freight in the other lane, by narrowing what {@code isPackage} counts as freight for this train. */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = FetchPackagesInstruction.class, remap = false)
public class FetchPackagesInstructionMixin {

    // Appended parameters are how a @Redirect reaches the runtime the enclosing method doesn't otherwise pass down.
    @Redirect(method = "start",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/box/PackageItem;isPackage(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean createTransit$ownLaneOnly(ItemStack stack, ScheduleRuntime runtime, Level level) {
        return PackageItem.isPackage(stack) && TransitTrain.carries(runtime.train, stack);
    }

    /** On a transit train the address field is a border gate's name, matched by regex — see {@link AddressLabels#headLabelRegex}. */
    @Redirect(method = "start",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/trains/schedule/destination/FetchPackagesInstruction;getFilterForRegex()Ljava/lang/String;"))
    private String createTransit$gateFilter(FetchPackagesInstruction self, ScheduleRuntime runtime,
        Level level) {
        if (!TransitTrain.of(runtime.schedule))
            return self.getFilterForRegex();
        return AddressLabels.headLabelRegex(self.getFilter());
    }

}
