package me.xiaoeyun.createtransit;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.foundation.data.CreateRegistrate;

import me.xiaoeyun.createtransit.content.ticker.TransitTickerConversion;
import me.xiaoeyun.createtransit.network.CtPackets;
import me.xiaoeyun.createtransit.registry.CtBlockEntities;
import me.xiaoeyun.createtransit.registry.CtBlocks;
import me.xiaoeyun.createtransit.registry.CtCreativeTab;
import me.xiaoeyun.createtransit.registry.CtPartialModels;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(CreateTransit.MOD_ID)
public class CreateTransit {

    public static final String MOD_ID = "create_transit";
    public static final String NAME = "Create: Transit";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID)
        .setCreativeTab(AllCreativeModeTabs.BASE_CREATIVE_TAB);

    public CreateTransit(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        REGISTRATE.registerEventListeners(modEventBus);

        CtBlocks.register();
        CtBlockEntities.register();
        // Partial models must exist before the first model bake collects them,
        // which the constructor comfortably precedes and a setup event may not.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> CtPartialModels::init);

        modEventBus.addListener(CreateTransit::commonSetup);
        // Create's creative tab is filled from Create's own registrate, which
        // never sees an addon's entries, so our blocks have to add themselves.
        modEventBus.addListener(CtCreativeTab::onBuildContents);
        // Any logistics link placed against a tuned Stock Ticker converts it,
        // vanilla Stock Links included, so this cannot live on our own block.
        //
        // LOWEST matters: claim and protection mods work by cancelling this
        // event, and listeners at the same priority run in registration order.
        // Anywhere earlier we could convert a ticker whose placement is about
        // to be rolled back, leaving the block destroyed and its contents on
        // the floor. Last in line, a cancelled placement never reaches us.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, TransitTickerConversion::onLinkPlaced);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CtPackets::register);
    }

    public static CreateRegistrate registrate() {
        return REGISTRATE;
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
