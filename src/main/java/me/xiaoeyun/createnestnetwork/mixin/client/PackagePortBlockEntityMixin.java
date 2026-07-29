package me.xiaoeyun.createnestnetwork.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;

import me.xiaoeyun.createnestnetwork.client.TransitLivery;
import me.xiaoeyun.createnestnetwork.content.transit.AddressLabels;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Tells a package port's model whether the port is a transit endpoint.
 *
 * The answer is read straight off the address, which is already on the client
 * to be drawn as a nameplate, so nothing is stored, synchronised or migrated
 * for the livery. A port that stops being an endpoint stops being painted by
 * the same token.
 */
@Mixin(value = PackagePortBlockEntity.class, remap = false)
public abstract class PackagePortBlockEntityMixin extends BlockEntity {

    @Shadow
    public String addressFilter;

    private PackagePortBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public ModelData getModelData() {
        return AddressLabels.startsWithLabel(addressFilter) ? TransitLivery.ENDPOINT_DATA : ModelData.EMPTY;
    }

    /**
     * Model data is only consulted while a chunk section is being built, and
     * editing an address rebuilds nothing on its own — so an address that
     * arrives from the server has to ask for both, or the paint would not
     * appear until something else happened to dirty the section.
     */
    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Z)V", at = @At("TAIL"))
    private void createNestNetwork$refreshLivery(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        if (!clientPacket)
            return;
        Level level = getLevel();
        if (level == null || !level.isClientSide)
            return;
        requestModelDataUpdate();
        // Not setBlocksDirty: it asks ModelManager whether the two states
        // differ before doing anything, and here they are the same object, so
        // it returns without marking a thing. The section has to be dirtied
        // outright.
        BlockPos pos = getBlockPos();
        Minecraft.getInstance().levelRenderer
            .setSectionDirtyWithNeighbors(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

}
