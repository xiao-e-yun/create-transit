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

/**
 * Stops Create's retrieval setting out for freight in the other lane.
 *
 * <p>It scans every postbox in the world for a package matching its address
 * filter and drives to the nearest station that has one. Nothing in that scan
 * knows which post the train runs, so a train can pick a station whose only
 * cargo the station will refuse to hand over — and then arrive, load nothing,
 * and pick the very same station again next time round, because nothing there
 * has changed.
 *
 * <p>Both directions, and one of them is live today: an ordinary train sent to
 * a station holding only packages in transit. The filter is not where this can
 * be fixed — an address pattern cannot express a lane, and the player has no
 * business writing one that could.
 *
 * <p>So the scan is narrowed at its first question instead. {@code isPackage} is
 * asked once per slot and answers "is this freight at all", which is exactly the
 * question that was too broad: freight for somebody else was never freight for
 * this train.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = FetchPackagesInstruction.class, remap = false)
public class FetchPackagesInstructionMixin {

    // The enclosing method's own parameters, appended, which is how a redirect
    // reaches the runtime the scan never passes down.
    @Redirect(method = "start",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/box/PackageItem;isPackage(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean createTransit$ownLaneOnly(ItemStack stack, ScheduleRuntime runtime, Level level) {
        return PackageItem.isPackage(stack) && TransitTrain.carries(runtime.train, stack);
    }

    /**
     * On a transit train the address field is a border gate's name.
     *
     * <p>Which post a train runs decides how every address field on its schedule
     * reads, or it decides nothing: a player typing a gate's name into this box
     * and the identical box one card down has every right to expect the same
     * thing to happen. Our own round already reads it that way, so Create's
     * reads it that way too, and the rule belongs to the lane rather than to
     * either instruction.
     *
     * <p>A pattern and not {@code matchAddress}, because this is the one place
     * the address is compared by regex — see {@link AddressLabels#headLabelRegex}.
     */
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
