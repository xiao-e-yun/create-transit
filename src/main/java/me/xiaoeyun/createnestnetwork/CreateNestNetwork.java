package me.xiaoeyun.createnestnetwork;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.foundation.data.CreateRegistrate;

import me.xiaoeyun.createnestnetwork.content.proxy.StockProxyerConversion;
import me.xiaoeyun.createnestnetwork.network.CnnPackets;
import me.xiaoeyun.createnestnetwork.registry.CnnBlockEntities;
import me.xiaoeyun.createnestnetwork.registry.CnnBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CreateNestNetwork.MOD_ID)
public class CreateNestNetwork {

    public static final String MOD_ID = "create_nest_network";
    public static final String NAME = "Create: Nest Network";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID)
        .setCreativeTab(AllCreativeModeTabs.BASE_CREATIVE_TAB);

    public CreateNestNetwork(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        REGISTRATE.registerEventListeners(modEventBus);

        CnnBlocks.register();
        CnnBlockEntities.register();

        modEventBus.addListener(CreateNestNetwork::commonSetup);
        // Any logistics link placed against a tuned Stock Ticker converts it,
        // vanilla Stock Links included, so this cannot live on our own block.
        //
        // LOWEST matters: claim and protection mods work by cancelling this
        // event, and listeners at the same priority run in registration order.
        // Anywhere earlier we could convert a ticker whose placement is about
        // to be rolled back, leaving the block destroyed and its contents on
        // the floor. Last in line, a cancelled placement never reaches us.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, StockProxyerConversion::onLinkPlaced);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CnnPackets::register);
    }

    public static CreateRegistrate registrate() {
        return REGISTRATE;
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
