package me.xiaoeyun.createroutes.registry;

import me.xiaoeyun.createroutes.CreateRoutes;
import me.xiaoeyun.createroutes.content.route.RouteEditSession;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Where 1.20.1 wrote three root-level keys onto the borrowed schedule stack, 1.21 has no root tag
 * to write them on. One compound-valued component carries all three — the same shape Create gives
 * its own {@code TRAIN_SCHEDULE} — because they are written together and read together, and a
 * component per key would only be three chances for a stack to carry half a session.
 */
public class CrDataComponents {

    private static final DeferredRegister.DataComponents REGISTER =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateRoutes.MOD_ID);

    /** Present only on the stack {@link RouteEditSession} lends the editor, which is what marks it as one. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> ROUTE_EDITOR =
        REGISTER.registerComponentType("route_editor", b -> b
            .persistent(CompoundTag.CODEC)
            .networkSynchronized(ByteBufCodecs.COMPOUND_TAG));

    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }

}
