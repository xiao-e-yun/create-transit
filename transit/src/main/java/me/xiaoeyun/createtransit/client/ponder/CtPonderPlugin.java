package me.xiaoeyun.createtransit.client.ponder;

import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createtransit.registry.CtBlocks;
import me.xiaoeyun.createtransit.registry.CtItems;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Ponder scenes for the three border blocks.
 *
 * Scene ids are namespaced to this mod by Ponder itself, so they cannot collide
 * with Create's. A scene id and the schematic it loads are unrelated, and the
 * gate uses that: its two chapters carry different ids and load different
 * dioramas, because merging needs a network that packs a request in two places
 * and crossing needs three gates in a row.
 */
public class CtPonderPlugin implements PonderPlugin {

    private static final ResourceLocation TRANSIT = CreateTransit.asResource("transit");

    public static void register(FMLClientSetupEvent event) {
        PonderIndex.addPlugin(new CtPonderPlugin());
    }

    @Override
    public String getModId() {
        return CreateTransit.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemProviderEntry<?>> scenes = helper.withKeyFunction(RegistryEntry::getId);

        scenes.forComponents(CtBlocks.TRANSIT_LINK)
            .addStoryBoard("transit_link", TransitLinkScenes::transitLink, TRANSIT);

        scenes.forComponents(CtBlocks.TRANSIT_GATE)
            // The argument is the schematic to load, not the scene's id, which
            // comes from scene.title(). These two happen to differ in both.
            .addStoryBoard("transit_gate", TransitGateScenes::transitGate, TRANSIT)
            .addStoryBoard("transit_merge", TransitGateScenes::transitGateMerge, TRANSIT);

        scenes.forComponents(CtBlocks.TRANSIT_TICKER)
            .addStoryBoard("transit_ticker", TransitTickerScenes::transitTicker, TRANSIT);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag("transit")
            .addToIndex()
            .item(CtBlocks.TRANSIT_GATE.get(), true, false)
            .title("Transit Networks")
            .description("Components which let freight cross between logistics networks")
            .register();

        PonderTagRegistrationHelper<RegistryEntry<?>> tags = helper.withKeyFunction(RegistryEntry::getId);
        tags.addToTag(TRANSIT)
            .add(CtBlocks.TRANSIT_TICKER)
            .add(CtBlocks.TRANSIT_LINK)
            .add(CtBlocks.TRANSIT_GATE)
            // No scene of its own; it lists as an associated entry, which is
            // how the tag page points at what rail freight needs.
            .add(CtItems.TRANSIT_TIMETABLE);

        // Create's own index page for the Packager and Stock Link, which is
        // where someone looking for these would look first.
        tags.addToTag(AllCreatePonderTags.HIGH_LOGISTICS)
            .add(CtBlocks.TRANSIT_TICKER)
            .add(CtBlocks.TRANSIT_LINK)
            .add(CtBlocks.TRANSIT_GATE);
    }

}
