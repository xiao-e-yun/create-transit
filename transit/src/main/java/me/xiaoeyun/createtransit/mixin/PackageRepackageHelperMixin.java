package me.xiaoeyun.createtransit.mixin;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.repackager.PackageRepackageHelper;

import me.xiaoeyun.createtransit.content.transit.TransitCustoms;
import me.xiaoeyun.createtransit.content.transit.TransitPackaging;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Carries a customs declaration across a vanilla repackaging, which {@code PackageItem.containing}
 * otherwise loses by setting a fresh box's tag wholesale. Every fragment of one child order is
 * stamped from the same filing, so the first source box speaks for all of them; at the exit of
 * {@code repack} because the declaration has to land after {@code setOrder}, which clears one.
 */
// remap = false: the target is a Create class, so its names are never
// obfuscated and there is no SRG mapping for the annotation processor.
@Mixin(value = PackageRepackageHelper.class, remap = false)
public class PackageRepackageHelperMixin {

    @Shadow
    protected Map<Integer, List<ItemStack>> collectedPackages;

    @ModifyReturnValue(method = "repack(ILnet/minecraft/util/RandomSource;)Ljava/util/List;", at = @At("RETURN"))
    private List<BigItemStack> createTransit$repackKeepsDeclaration(List<BigItemStack> repacked, int orderId,
        RandomSource random) {
        List<ItemStack> sources = collectedPackages.get(orderId);
        if (sources == null || sources.isEmpty())
            return repacked;
        List<TransitCustoms> declarations = TransitCustoms.on(sources.get(0));
        for (BigItemStack box : repacked) {
            TransitCustoms.store(box.stack, declarations);
            // After the tag is written, since restyling copies it across.
            box.stack = TransitPackaging.restyle(box.stack);
        }
        return repacked;
    }

}
