package me.xiaoeyun.createtransit.client;

import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerVisual;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;

import me.xiaoeyun.createtransit.content.transit.TransitGateBlockEntity;
import me.xiaoeyun.createtransit.registry.CtPartialModels;
import net.minecraft.core.Direction;

/**
 * Swings a gate's curtain, on the Flywheel path. Inherits the packager's visual rather than
 * replacing it, so the tray, the package and the rail stay Create's to place and no copy of its
 * chain lives here.
 *
 * The strips are re-placed every frame because {@link PackagerVisual} implements
 * {@code SimpleDynamicVisual}, whose {@code beginFrame} already calls {@link #animate}; the cost
 * is one matrix per strip per frame, and only while the curtain is moving.
 */
public class TransitGateVisual extends PackagerVisual<TransitGateBlockEntity> {

    private final TransformedInstance[] strips = new TransformedInstance[TransitGateCurtain.STRIPS];

    /** The pose last written, guarded as Create guards its own tray, so a resting curtain costs nothing. */
    private float lastFlapness = Float.NaN;

    public TransitGateVisual(VisualizationContext ctx, TransitGateBlockEntity gate, float partialTick) {
        super(ctx, gate, partialTick);

        for (int strip = 0; strip < strips.length; strip++)
            strips[strip] = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(CtPartialModels.flap(strip)))
                .createInstance();

        animate(partialTick);
    }

    @Override
    public void animate(float partialTick) {
        super.animate(partialTick);

        // PackagerVisual's constructor ends by calling animate, which lands here
        // before this class's field initialisers have run.
        if (strips == null)
            return;

        float flapness = blockEntity.curtainPush(partialTick);
        if (flapness == lastFlapness)
            return;

        Direction facing = blockState.getValue(PackagerBlock.FACING);
        for (int strip = 0; strip < strips.length; strip++) {
            TransformedInstance instance = strips[strip].setIdentityTransform();
            instance.translate(getVisualPosition());
            TransitGateCurtain.place(instance, facing, strip, flapness)
                .setChanged();
        }

        lastFlapness = flapness;
    }

    @Override
    public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        relight(strips);
    }

    @Override
    protected void _delete() {
        super._delete();
        for (TransformedInstance strip : strips)
            strip.delete();
    }

}
