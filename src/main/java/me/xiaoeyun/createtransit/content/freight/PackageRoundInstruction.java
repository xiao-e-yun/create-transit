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
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime.State;
import com.simibubi.create.content.trains.schedule.destination.TextScheduleInstruction;
import com.simibubi.create.content.trains.station.GlobalPackagePort;
import com.simibubi.create.content.trains.station.GlobalStation;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createtransit.content.schedule.Repeats;
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

/**
 * One entry that works a whole round of stations instead of one journey.
 *
 * <p>Create's {@code package_retrieval} goes to the nearest station holding a
 * matching package, and {@code package_delivery} to the nearest station that
 * can take one off the train; either way the entry is then over. That is the
 * right shape when a station's freight is worth a trip of its own, and the
 * wrong one for what this exists for — a dozen small producers feeding one
 * warehouse, where the honest answer is one trip collecting a dozen and
 * Create's answer is a dozen round trips. Writing out a dozen entries is not
 * the fix either: the number is however many stations happen to have something
 * today.
 *
 * <p>So the round is the entry. It resolves one station at a time, exactly as
 * the vanilla instructions do, and asks through {@link Repeats} to be run again
 * until no station is left where anything would change hands. Collecting and
 * delivering are the same question — {@code GlobalStation.runMailTransfer}
 * loads and unloads at every stop regardless of why the train came — so they
 * are one instruction rather than two: a round can pick up at one station, drop
 * off at the next, and pick up again at the one after.
 *
 * <p>The filter is Create's, unchanged: the label is an <em>address</em>
 * pattern, so what is written is a flow rather than a place. {@code *warehouse*}
 * means "everything bound for the warehouse" and the stations follow from that,
 * which is why a new producer needs an address and no edit here. It only
 * governs what is picked up — where a package is dropped is decided by the
 * package's own address, as it is in vanilla.
 */
public class PackageRoundInstruction extends TextScheduleInstruction implements Repeats {

    /**
     * Stations already called at on this round. Transient, and the whole of the
     * round's state: present means a round is running, absent means the next
     * start begins one.
     *
     * <p>It bars collection and nothing else. A producer that refills while the
     * round is still running would otherwise be picked again and again — often
     * the station the train is standing on, which resolves to a path of no
     * length, reaches {@code destinationReached} at once, and never lets the
     * round reach its second station. Delivery is deliberately left free of it:
     * a round that collects at A, unloads at the warehouse and collects at B
     * has to be able to go back to the warehouse.
     */
    private static final String NBT_VISITED = "Visited";

    /**
     * The station the train was last sent to, while it is still on its way
     * there. Transient, and how arriving is noticed at all — nothing calls back
     * on arrival, so the next start looks at where the train is standing.
     *
     * <p>A station joins {@link #NBT_VISITED} only once that has happened.
     * Handing back a path is not the same as leaving: {@code tick} takes it only
     * if {@code startNavigation} accepts it, and when it does not the very same
     * entry is asked again a tick later. Recording on dispatch made that second
     * ask strike the station off and set out for another, so a round could walk
     * itself to the end in a second with the train standing still.
     */
    private static final String NBT_SENT = "Sent";

    // Spelled out rather than built from a prefix so scripts/lang_audit.py can
    // see them. A key assembled at runtime is invisible to it, which turns the
    // one check that catches an unused or misspelt translation into a liar.
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

    /**
     * Named rather than labelled, because the label here is a filter and a card
     * showing {@code *warehouse*} says less than one saying what it does.
     *
     * <p>Its plain name, and the same one the type dropdown offered — which is
     * what {@code FetchPackagesInstruction} puts here too. A card face is a
     * label on a stack of cards; the sentence explaining the instruction belongs
     * in the tooltip, where Create also keeps its own.
     */
    @Override
    public Pair<ItemStack, Component> getSummary() {
        return Pair.of(getSecondLineIcon(), Component.translatable(LANG_NAME));
    }

    /**
     * Overridden because the inherited one asks Create's language file for a key
     * under Create's namespace, which will never hold ours.
     *
     * <p>Create's own four-line shape, kept: a gold line that runs into the
     * quoted field, then the explanation over two short grey ones.
     */
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
        // Create's own limit on its own filter syntax, kept so a pattern written
        // here means what it means in the vanilla instruction beside it.
        box.setFilter(s -> StringUtils.countMatches(s, '*') <= 3);
    }

    /** Blank collects everything, which is what an unfilled field should mean. */
    private String addresses() {
        String filter = getLabelText();
        return Glob.toRegexPattern(filter.isBlank() ? "*" : filter, "");
    }

    /**
     * What the same field means on a transit train: the name of a border gate,
     * not an address.
     *
     * <p>A package in transit is addressed to a gate rather than to a place —
     * that is the whole of what a transit label is — so an address pattern has
     * nothing to bite on. The player types the gate's name, the same name they
     * gave it on the transit link, and the delimiters that make it a label never
     * appear on screen. Blank takes any gate, which is what blank means in
     * Create's own retrieval filter.
     *
     * <p>Matched with {@code PackageItem.matchAddress} rather than a pattern,
     * because label matching is already defined — outermost label only, exact,
     * with {@code *} as the one filter that takes any — and a glob cannot even
     * be built for it: {@code Glob.toRegexPattern} would hand the brackets
     * straight to the regex engine as a character class.
     */
    private String gate() {
        String name = getLabelText()
            .trim();
        return AddressLabels.endpoint(name.isEmpty() ? AddressLabels.WILDCARD : name);
    }

    @Override
    @Nullable
    public DiscoveredPath start(ScheduleRuntime runtime, Level level) {
        Train train = runtime.train;

        // Already going somewhere, so this is not a departure — it is Create
        // asking the same entry to think again, which it does every hundred
        // blocks or so of a journey and again when a station the train is
        // heading for enters assembly mode. Every instruction Create ships
        // answers that for free by re-picking the nearest match; this one would
        // write down another station called at and set off for a different one.
        //
        // Nothing said is the honest answer to "have you thought of anything
        // better": carry on. If the station really has become unreachable the
        // journey is cancelled anyway, and the next start is a fresh one that
        // finds this station already called at and moves the round along.
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

        // Where the train stands is barred from delivery and nowhere else. It is
        // the only way a round can stall: a postbox too full to take what the
        // train brought would otherwise be the nearest station that can take it,
        // for ever. Every other station stays open to a second visit, because
        // coming back to unload is the point — a round that collects at A,
        // unloads at the warehouse, collects at B and cannot return has left B's
        // freight on the train for no reason.
        GlobalStation here = train.getCurrentStation();

        // Standing where it was sent means that station has now been called at.
        // The only evidence there is, and the only moment it is true.
        String sent = getData().getString(NBT_SENT);
        if (!sent.isEmpty() && here != null && here.name.equals(sent)) {
            ListTag been = getData().getList(NBT_VISITED, Tag.TAG_STRING);
            been.add(StringTag.valueOf(sent));
            getData().put(NBT_VISITED, been);
            getData().remove(NBT_SENT);
        }

        Set<String> called = called();
        Set<GlobalStation> taken = spokenFor(train);
        // Which post this train runs is the schedule's to say, not this entry's.
        // Asked here so the round never sets out for a station whose only
        // freight is in the other lane and would be refused on arrival — and
        // because it also decides what the one text field means.
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
            // Unreachable rather than absent: something is there and this train
            // cannot get to it. Said as such and retried rather than given up
            // on, because a signal or a switch may free it.
            train.status.failedNavigation();
            runtime.startCooldown();
            return null;
        }

        // Sent for, not called at. Which side chose it does not matter once the
        // train gets there — runMailTransfer loads everything waiting and does
        // not care why it came — but whether it gets there does.
        getData().putString(NBT_SENT, best.destination.name);
        return best;
    }

    /**
     * Nothing left to exchange: end the round and let the schedule move on.
     *
     * <p>Which is also how a train carrying something undeliverable gets free.
     * Create's delivery instruction cools down and retries forever in that case,
     * so one package addressed to a station that no longer exists parks the
     * train for good; here it simply stops being a reason to go anywhere.
     */
    @Nullable
    private DiscoveredPath done(ScheduleRuntime runtime) {
        // Cleared before the entry moves on, so {@link #again} reads false and
        // the runtime is free to leave. The order matters.
        getData().remove(NBT_VISITED);
        getData().remove(NBT_SENT);
        runtime.state = State.PRE_TRANSIT;
        runtime.currentEntry++;
        runtime.startCooldown();
        return null;
    }

    /**
     * A round is running, so the entry is not finished with.
     *
     * <p>Answered from the list of stations called at rather than by looking
     * again: what is left can only be known by scanning, and scanning is what
     * {@link #start} does. So a round always ends with one extra start that
     * finds nothing — the price of not asking the same expensive question twice.
     */
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

    /**
     * Stations another train is already on its way to.
     *
     * <p>This is the whole of the scheduling. There is no claim table and
     * nothing to expire, because a train's claim on a station is something the
     * world already holds: where it is going. Nor need the other train be
     * carrying freight — {@code runMailTransfer} loads whatever is waiting into
     * whoever turns up, so any train heading somewhere is a train that will
     * empty it.
     *
     * <p>The race that remains is a station a train has already reached, which
     * is nobody's destination while it stands there. It costs at most one wasted
     * journey and settles itself: the packages are gone by the time the second
     * train picks its next move.
     */
    private static Set<GlobalStation> spokenFor(Train self) {
        Set<GlobalStation> taken = new HashSet<>();
        for (Train other : Create.RAILWAYS.trains.values()) {
            if (other == self)
                continue;
            GlobalStation heading = other.navigation.destination;
            if (heading != null)
                taken.add(heading);
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
     * @param wanted a border gate's label on a transit train, an address pattern
     *               otherwise — the two lanes address packages differently, so
     *               the one field is read differently
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
                // The same equality the station mixin refuses by, so the round
                // only ever sets out for something it will be handed.
                if (stack.getItem() instanceof TransitPackageItem != transit)
                    continue;
                // Create's own test: a package addressed to the port it sits in
                // has arrived rather than departed, and is not freight at all.
                // On the transit side that reads as "this is the gate it was
                // bound for", which is the same sentence one layer up.
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
                    // A filter the player is halfway through typing. Not thrown,
                    // because this runs while a train is deciding where to go.
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

    /**
     * Whether anything more could be loaded. An empty slot, because no two
     * packages ever stack — each carries its own address and order — so a
     * partly filled one is no room at all.
     */
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
