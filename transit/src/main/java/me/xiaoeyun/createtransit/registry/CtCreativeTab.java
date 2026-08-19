package me.xiaoeyun.createtransit.registry;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.tterrag.registrate.util.entry.ItemEntry;

import me.xiaoeyun.createtransit.content.transit.TransitPackageItem;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Places this mod's items in Create's own creative tab.
 *
 * The registrate is already pointed at BASE_CREATIVE_TAB, but that places nothing by itself:
 * CreateRegistrate's setCreativeTab only files the entry in a lookup table, and the generator
 * reading it walks {@code Create.registrate()} — Create's entries, never an addon's. Without this
 * our blocks belong to no tab, which also hides them from JEI's ingredient list.
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

        // Beside the block each one stands in for. Missing anchors need no guard -- putAfter
        // falls back to appending.
        event.getEntries()
            .putAfter(AllBlocks.PACKAGER.asStack(), CtBlocks.TRANSIT_GATE.asStack(),
                TabVisibility.PARENT_AND_SEARCH_TABS);
        event.getEntries()
            .putAfter(AllBlocks.STOCK_LINK.asStack(), CtBlocks.TRANSIT_LINK.asStack(),
                TabVisibility.PARENT_AND_SEARCH_TABS);
        event.getEntries()
            .putAfter(AllBlocks.STOCK_TICKER.asStack(), CtBlocks.TRANSIT_TICKER.asStack(),
                TabVisibility.PARENT_AND_SEARCH_TABS);
        event.getEntries()
            .putAfter(AllItems.SCHEDULE.asStack(), CtItems.TRANSIT_TIMETABLE.asStack(),
                TabVisibility.PARENT_AND_SEARCH_TABS);

        // Create's own boxes sit in the tab in registration order, so "after the last style
        // Create declares" is the end of that run. Derived from PackageStyles.STYLES rather than
        // a hard-coded id, so it stays pointed there if the list changes shape upstream.
        Item lastBox = ForgeRegistries.ITEMS.getValue(PackageStyles.STYLES.get(PackageStyles.STYLES.size() - 1)
            .getItemId());
        // A missing anchor is not the same as an absent one: putAfter appends
        // only for a stack it cannot find, and a null item would be air.
        // Each box anchors on the one before it, so the four keep their order.
        ItemStack anchor = lastBox == null ? null : new ItemStack(lastBox);
        for (ItemEntry<TransitPackageItem> entry : CtItems.TRANSIT_PACKAGES) {
            ItemStack stack = entry.asStack();
            if (anchor == null)
                event.accept(stack, TabVisibility.PARENT_AND_SEARCH_TABS);
            else
                event.getEntries()
                    .putAfter(anchor, stack, TabVisibility.PARENT_AND_SEARCH_TABS);
            anchor = stack;
        }
    }

}
