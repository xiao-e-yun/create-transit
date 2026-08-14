package me.xiaoeyun.createtransit.client;

import java.util.List;
import java.util.function.Consumer;

import com.simibubi.create.content.trains.schedule.IScheduleInput;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;

import net.minecraft.client.gui.screens.Screen;

/**
 * Everything {@link RouteView} needs from the screen it is drawn on, and
 * nothing else.
 *
 * <p>Reaching Create's {@code ScheduleScreen} internals takes {@code @Shadow},
 * so this interface is the only place a Create update can break — implemented
 * once by the mixin rather than added to {@code ScheduleScreen} itself, which
 * would expose these methods on Create's own screen to buy nothing.
 */
public interface RouteHost {

    /** For its width and height, which the layout is proportional to. */
    Screen screen();

    /** The route's stops, which are the schedule's entries. */
    List<ScheduleEntry> entries();

    /** Whether Create's instruction or condition editor is up. */
    boolean editorOpen();

    /** Opens Create's editor on a field; the callback fires on close. */
    void startEditing(IScheduleInput field, Consumer<Boolean> onClose, boolean allowDeletion);

    /** What the editor is holding, read from inside an {@code onClose} callback. */
    ScheduleInstruction editedInstruction();

    ScheduleWaitCondition editedCondition();

    /** Re-runs the screen's layout, the way Create does after adding a stop. */
    void rebuild();

}
