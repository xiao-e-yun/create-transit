package me.xiaoeyun.createtransit.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Repaints a package port when its address changes; the livery itself is read off the address by the baked model. */
@Mixin(value = PackagePortBlockEntity.class, remap = false)
public class PackagePortBlockEntityMixin {

    /**
     * Model data is only consulted while a chunk section is being built, and
     * editing an address rebuilds nothing on its own — so an address that
     * arrives from the server has to ask for one.
     */
    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Z)V",
        at = @At("TAIL"))
    private void createTransit$refreshLivery(CompoundTag tag, HolderLookup.Provider registries,
        boolean clientPacket, CallbackInfo ci) {
        if (!clientPacket)
            return;
        BlockEntity port = (BlockEntity) (Object) this;
        Level level = port.getLevel();
        if (level == null || !level.isClientSide)
            return;
        // Not setBlocksDirty: it asks ModelManager whether the two states
        // differ before doing anything, and here they are the same object.
        BlockPos pos = port.getBlockPos();
        Minecraft.getInstance().levelRenderer
            .setSectionDirtyWithNeighbors(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

}
