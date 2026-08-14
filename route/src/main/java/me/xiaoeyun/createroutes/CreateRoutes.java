package me.xiaoeyun.createroutes;

import com.mojang.logging.LogUtils;

import me.xiaoeyun.createroutes.content.route.RouteStore;
import me.xiaoeyun.createroutes.network.CrPackets;
import me.xiaoeyun.createroutes.registry.CrSchedule;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CreateRoutes.MOD_ID)
public class CreateRoutes {

    public static final String MOD_ID = "create_routes";
    public static final String NAME = "Create: Routes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CreateRoutes(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(CreateRoutes::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(CreateRoutes::onPlayerJoin);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CrPackets::register);
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
