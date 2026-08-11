package me.xiaoeyun.createtransit.mixin.client;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.simibubi.create.content.trains.schedule.ScheduleScreen;

/**
 * Lets a configuration row close the editor it is sitting in.
 *
 * <p>An entry is edited on a copy; the copy only reaches the schedule when the
 * editor is confirmed. So a button that leaves for another screen has to
 * confirm first, or the route name the player just typed is thrown away on the
 * way out.
 *
 * <p>Both halves are needed, because they do different jobs. The callback
 * commits the instruction to its entry; {@code stopEditing} is what writes the
 * editor's widgets into that instruction's data. Every confirm path in
 * {@code ScheduleScreen} calls the two in that order, and calling only the
 * first reads back the value from before this edit.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = ScheduleScreen.class, remap = false)
public interface ScheduleScreenAccessor {

    @Accessor("onEditorClose")
    Consumer<Boolean> createTransit$getOnEditorClose();

    @Invoker("stopEditing")
    void createTransit$stopEditing();

}
