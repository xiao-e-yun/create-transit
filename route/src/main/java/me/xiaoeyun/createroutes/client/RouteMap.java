package me.xiaoeyun.createroutes.client;

import java.util.List;
import java.util.function.Predicate;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.compat.trainmap.TrainMapManager;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/** Create's own railway map, boxed off from the schedule editing {@link RouteView} does. */
public class RouteMap {

    /**
     * How near the cursor has to be to a station to mean it: four pixels, or the
     * sprite itself, whichever is the larger.
     *
     * <p>Neither alone is enough — a fixed pixel target breaks down zoomed in,
     * and Create's fixed block reach vanishes zoomed out.
     */
    private static final int REACH = 4;

    /** Create's own, which is what the sprite it draws is measured in. */
    private static final int SPRITE = 3;

    /** How far back the map may be pulled, in pixels per block. */
    private static final float FLOOR = 1 / 8f;

    private static final int MARKER = 0xFFFFFFFF;

    /** A station this route stops at, ringed in this colour. */
    private static final int ON_ROUTE = 0xFF4C8CFF;

    /** The middle of Create's five by five station sprite, which its ring turns about. */
    private static final float SPRITE_MIDDLE = 2.5F;

    /** How wide a station is ringed when a row names it, in pixels. */
    private static final int LINK = 4;

    /** The ground under the map, dark enough that a rail is the brightest thing on it — Create draws no terrain, so without this the checkered field would show through. */
    private static final int GROUND = 0xB3000000;

    /** Vanilla's map markers: sprite zero of sixteen, eight square on a 128 sheet. */
    private static final ResourceLocation MAP_ICONS =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/map/map_icons.png");

    private static final int MAP_ICON = 8;

    private static final int MAP_SHEET = 128;

    private final RouteHost host;

    /** Where the map is centred, in blocks. NaN until the player's position seeds it. */
    private double mapX = Double.NaN;

    private double mapZ;

    /** Screen pixels per block. */
    private float scale = 1;

    private boolean panning;

    private double grabX;

    private double grabY;

    /** Where the map was last drawn, so {@link #look} has something to fit into. */
    private Box box;

    /**
     * The station under the cursor, worked out ourselves rather than asked of
     * Create's own {@code renderAndPick} — that answers with a line of text,
     * not the station, and measures the cursor in blocks rather than pixels.
     */
    private Stations.At station;

    /** What the map says is under the cursor, held until the frame's last word. */
    private List<FormattedText> mapTip;

    /** Every station in the world, as it was this frame — read fresh by {@link #render} and shared by the rest of the frame's questions of it. */
    private List<Stations.At> stations = List.of();

    RouteMap(RouteHost host) {
        this.host = host;
    }

    /** Which station the cursor is on, or null. */
    public Stations.At station() {
        return station;
    }

    /** What the map wants said in a tooltip this frame, or null for nothing. */
    public List<FormattedText> tooltip() {
        return mapTip;
    }

    /**
     * Create's own railway map, drawn into our window.
     *
     * <p>{@code renderAndPick} takes the visible area in blocks, not pixels, so
     * the rectangle passed to it is world coordinates.
     *
     * <p>Ticked here because {@code TrainMapEvents} only ticks for FTB Chunks,
     * JourneyMap and Xaero; without one installed the data would never rebuild.
     */
    public void render(GuiGraphics graphics, Box box, List<Predicate<String>> filters, int mouseX,
        int mouseY, float partialTicks) {
        this.box = box;
        int x = box.x();
        int y = box.y();
        int width = box.width();
        int height = box.height();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || width <= 0 || height <= 0)
            return;
        TrainMapManager.tick(minecraft.level.dimension());
        mapTip = null;
        stations = Stations.all();
        // Before the drag, which would otherwise be measured against a centre
        // that is not a number yet.
        if (Double.isNaN(mapX))
            frame(minecraft, filters);
        drag(mouseX, mouseY);

        graphics.enableScissor(x, y, x + width, y + height);
        graphics.fill(x, y, x + width, y + height, GROUND);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + width / 2.0, y + height / 2.0, 0);
        pose.scale(scale, scale, 1);
        pose.translate(-mapX, -mapZ, 0);

        double halfWidth = width / 2.0 / scale;
        double halfHeight = height / 2.0 / scale;
        // Converted to world space, since renderAndPick tests the cursor in
        // blocks, not screen pixels.
        boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        double worldX = mapX + (mouseX - (x + width / 2.0)) / scale;
        double worldZ = mapZ + (mouseY - (y + height / 2.0)) / scale;
        station = over ? Stations.near(stations, worldX, worldZ, reach()) : null;

        // Nothing for Create to pick: a cursor it can never reach, since its
        // own highlight at its stricter reach would otherwise show as a second
        // ring.
        //
        // Nearest-neighbour, since everything on this map is a pixel drawing
        // that linear filtering would turn to a smear.
        int away = Integer.MIN_VALUE / 2;
        TrainMapManager.renderAndPick(graphics, away, away, partialTicks, false,
            new Rect2i((int) (mapX - halfWidth), (int) (mapZ - halfHeight), (int) (halfWidth * 2),
                (int) (halfHeight * 2)));

        pose.popPose();
        graphics.disableScissor();
    }

    /**
     * Everything on the map that is ours: the route, the player, and the two
     * ends of whatever the cursor has hold of.
     *
     * <p>Called after the table has drawn, so that {@code hoveredStop} is this
     * frame's rather than last frame's.
     */
    public void marks(GuiGraphics graphics, Box at, List<Predicate<String>> filters, int hoveredStop) {
        Minecraft minecraft = Minecraft.getInstance();
        if (Double.isNaN(mapX) || minecraft.player == null)
            return;
        graphics.enableScissor(at.x(), at.y(), at.right(), at.bottom());

        // A row lights its stations and a station lights its rows; the filter
        // is the whole of the relation, so there is nothing here to keep in step.
        Predicate<String> linked =
            hoveredStop >= 0 && hoveredStop < filters.size() ? filters.get(hoveredStop) : name -> false;

        // In the map's own space, so marks grow with the zoom like everything
        // else on the ground; drawn above it too, since Create leaves the depth
        // test on and puts its stations five deep.
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(at.x() + at.width() / 2.0, at.y() + at.height() / 2.0, 10);
        pose.scale(scale, scale, 1);
        pose.translate(-mapX, -mapZ, 0);
        graphics.setColor(FastColor.ARGB32.red(ON_ROUTE) / 255F,
            FastColor.ARGB32.green(ON_ROUTE) / 255F, FastColor.ARGB32.blue(ON_ROUTE) / 255F, 1);
        for (Stations.At on : stations)
            if (filters.stream()
                .anyMatch(filter -> filter.test(on.name())))
                station(graphics, on);
        graphics.setColor(1, 1, 1, 1);

        // Panned away from, the map is a network with no you in it.
        here(graphics, minecraft.player.getX(), minecraft.player.getZ(),
            minecraft.player.getYRot());
        pose.popPose();

        // The two rings are the exception, on purpose: they outline what a
        // click will act on, and that reach has a floor in pixels since a
        // station three blocks across is unreachable at route-fitting zoom.
        for (Stations.At on : stations)
            if (linked.test(on.name()))
                marker(graphics, screenX(at, on.x()), screenY(at, on.z()), LINK);

        if (station != null) {
            marker(graphics, screenX(at, station.x()), screenY(at, station.z()),
                (int) Math.ceil(reach() * scale));
            mapTip = List.of(Component.literal(station.name()), CommonComponents.EMPTY,
                hint("create_routes.route.map.add"), hint("create_routes.route.map.copy"));
        }
        graphics.disableScissor();
    }

    /** A station if the cursor is on one, and the map itself otherwise. */
    public boolean grab(double mouseX, double mouseY, int button) {
        if (station != null) {
            if (button == 0) {
                // Its exact name, so a second station called the same is a
                // second answer and not a mistake.
                ScheduleEntry added = new ScheduleEntry();
                DestinationInstruction destination = new DestinationInstruction();
                destination.getData()
                    .putString("Text", station.name());
                added.instruction = destination;
                host.entries()
                    .add(added);
                host.rebuild();
            } else if (button == 1)
                Minecraft.getInstance().keyboardHandler.setClipboard(station.name());
            return true;
        }

        if (button != 0)
            return false;
        // Truncated: the drag that follows is measured against whole pixels,
        // and kept as it came the first frame of every press moved the map by
        // the leftover fraction.
        panning = true;
        grabX = (int) mouseX;
        grabY = (int) mouseY;
        return true;
    }

    /**
     * The wheel, over the map.
     *
     * <p>A block per pixel at 1; the floor shows a line spanning a few thousand
     * blocks at once, and the ceiling (4) is where 1:1 stops adding anything to
     * look at.
     */
    public boolean zoom(double delta) {
        scale = Mth.clamp(scale * (delta > 0 ? 1.5f : 1 / 1.5f), FLOOR, 4f);
        return true;
    }

    /** Where a block stands on screen, which every mark here is placed by. */
    private int screenX(Box at, double worldX) {
        return (int) (at.x() + at.width() / 2.0 + (worldX - mapX) * scale);
    }

    private int screenY(Box at, double worldZ) {
        return (int) (at.y() + at.height() / 2.0 + (worldZ - mapZ) * scale);
    }

    /** How near the cursor has to be to a station to mean it, in blocks. */
    private double reach() {
        return Math.max(REACH / scale, SPRITE);
    }

    /**
     * The player, as vanilla's own map draws them.
     *
     * <p>Turned by the facing plus half a turn: the sprite points up the sheet,
     * but a yaw of zero faces south.
     */
    private static void here(GuiGraphics graphics, double x, double z, float facing) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, z, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(facing + 180));
        graphics.blit(MAP_ICONS, -MAP_ICON / 2, -MAP_ICON / 2, MAP_ICON, MAP_ICON, 0, 0, MAP_ICON,
            MAP_ICON, MAP_SHEET, MAP_SHEET);
        pose.popPose();
    }

    /**
     * Create's own highlight ring, in whatever colour is set.
     *
     * <p>Tinting multiplies, so a coloured sprite would come back a darker
     * version of Create's cream-on-brown texture; the ring is pure white
     * instead, and white multiplied is the colour itself.
     */
    private static void station(GuiGraphics graphics, Stations.At station) {
        AllGuiTextures ring =
            station.rotation() % 2 == 0 ? AllGuiTextures.TRAINMAP_STATION_ORTHO_HIGHLIGHT
                : AllGuiTextures.TRAINMAP_STATION_DIAGO_HIGHLIGHT;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(station.x() - 2, station.z() - 2, 0);
        pose.translate(SPRITE_MIDDLE, SPRITE_MIDDLE, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(90 * (station.rotation() / 2)));
        pose.translate(-SPRITE_MIDDLE, -SPRITE_MIDDLE, 0);
        ring.render(graphics, -1, -1);
        pose.popPose();
    }

    /** A ring around the station a click would mean. */
    private static void marker(GuiGraphics graphics, int x, int y, int reach) {
        graphics.fill(x - reach, y - reach, x + reach + 1, y - reach + 1, MARKER);
        graphics.fill(x - reach, y + reach, x + reach + 1, y + reach + 1, MARKER);
        graphics.fill(x - reach, y - reach, x - reach + 1, y + reach + 1, MARKER);
        graphics.fill(x + reach, y - reach, x + reach + 1, y + reach + 1, MARKER);
    }

    /** The box every station a filter means stands in, in blocks, or null when it means none — zero sized for a single station, which still has a middle. */
    private Box extent(Predicate<String> meant) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Stations.At station : stations) {
            if (!meant.test(station.name()))
                continue;
            minX = Math.min(minX, station.x());
            maxX = Math.max(maxX, station.x());
            minZ = Math.min(minZ, station.z());
            maxZ = Math.max(maxZ, station.z());
        }
        return minX > maxX ? null : new Box(minX, minZ, maxX - minX, maxZ - minZ);
    }

    /** Puts every station a filter means on screen at once, centred and at the zoom that fits them. */
    public void look(Predicate<String> meant) {
        Box over = extent(meant);
        if (over == null || box == null)
            return;
        mapX = over.x() + over.width() / 2.0;
        mapZ = over.y() + over.height() / 2.0;
        // A fifth wider than it has to be, so the outermost station is not
        // standing on the frame.
        float across = Math.max(over.width(), 32) * 1.2f;
        float down = Math.max(over.height(), 32) * 1.2f;
        scale = Mth.clamp(Math.min(box.width() / across, box.height() / down), FLOOR, 1);
    }

    /**
     * Opens on the route rather than on wherever the player happens to be
     * standing.
     *
     * <p>Falls back to the player when nothing matches — a route with no stops
     * yet, or one whose stations are all elsewhere.
     */
    private void frame(Minecraft minecraft, List<Predicate<String>> filters) {
        mapX = minecraft.player.getX();
        mapZ = minecraft.player.getZ();
        look(name -> filters.stream()
            .anyMatch(filter -> filter.test(name)));
    }

    /** Pans while the button is held; {@code ScheduleScreen} declares no {@code mouseDragged}, so this is measured while drawing instead. */
    private void drag(double mouseX, double mouseY) {
        if (!panning)
            return;
        long window = Minecraft.getInstance()
            .getWindow()
            .getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            panning = false;
            return;
        }
        mapX -= (mouseX - grabX) / scale;
        mapZ -= (mouseY - grabY) / scale;
        grabX = mouseX;
        grabY = mouseY;
    }

    /** Ours, in the same voice as Create's own hints. */
    private static Component hint(String key) {
        return Component.translatable(key)
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

}
