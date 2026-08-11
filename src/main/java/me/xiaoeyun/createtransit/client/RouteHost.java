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
 * <p>The route layout lives on Create's own {@code ScheduleScreen}, whose state
 * is private to the last field. Reaching it takes {@code @Shadow}, and a shadow
 * is the only thing here that a Create update can break: rename a field and it
 * fails at class load, change a method's shape and it fails at compile.
 *
 * <p>So they are worth counting, and worth keeping in one place. This interface
 * is that place — seven members, implemented once by the mixin, and the whole of
 * what the view is allowed to know about Create. Nothing else in this package
 * touches {@code ScheduleScreen}, which means a Create update is read against
 * one file rather than six hundred lines.
 *
 * <p>Implemented anonymously rather than by adding it to {@code ScheduleScreen}
 * itself: a mixin can give a class new interfaces, but that would put seven
 * public methods on Create's screen for every mod to see, to buy nothing.
 */
public interface RouteHost {

    /** For its width and height. The layout is proportional to both. */
    Screen screen();

    /** The route's stops, which are the schedule's entries. */
    List<ScheduleEntry> entries();

    /**
     * Whether Create's instruction or condition editor is up.
     *
     * <p>Create's own {@code action} refuses every click while one is, because
     * the editor covers the list and a click that reaches through it lands on
     * something the player cannot see. Replacing that method means taking the
     * rule with it.
     */
    boolean editorOpen();

    /** Opens Create's editor on a field. The callback fires on close. */
    void startEditing(IScheduleInput field, Consumer<Boolean> onClose, boolean allowDeletion);

    /**
     * What the editor is holding, read from inside an {@code onClose} callback.
     *
     * <p>Create hands the edited copy back through its own field rather than
     * through the callback, so it has to be read at the moment the callback
     * runs, not captured before.
     */
    ScheduleInstruction editedInstruction();

    ScheduleWaitCondition editedCondition();

    /** Re-runs the screen's layout, the way Create does after adding a stop. */
    void rebuild();

}
