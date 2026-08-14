package me.xiaoeyun.createtransit.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.foundation.gui.ModularGuiLine;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;

/** Reaches the widget list behind a schedule entry's configuration row, which the builder's public surface has no way to add a button to. */
// remap = false: a Create class, so its names are never obfuscated.
@Mixin(value = ModularGuiLineBuilder.class, remap = false)
public interface ModularGuiLineBuilderAccessor {

    @Accessor("target")
    ModularGuiLine createTransit$getTarget();

    @Accessor("x")
    int createTransit$getX();

    @Accessor("y")
    int createTransit$getY();

}
