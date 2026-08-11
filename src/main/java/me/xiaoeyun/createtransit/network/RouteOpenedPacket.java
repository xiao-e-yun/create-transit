package me.xiaoeyun.createtransit.network;

import java.util.function.Supplier;

import me.xiaoeyun.createtransit.content.route.RouteEditSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/**
 * The route editor is on screen.
 *
 * <p>Empty on purpose. It says when, not what — the server already knows which
 * route it opened, and letting the client name one would let it save a schedule
 * into any route it liked.
 *
 * <p>It exists because opening a route displaces whatever screen the player had,
 * and a displaced schedule screen saves itself on the way out. That save must
 * reach the item it came from, not the route; the only thing that knows the
 * displaced screen has gone is the client that replaced it.
 */
public class RouteOpenedPacket {

    public RouteOpenedPacket() {}

    public RouteOpenedPacket(FriendlyByteBuf buffer) {}

    public void encode(FriendlyByteBuf buffer) {}

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer sender = context.get()
            .getSender();
        if (sender != null)
            RouteEditSession.opened(sender);
    }

}
