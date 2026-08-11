package me.xiaoeyun.createtransit.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.foundation.gui.ModularGuiLine;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;

/**
 * Reaches the widget list behind a schedule entry's configuration row.
 *
 * <p>The builder offers scroll inputs and text boxes and nothing else — there
 * is no way to put a button on that row through its public surface, and the
 * list it is filling, along with where on screen the row sits, are private.
 * Create Railways Navigator solves this the same way, which is some assurance
 * the shape is stable.
 *
 * <p>Only the row inside Create's own screen needs this. Our editor builds its
 * own {@link ModularGuiLine} through the public constructor and can add
 * whatever it likes to it.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = ModularGuiLineBuilder.class, remap = false)
public interface ModularGuiLineBuilderAccessor {

    @Accessor("target")
    ModularGuiLine createTransit$getTarget();

    @Accessor("x")
    int createTransit$getX();

    @Accessor("y")
    int createTransit$getY();

}
