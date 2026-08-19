package me.xiaoeyun.createtransit;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;

import me.xiaoeyun.createtransit.client.ponder.CtPonderPlugin;
import me.xiaoeyun.createtransit.content.dispatch.TimetableConductorInteraction;
import me.xiaoeyun.createtransit.network.CtPackets;
import me.xiaoeyun.createtransit.registry.CtBlockEntities;
import me.xiaoeyun.createtransit.registry.CtBlocks;
import me.xiaoeyun.createtransit.registry.CtDataComponents;
import me.xiaoeyun.createtransit.registry.CtItems;
import me.xiaoeyun.createtransit.registry.CtCreativeTab;
import me.xiaoeyun.createtransit.registry.CtPartialModels;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
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
        // Registrate 1.21 defaults every item into the SEARCH tab and inserts
        // it itself; our curated CtCreativeTab insertion then collides with it
        // when the search tab rebuilds. Null is Create's own answer upstream:
        // it turns the automatic path off so exactly one inserter remains.
        .defaultCreativeTab((ResourceKey<CreativeModeTab>) null)
        .setCreativeTab(AllCreativeModeTabs.BASE_CREATIVE_TAB)
        .setTooltipModifierFactory(item -> new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));

    public CreateTransit(IEventBus modEventBus) {
        REGISTRATE.registerEventListeners(modEventBus);

        CtDataComponents.register(modEventBus);
        CtBlocks.register();
        CtBlockEntities.register();
        CtItems.register();
        // Partial models must exist before the first model bake collects them,
        // which the constructor comfortably precedes and a setup event may not.
        // A plain dist check replaces DistExecutor: the classes only load when
        // the branch actually runs, which is all the old dance guaranteed.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CtPartialModels.init();
            modEventBus.addListener(CtPonderPlugin::register);
        }

        modEventBus.addListener(CtPackets::onRegisterPayloads);
        // Create's creative tab is filled from Create's own registrate, which
        // never sees an addon's entries, so our blocks have to add themselves.
        modEventBus.addListener(CtCreativeTab::onBuildContents);
        NeoForge.EVENT_BUS.addListener(TimetableConductorInteraction::interactWithConductor);
    }

    public static CreateRegistrate registrate() {
        return REGISTRATE;
    }

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
