package me.xiaoeyun.createtransit.registry;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.tterrag.registrate.util.entry.ItemEntry;

import me.xiaoeyun.createtransit.content.transit.TransitPackageItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab.TabVisibility;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

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
     * NeoForge fires this after the tab's own generator has filled the set, so
     * Create's entries are already in place to position ours against.
     */
    public static void onBuildContents(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey()
            .equals(AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey()))
            return;

        // Beside the block each one stands in for, rather than trailing after
        // the whole of Create: a gate is a packager and a transit link is a
        // stock link, and whoever wants one is already looking there.
        insertAfterOrAppend(event, AllBlocks.PACKAGER.asStack(), CtBlocks.TRANSIT_GATE.asStack());
        insertAfterOrAppend(event, AllBlocks.STOCK_LINK.asStack(), CtBlocks.TRANSIT_LINK.asStack());
        insertAfterOrAppend(event, AllBlocks.STOCK_TICKER.asStack(), CtBlocks.TRANSIT_TICKER.asStack());
        insertAfterOrAppend(event, AllItems.SCHEDULE.asStack(), CtItems.TRANSIT_TIMETABLE.asStack());

        // The packages ride with Create's own boxes, which sit in the tab in
        // registration order -- so "after the last style Create declares" is
        // the end of that run. Deriving the anchor from PackageStyles.STYLES
        // instead of hard-coding an id keeps it pointed there even if the
        // list changes shape upstream.
        Item lastBox = BuiltInRegistries.ITEM.get(PackageStyles.STYLES.get(PackageStyles.STYLES.size() - 1)
            .getItemId());
        // A registry miss answers AIR, and an air anchor is no anchor at all.
        // Each box anchors on the one before it, so the four keep their order.
        ItemStack anchor = lastBox == Items.AIR ? null : new ItemStack(lastBox);
        for (ItemEntry<TransitPackageItem> entry : CtItems.TRANSIT_PACKAGES) {
            ItemStack stack = entry.asStack();
            insertAfterOrAppend(event, anchor, stack);
            anchor = stack;
        }
    }

    /**
     * Forge's putAfter appended when the anchor was missing; NeoForge's
     * insertAfter throws instead, so the fallback moves out here. The parent
     * list is checked for both tabs -- Create adds everything we anchor on to
     * both, so the two sets cannot disagree about these stacks.
     */
    private static void insertAfterOrAppend(BuildCreativeModeTabContentsEvent event,
        ItemStack anchor, ItemStack stack) {
        if (anchor != null && event.getParentEntries()
            .contains(anchor))
            event.insertAfter(anchor, stack, TabVisibility.PARENT_AND_SEARCH_TABS);
        else
            event.accept(stack, TabVisibility.PARENT_AND_SEARCH_TABS);
    }

}
