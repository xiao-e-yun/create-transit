package me.xiaoeyun.createroutes.mixin.client;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.simibubi.create.content.trains.schedule.ScheduleScreen;

/** Lets a configuration row close the editor it is sitting in; callback then {@code stopEditing} — both required, Create's own confirm order. */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = ScheduleScreen.class, remap = false)
public interface ScheduleScreenAccessor {

    @Accessor("onEditorClose")
    Consumer<Boolean> createTransit$getOnEditorClose();

    @Invoker("stopEditing")
    void createTransit$stopEditing();

}
