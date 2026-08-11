package me.xiaoeyun.createtransit.network;

import java.util.function.Supplier;

import com.simibubi.create.content.trains.schedule.ScheduleItem;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

/**
 * Puts the player back on the schedule they were holding.
 *
 * <p>A route may be opened from a train's schedule, and finishing with it used
 * to close everything — so the way back to the schedule was to right-click the
 * item again, having lost the row that led in. This is the last step out of a
 * trip that began there.
 *
 * <p>Empty, and it names nothing. What it opens is the schedule in the player's
 * main hand, which is the only one Create ever opens: {@code ScheduleItem} is
 * its own {@code MenuProvider} and this is the same call its right-click makes.
 * Nothing to open — the item was put away or handed to a train while the route
 * was being edited — is not a failure. The editor simply closes, which is what
 * it did before there was anywhere to go back to.
 */
public class ScheduleReopenPacket {

    public ScheduleReopenPacket() {}

    public ScheduleReopenPacket(FriendlyByteBuf buffer) {}

    public void encode(FriendlyByteBuf buffer) {}

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get()
            .getSender();
        if (sender == null)
            return;

        ItemStack held = sender.getMainHandItem();
        if (held.getItem() instanceof ScheduleItem schedule)
            NetworkHooks.openScreen(sender, schedule, buffer -> buffer.writeItem(held));
        else
            sender.closeContainer();
    }

}
