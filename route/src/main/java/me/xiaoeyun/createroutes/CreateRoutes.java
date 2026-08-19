package me.xiaoeyun.createroutes;

import com.mojang.logging.LogUtils;

import me.xiaoeyun.createroutes.content.route.RouteStore;
import me.xiaoeyun.createroutes.network.CrPackets;
import me.xiaoeyun.createroutes.registry.CrDataComponents;
import me.xiaoeyun.createroutes.registry.CrSchedule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

@Mod(CreateRoutes.MOD_ID)
public class CreateRoutes {

    public static final String MOD_ID = "create_routes";
    public static final String NAME = "Create: Routes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateRoutes(IEventBus modEventBus) {
        CrDataComponents.register(modEventBus);
        modEventBus.addListener(CrPackets::onRegisterPayloads);
        modEventBus.addListener(CreateRoutes::commonSetup);
        NeoForge.EVENT_BUS.addListener(CreateRoutes::onPlayerJoin);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        // Schedule types are looked up by id from NBT, so they must be in Create's table before any world loads.
        event.enqueueWork(CrSchedule::register);
    }

    /** A joining client has no idea what routes exist until it is told. */
    private static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            RouteStore.syncNamesTo(player);
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
