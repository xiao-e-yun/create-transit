package me.xiaoeyun.createtransit.registry;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.content.logistics.box.PackageStyles;

import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Places this mod's items in Create's own creative tab.
 *
 * The registrate is already pointed at BASE_CREATIVE_TAB, but that setting
 * places nothing by itself: CreateRegistrate's setCreativeTab only files the
 * entry in a lookup table, and the generator that reads it walks
 * {@code Create.registrate()} — Create's entries, never an addon's. So without
 * something like this, our blocks belong to no tab at all.
 *
 * That is worth more than tidiness, because a creative tab is also how a
 * recipe browser finds items: JEI builds its ingredient list from what the
 * tabs display and only falls back to the item registry when the player turns
 * on "show hidden items". An item in no tab is an item that, as far as the
 * browser is concerned, does not exist — recipes for it included.
 */
public final class CtCreativeTab {

    private CtCreativeTab() {}

    /**
     * Forge fires this after the tab's own generator has filled the map, so
     * Create's entries are already in place to position ours against.
     */
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey()
            .equals(AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey()))
            return;

        // Beside the block each one stands in for, rather than trailing after
        // the whole of Create: a gate is a packager and a transit link is a
        // stock link, and whoever wants one is already looking there. Missing
        // anchors need no guard -- putAfter falls back to appending.
        event.getEntries()
            .putAfter(AllBlocks.PACKAGER.asStack(), CtBlocks.TRANSIT_GATE.asStack(),
                TabVisibility.PARENT_AND_SEARCH_TABS);
        event.getEntries()
            .putAfter(AllBlocks.STOCK_LINK.asStack(), CtBlocks.TRANSIT_LINK.asStack(),
                TabVisibility.PARENT_AND_SEARCH_TABS);
        event.getEntries()
            .putAfter(AllBlocks.STOCK_TICKER.asStack(), CtBlocks.TRANSIT_TICKER.asStack(),
                TabVisibility.PARENT_AND_SEARCH_TABS);

        // The package rides with Create's own boxes, which sit in the tab in
        // registration order -- so "after the last style Create declares" is
        // the end of that run. Deriving the anchor from PackageStyles.STYLES
        // instead of hard-coding an id keeps it pointed there even if the
        // list changes shape upstream.
        Item lastBox = ForgeRegistries.ITEMS.getValue(PackageStyles.STYLES.get(PackageStyles.STYLES.size() - 1)
            .getItemId());
        // A missing anchor is not the same as an absent one: putAfter appends
        // only for a stack it cannot find, and a null item would be air.
        if (lastBox == null)
            event.accept(CtItems.TRANSIT_PACKAGE.asStack(), TabVisibility.PARENT_AND_SEARCH_TABS);
        else
            event.getEntries()
                .putAfter(new ItemStack(lastBox), CtItems.TRANSIT_PACKAGE.asStack(),
                    TabVisibility.PARENT_AND_SEARCH_TABS);
    }

}
