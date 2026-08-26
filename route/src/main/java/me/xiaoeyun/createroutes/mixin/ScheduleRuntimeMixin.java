package me.xiaoeyun.createroutes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;

import me.xiaoeyun.createroutes.content.route.RouteExpansion;
import me.xiaoeyun.createroutes.content.route.RouteStore;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Keeps a running train's schedule holding the stops its routes currently name, and takes them back out before
 * Create hands the schedule to a player. Between the two hooks it is ordinary entries all the way down.
 */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = ScheduleRuntime.class, remap = false)
public abstract class ScheduleRuntimeMixin {

    @Shadow
    public Train train;

    @Shadow
    public Schedule schedule;

    @Shadow
    public int currentEntry;

    @Shadow
    public boolean isAutoSchedule;

    @Shadow
    public abstract void setSchedule(Schedule schedule, boolean auto);

    /**
     * Rebuilds stale blocks at departure, when Create's position-indexed condition bookkeeping is spent.
     *
     * <p>Create also calls this mid-leg to re-plan a route it is already flying, from {@code
     * Train.updateNavigationTarget} and {@code StationBlockEntity.enterAssemblyMode}; swapping the schedule
     * under either would retarget the train between stations, so only the standing case is taken.
     */
    @Inject(method = "startCurrentInstruction", at = @At("HEAD"))
    private void createTransit$refreshRoutes(Level level, CallbackInfoReturnable<DiscoveredPath> cir) {
        MinecraftServer server = level.getServer();
        if (server == null || schedule == null || train.navigation.destination != null)
            return;

        RouteStore store = RouteStore.get(server);
        if (!RouteExpansion.isStale(schedule.entries, store))
            return;

        // Through setSchedule rather than by assigning the field: shrinking a schedule silently leaves readers
        // holding entry indices that no longer exist, and this is the call they all watch for.
        RouteExpansion.Expansion rebuilt =
            RouteExpansion.expand(level.registryAccess(), schedule, store, currentEntry);
        setSchedule(rebuilt.schedule(), isAutoSchedule);
        currentEntry = rebuilt.currentEntry();
    }

    /** Hands back what the player wrote, not what the train was running. */
    @Inject(method = "returnSchedule", at = @At("HEAD"))
    private void createTransit$collapseRoutes(HolderLookup.Provider registries,
        CallbackInfoReturnable<ItemStack> cir) {
        // Set before Create reads it into savedProgress, so a returned schedule points at an entry that exists.
        if (schedule != null)
            currentEntry = RouteExpansion.collapse(schedule.entries, currentEntry);
    }

}
