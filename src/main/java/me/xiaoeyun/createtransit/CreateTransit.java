package me.xiaoeyun.createtransit;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;

import me.xiaoeyun.createtransit.network.CtPackets;
import me.xiaoeyun.createtransit.registry.CtBlockEntities;
import me.xiaoeyun.createtransit.registry.CtBlocks;
import me.xiaoeyun.createtransit.registry.CtItems;
import me.xiaoeyun.createtransit.registry.CtCreativeTab;
import me.xiaoeyun.createtransit.registry.CtPartialModels;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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

    // Registrate attaches this to every item we register, and Create's own
    // tooltip event serves whatever is in the registry; an item with no
    // ".tooltip.summary" key simply gets nothing.
    private static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MOD_ID)
        .setCreativeTab(AllCreativeModeTabs.BASE_CREATIVE_TAB)
        .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));

    public CreateTransit(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        REGISTRATE.registerEventListeners(modEventBus);

        CtBlocks.register();
        CtBlockEntities.register();
        CtItems.register();
        // Partial models must exist before the first model bake collects them,
        // which the constructor comfortably precedes and a setup event may not.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> CtPartialModels::init);

        modEventBus.addListener(CreateTransit::commonSetup);
        // Create's creative tab is filled from Create's own registrate, which
        // never sees an addon's entries, so our blocks have to add themselves.
        modEventBus.addListener(CtCreativeTab::onBuildContents);
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
