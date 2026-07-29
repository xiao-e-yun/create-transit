package me.xiaoeyun.createtransit.client;

import java.util.List;

import com.simibubi.create.content.logistics.box.PackageItem;

import me.xiaoeyun.createtransit.CreateTransit;
import me.xiaoeyun.createtransit.content.transit.AddressLabels;
import me.xiaoeyun.createtransit.content.transit.TransitAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Spells out the address on a package in transit.
 *
 * A box carrying {@code <[depot]> drawer 4} was showing exactly that, which is
 * the one place a player meets the grammar without having gone looking for it:
 * ports are configured with a switch and links with a plain name, but a package
 * can be picked up by anybody, anywhere along the route.
 *
 * The line is rewritten rather than injected, because Create builds it from the
 * raw tag and there is nothing in the middle to intercept. Finding it by the
 * address it ends with also means the prefix stays whatever Create decided it
 * is, and a line some other mod has already rewritten is left alone.
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
