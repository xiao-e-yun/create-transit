package me.xiaoeyun.createtransit.content.transit;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The customs manifest: which parent order a forwarded child order fulfils.
 *
 * A ticker forwarding across a border broadcasts a fresh order into the child
 * network and files one entry here; the transit gate that collects the child
 * order's boxes looks the entry up to re-stamp them with the parent identity
 * the destination's defragmenter expects. One entry is one border hop, so a
 * chain of borders composes naturally: each hop's gate consumes its own entry
 * and the boxes it releases already carry the identity the next hop filed.
 *
 * Stored as world saved data because boxes travel by rail: an order can be
 * hours in transit and survive any number of reloads. An entry whose boxes
 * never arrive expires quietly, and a gate holding boxes whose entry expired
 * releases them under their child identity — degraded to exactly what the
 * pre-customs behaviour was, never a jam.
 */
public class TransitOrderMappings extends SavedData {

    private static final String NAME = "create_transit_order_mappings";

    /** One real hour; generous for rail transit, short enough to self-heal. */
    private static final long EXPIRY_TICKS = 20 * 60 * 60;

    public record Mapping(int parentOrderId, int parentLinkIndex, boolean parentIsFinalLink,
        @Nullable PackageOrderWithCrafts context, long created) {}

    private final Map<Integer, Mapping> mappings = new HashMap<>();

    public static TransitOrderMappings get(ServerLevel level) {
        // The overworld carries the store so every dimension shares one
        // manifest: a border's two sides need not be in the same dimension.
        return level.getServer()
            .overworld()
            .getDataStorage()
            .computeIfAbsent(TransitOrderMappings::load, TransitOrderMappings::new, NAME);
    }

    public void register(int childOrderId, int parentOrderId, int parentLinkIndex, boolean parentIsFinalLink,
        @Nullable PackageOrderWithCrafts context, long gameTime) {
        purge(gameTime);
        mappings.put(childOrderId, new Mapping(parentOrderId, parentLinkIndex, parentIsFinalLink, context, gameTime));
        setDirty();
    }

    /** The entry a gate should hold this child order for, if it still stands. */
    @Nullable
    public Mapping peek(int childOrderId, long gameTime) {
        purge(gameTime);
        return mappings.get(childOrderId);
    }

    /** Consumes the entry: the child order's last box is about to be re-stamped. */
    @Nullable
    public Mapping take(int childOrderId, long gameTime) {
        purge(gameTime);
        Mapping mapping = mappings.remove(childOrderId);
        if (mapping != null)
            setDirty();
        return mapping;
    }

    private void purge(long gameTime) {
        if (mappings.values()
            .removeIf(mapping -> gameTime - mapping.created() > EXPIRY_TICKS || gameTime < mapping.created()))
            setDirty();
    }

    // Serialization

    private static TransitOrderMappings load(CompoundTag tag) {
        TransitOrderMappings result = new TransitOrderMappings();
        for (Tag entryTag : tag.getList("Mappings", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) entryTag;
            PackageOrderWithCrafts context =
                entry.contains("Context") ? PackageOrderWithCrafts.read(entry.getCompound("Context")) : null;
            result.mappings.put(entry.getInt("Child"), new Mapping(entry.getInt("Parent"), entry.getInt("Link"),
                entry.getBoolean("FinalLink"), context, entry.getLong("Created")));
        }
        return result;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        mappings.forEach((childOrderId, mapping) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Child", childOrderId);
            entry.putInt("Parent", mapping.parentOrderId());
            entry.putInt("Link", mapping.parentLinkIndex());
            entry.putBoolean("FinalLink", mapping.parentIsFinalLink());
            entry.putLong("Created", mapping.created());
            if (mapping.context() != null)
                entry.put("Context", mapping.context()
                    .write());
            list.add(entry);
        });
        tag.put("Mappings", list);
        return tag;
    }

}
