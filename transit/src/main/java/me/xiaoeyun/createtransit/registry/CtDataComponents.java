package me.xiaoeyun.createtransit.registry;

import java.util.List;

import com.mojang.serialization.Codec;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createtransit.content.transit.TransitCustoms;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Where 1.20.1 wrote a root-level tag on the box, 1.21 files the customs stack
 * as a data component: the codec is the persistence, the stream codec is what
 * lets a stamped box cross into an open container screen, and immutability is
 * the registry's rule rather than ours — {@link TransitCustoms#store} copies.
 */
public class CtDataComponents {

    private static final DeferredRegister.DataComponents REGISTER =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateTransit.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<TransitCustoms>>> TRANSIT_CUSTOMS =
        REGISTER.registerComponentType("transit_customs", b -> b
            .persistent(TransitCustoms.CODEC.listOf())
            .networkSynchronized(ByteBufCodecs.fromCodec(TransitCustoms.CODEC.listOf())));

    /** The parking bay a timetable is bound to; blank is the unbound item, which never carries the component. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TIMETABLE_DEPOT =
        REGISTER.registerComponentType("timetable_depot", b -> b
            .persistent(Codec.STRING)
            .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }

}
