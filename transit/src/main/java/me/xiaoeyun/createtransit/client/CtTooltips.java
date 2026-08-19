package me.xiaoeyun.createtransit.client;

import java.util.List;

import com.simibubi.create.content.logistics.box.PackageItem;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Spells out the address on a package in transit. The line is rewritten rather than injected,
 * because Create builds it from the raw tag and there is nothing in the middle to intercept;
 * finding it by the address it ends with keeps Create's prefix and leaves a line another mod has
 * already rewritten alone.
 */
@Mod.EventBusSubscriber(modid = CreateTransit.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE,
    value = Dist.CLIENT)
public final class CtTooltips {

    private CtTooltips() {}

    @SubscribeEvent
    public static void spellPackageAddress(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!PackageItem.isPackage(stack))
            return;

        String address = PackageItem.getAddress(stack);
        if (!AddressLabels.startsWithLabel(address))
            return;

        List<Component> tooltip = event.getToolTip();
        for (int i = 0; i < tooltip.size(); i++) {
            Component line = tooltip.get(i);
            String text = line.getString();
            if (!text.endsWith(address))
                continue;
            String prefix = text.substring(0, text.length() - address.length());
            tooltip.set(i, Component.literal(prefix + TransitAddress.spell(address))
                .withStyle(line.getStyle()));
            return;
        }
    }

}
