package me.xiaoeyun.createtransit.content.freight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime.State;
import com.simibubi.create.content.trains.schedule.destination.TextScheduleInstruction;
import com.simibubi.create.content.trains.station.GlobalPackagePort;
import com.simibubi.create.content.trains.station.GlobalStation;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createroutes.schedule.Repeats;
import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import me.xiaoeyun.createtransit.content.transit.TransitPackageItem;
import net.createmod.catnip.data.Glob;
import net.createmod.catnip.data.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.IItemHandlerModifiable;

/** One entry that works a whole round of stations instead of one, asking via {@link Repeats} to run again until nothing more would change hands. */
public class PackageRoundInstruction extends TextScheduleInstruction implements Repeats {

    /** Stations already called at this round; bars re-collection only, so a round can still return to unload. */
    private static final String NBT_VISITED = "Visited";

    /** Station last sent to while still travelling; joins {@link #NBT_VISITED} only once standing there — no arrival hook exists. */
    private static final String NBT_SENT = "Sent";

    // Spelled out, not built from a prefix, so scripts/lang_audit.py can see these keys.
    /** The name in the type dropdown, which is also what the card wears. */
    private static final String LANG_NAME = "create_transit.schedule.instruction.package_round";
    private static final String LANG_SUMMARY =
        "create_transit.schedule.instruction.package_round.summary";
    private static final String LANG_SUMMARY_1 =
        "create_transit.schedule.instruction.package_round.summary_1";
    private static final String LANG_SUMMARY_2 =
        "create_transit.schedule.instruction.package_round.summary_2";
    private static final String LANG_ADDRESS =
        "create_transit.schedule.instruction.package_round.address";
    private static final String LANG_ADDRESS_1 =
        "create_transit.schedule.instruction.package_round.address_1";
    private static final String LANG_ADDRESS_2 =
        "create_transit.schedule.instruction.package_round.address_2";

    @Override
    public ResourceLocation getId() {
        return CreateTransit.asResource("package_round");
    }

    @Override
    public boolean supportsConditions() {
        return true;
    }

    @Override
    public ItemStack getSecondLineIcon() {
        return PackageStyles.getDefaultBox();
    }

    /** Named rather than labelled, since the label here is a filter and a card showing {@code *warehouse*} says less than its plain name. */
    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(getSecondLineIcon(), Component.translatable(LANG_NAME));
    }

    /** Overridden because the inherited one looks up a key under Create's own namespace, not ours. */
    @Override
    public List<Component> getTitleAs(String type) {
        return ImmutableList.of(Component.translatable(LANG_SUMMARY)
            .withStyle(ChatFormatting.GOLD),
            Component.translatable("create.generic.in_quotes", Component.literal(getLabelText())),
            Component.translatable(LANG_SUMMARY_1)
                .withStyle(ChatFormatting.GRAY),
            Component.translatable(LANG_SUMMARY_2)
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public List<Component> getSecondLineTooltip(int slot) {
        return ImmutableList.of(Component.translatable(LANG_ADDRESS),
            Component.translatable(LANG_ADDRESS_1)
                .withStyle(ChatFormatting.GRAY),
            Component.translatable(LANG_ADDRESS_2)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void modifyEditBox(EditBox box) {
        // Create's own limit, kept so a pattern here means what it means in the vanilla instruction beside it.
        box.setFilter(s -> StringUtils.countMatches(s, '*') <= 3);
    }

    /** Blank collects everything, which is what an unfilled field should mean. */
    private String addresses() {
        String filter = getLabelText();
        return Glob.toRegexPattern(filter.isBlank() ? "*" : filter, "");
    }

    /** What the same field means on a transit train: a border gate's name, matched via {@code PackageItem.matchAddress} since a gate label can't be expressed as a glob pattern. */
    private String gate() {
        String name = getLabelText()
            .trim();
        return AddressLabels.endpoint(name.isEmpty() ? AddressLabels.WILDCARD : name);
    }

    @Override
    @Nullable
    public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        Train train = runtime.train;

        // Create re-runs the live entry mid-journey and on assembly; answered with null so the round doesn't
        // abandon its current station for another mid-trip.
        if (train.navigation.destination != null)
            return null;

        MinecraftServer server = level.getServer();
        if (server == null)
            return done(runtime);

        if (!train.hasForwardConductor() && !train.hasBackwardConductor()) {
            train.status.missingConductor();
            runtime.startCooldown();
            return null;
        }

        // The standing station is barred from delivery only — otherwise a full postbox there would be the
        // nearest deliverable stop forever.
        GlobalStation here = train.getCurrentStation();

        // Standing at the sent station is the only evidence it was reached.
        String sent = getData().getString(NBT_SENT);
        if (!sent.isEmpty() && here != null && here.name.equals(sent)) {
            ListTag been = getData().getList(NBT_VISITED, Tag.TAG_STRING);
            been.add(StringTag.valueOf(sent));
            getData().put(NBT_VISITED, been);
            getData().remove(NBT_SENT);
        }

        Set<String> called = called();
        Set<GlobalStation> taken = spokenFor(train);
        // Read here so the round never sets out for freight in the other lane, which would be refused on arrival.
        boolean transit = TransitTrain.of(runtime.schedule);
        String wanted = transit ? gate() : addresses();
        boolean room = hasRoom(train);
        List<ItemStack> aboard = aboard(train);

        // ArrayList and not List: Create's overload is declared on the concrete
        // type, so the interface does not resolve.
        ArrayList<GlobalStation> exchanging = new ArrayList<>();
        for (GlobalStation station : train.graph.getPoints(EdgePointType.STATION)) {
            if (taken.contains(station))
                continue;
            if (station != here && delivers(station, aboard)) {
                exchanging.add(station);
                continue;
            }
            if (room && !called.contains(station.name) && collects(station, server, wanted, transit))
                exchanging.add(station);
        }

        if (exchanging.isEmpty())
            return done(runtime);

        DiscoveredPath best = train.navigation.findPathTo(exchanging, Double.MAX_VALUE);
        if (best == null) {
            // Unreachable, not absent — retried rather than abandoned since a signal or switch may free it.
            train.status.failedNavigation();
            runtime.startCooldown();
            return null;
        }

        // Sent for, not called at — runMailTransfer loads and unloads at every stop regardless of why the train came.
        getData().putString(NBT_SENT, best.destination.name);
        return best;
    }

    /** Nothing left to exchange: ends the round; also how a train carrying an undeliverable package gets free instead of retrying forever. */
    @Nullable
    private DiscoveredPath done(ScheduleRuntime runtime) {
        // Cleared before advancing, so {@link #again} reads false and the runtime is free to leave.
        clearRound();
        runtime.state = State.PRE_TRANSIT;
        runtime.currentEntry++;
        runtime.startCooldown();
        return null;
    }

    /** True while a round is running; a round always ends with one extra start that finds nothing, the price of not scanning twice. */
    @Override
    public boolean again() {
        return getData().contains(NBT_VISITED) || getData().contains(NBT_SENT);
    }

    private Set<String> called() {
        Set<String> names = new HashSet<>();
        for (Tag name : getData().getList(NBT_VISITED, Tag.TAG_STRING))
            names.add(name.getAsString());
        return names;
    }

    /** Drops what only makes sense while a particular train is on a round. */
    public void clearRound() {
        getData().remove(NBT_VISITED);
        getData().remove(NBT_SENT);
    }

    @Override
    public void clearTransient(ScheduleEntry entry) {
        clearRound();
    }

    /** Stations another train is heading to or standing at; the destination claim vanishes on arrival, so the platform itself claims until departure. */
    private static Set<GlobalStation> spokenFor(Train self) {
        Set<GlobalStation> taken = new HashSet<>();
        for (Train other : Create.RAILWAYS.trains.values()) {
            if (other == self)
                continue;
            GlobalStation heading = other.navigation.destination;
            if (heading != null)
                taken.add(heading);
            GlobalStation standing = other.getCurrentStation();
            if (standing != null)
                taken.add(standing);
        }
        return taken;
    }

    /** Whether anything on the train belongs here. Ports only, so no chunk is read. */
    private static boolean delivers(GlobalStation station, List<ItemStack> aboard) {
        for (GlobalPackagePort port : station.connectedPorts.values())
            for (ItemStack carried : aboard)
                if (PackageItem.matchAddress(carried, port.address))
                    return true;
        return false;
    }

    /**
     * Whether anything here is waiting to be taken, and is wanted.
     *
     * @param wanted a border gate's label on a transit train, an address pattern otherwise
     */
    private static boolean collects(GlobalStation station, MinecraftServer server, String wanted,
        boolean transit) {
        ServerLevel level = server.getLevel(station.blockEntityDimension);
        if (level == null)
            return false;

        for (Entry<BlockPos, GlobalPackagePort> entry : station.connectedPorts.entrySet()) {
            GlobalPackagePort port = entry.getValue();
            BlockPos pos = entry.getKey();

            // The buffer is where an unloaded postbox keeps its mail; a loaded
            // one has the real inventory and the buffer is stale.
            IItemHandlerModifiable inventory = port.offlineBuffer;
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof PostboxBlockEntity postbox)
                inventory = postbox.inventory;

            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!PackageItem.isPackage(stack))
                    continue;
                // Same equality the station mixin refuses by, so the round only sets out for what it will be handed.
                if (stack.getItem() instanceof TransitPackageItem != transit)
                    continue;
                // Create's own test: a package addressed to the port it sits in has arrived, not departed, and isn't freight.
                if (PackageItem.matchAddress(stack, port.address))
                    continue;

                if (transit) {
                    if (PackageItem.matchAddress(stack, wanted))
                        return true;
                    continue;
                }

                try {
                    if (PackageItem.getAddress(stack)
                        .matches(wanted))
                        return true;
                } catch (PatternSyntaxException malformed) {
                    // Half-typed filter; not thrown, since this runs while a train is deciding where to go.
                    return false;
                }
            }
        }
        return false;
    }

    /** Every package currently on the train, which is what it has to deliver. */
    private static List<ItemStack> aboard(Train train) {
        List<ItemStack> packages = new ArrayList<>();
        for (Carriage carriage : train.carriages) {
            IItemHandlerModifiable inventory = carriage.storage.getAllItems();
            if (inventory == null)
                continue;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (PackageItem.isPackage(stack))
                    packages.add(stack);
            }
        }
        return packages;
    }

    /** Whether anything more could be loaded; an empty slot, since no two packages ever stack. */
    private static boolean hasRoom(Train train) {
        for (Carriage carriage : train.carriages) {
            IItemHandlerModifiable inventory = carriage.storage.getAllItems();
            if (inventory == null)
                continue;
            for (int slot = 0; slot < inventory.getSlots(); slot++)
                if (inventory.getStackInSlot(slot)
                    .isEmpty())
                    return true;
        }
        return false;
    }

}
