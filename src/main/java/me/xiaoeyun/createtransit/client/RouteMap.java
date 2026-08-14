package me.xiaoeyun.createtransit.client;

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

/**
 * Create's own railway map, boxed off from the schedule editing {@link RouteView}
 * does.
 *
 * <p>A pan/zoom state machine that never needed anything of the editing half but
 * two answers: which station names a stop means ({@code filters}, handed in) and
 * which row is under the cursor ({@code hoveredStop}, handed in). Everything else
 * here — where the map is centred, how far zoomed in, which station the cursor is
 * on — is this class's own, and {@link RouteView} reaches it only through
 * {@link #station()}, {@link #grab}, {@link #zoom}, {@link #look} and
 * {@link #tooltip()}.
 */
public class RouteMap {

    /**
     * How near the cursor has to be to a station to mean it: four pixels, or the
     * sprite itself, whichever is the larger.
     *
     * <p>Neither alone is enough. Create measures three blocks, and the station
     * sprite is drawn in blocks too, so the two grow together — but pulled back
     * far enough to see a route, three blocks is a fraction of a pixel and the
     * station cannot be hit at all. A fixed four pixels fixes that end and breaks
     * the other: zoomed right in, the sprite is twenty pixels across and the
     * target sits in the middle of it, so the thing on screen is mostly not
     * clickable.
     */
    private static final int REACH = 4;

    /** Create's own, which is what the sprite it draws is measured in. */
    private static final int SPRITE = 3;

    /**
     * How far back the map may be pulled, in pixels per block.
     *
     * <p>The floor rather than the arrow: every mark on here is measured in
     * blocks, so pulled back far enough they all go under a pixel together, and
     * a map where nothing can be seen is not a view worth being able to reach.
     */
    private static final float FLOOR = 1 / 8f;

    private static final int MARKER = 0xFFFFFFFF;

    /**
     * A station this route stops at, ringed in this colour.
     *
     * <p>The ring is Create's own — the one it puts round a station the cursor
     * is on — because the station cannot be recoloured and a mark of our own
     * drawing would be a second kind of station on a map that has one.
     */
    private static final int ON_ROUTE = 0xFF4C8CFF;

    /** The middle of Create's five by five station sprite, which its ring turns about. */
    private static final float SPRITE_MIDDLE = 2.5F;

    /** How wide a station is ringed when a row names it, in pixels. */
    private static final int LINK = 4;

    /**
     * The ground under the map, dark enough that a rail is the brightest thing
     * on it. Create draws track and nothing else — no terrain — so without this
     * the whole window is the checkered field showing through.
     */
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
     * The station under the cursor, which is ours to work out rather than
     * Create's to tell us.
     *
     * <p>{@code renderAndPick} answers with a line of text and not the station,
     * and it measures the cursor in blocks — three of them — so at the zoom that
     * fits a route on screen its target is a fraction of a pixel. Ours is
     * measured in pixels and stays the same size at every zoom, which is what a
     * thing you are meant to click has to do.
     */
    private Stations.At station;

    /** What the map says is under the cursor, held until the frame's last word. */
    private List<FormattedText> mapTip;

    /**
     * Every station in the world, as it was this frame.
     *
     * <p>Read fresh by {@link #render} and shared by the questions the rest of
     * the frame asks of it — which stations the route touches, which of them the
     * cursor is on, and the box a filter's stations fit in.
     */
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
     * <p>None of this is ours: {@code renderAndPick} draws the track network,
     * the stations and the live trains, and answers what the cursor is over. It
     * takes the visible area in <em>blocks</em> — the sections it culls against
     * are positioned at {@code section * 128} in the same space the pose puts
     * them — so the rectangle below is world coordinates, not screen ones.
     *
     * <p>Ticked here because {@code TrainMapEvents} only ticks for FTB Chunks,
     * JourneyMap and Xaero; with none of them installed the data would never be
     * rebuilt and the map would be empty.
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
        // In blocks, like the bounds: renderAndPick tests the cursor against a
        // station's own position — {@code |mouseX - position.x()| < 3} — which is
        // world space, not the screen space the pose above is drawing in. Given
        // pixels it answers for whatever station happens to stand where the
        // world coordinates read the same as the screen ones, which is nowhere
        // in particular.
        boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        double worldX = mapX + (mouseX - (x + width / 2.0)) / scale;
        double worldZ = mapZ + (mouseY - (y + height / 2.0)) / scale;
        station = over ? Stations.near(stations, worldX, worldZ, reach()) : null;

        // Nothing for Create to pick: a cursor it can never reach. Its own
        // highlight is drawn at its own stricter reach, so left on it appears as
        // a second ring inside ours whenever the map is zoomed in far enough —
        // two marks for one station, one of which is not the one a click means.
        //
        // Nearest-neighbour, because everything on this map is a pixel drawing:
        // linear filtering is what turns a one pixel rail into a smear.
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
     * <p>All of it in screen pixels rather than under the map's own pose, so
     * that a station is the same size to reach for at every zoom — which is the
     * same reason the cursor's own reach is measured in pixels.
     *
     * <p>Called after the table has drawn, so that {@code hoveredStop} is this
     * frame's rather than last frame's — a marker a frame behind the row that
     * asked for it is a marker pointing at where the cursor used to be.
     */
    public void marks(GuiGraphics graphics, Box at, List<Predicate<String>> filters, int hoveredStop) {
        Minecraft minecraft = Minecraft.getInstance();
        if (Double.isNaN(mapX) || minecraft.player == null)
            return;
        graphics.enableScissor(at.x(), at.y(), at.right(), at.bottom());

        // A row lights its stations and a station lights its rows, and one stop
        // may mean four platforms: the filter is the whole of the relation, so
        // there is nothing here to pair up or keep in step.
        Predicate<String> linked =
            hoveredStop >= 0 && hoveredStop < filters.size() ? filters.get(hoveredStop) : name -> false;

        // In the map's own space: everything drawn on the map is measured in
        // blocks and grows with the zoom, and a mark that alone stayed the same
        // size would be the one thing on here that is not lying on the ground.
        // Above it too — Create leaves the depth test on and puts its stations
        // five deep.
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

        // The two rings are the exception, and on purpose: they outline what a
        // click will act on, and that reach has a floor in pixels because a
        // station three blocks across is not reachable at the zoom that fits a
        // whole route on screen.
        for (Stations.At on : stations)
            if (linked.test(on.name()))
                marker(graphics, screenX(at, on.x()), screenY(at, on.z()), LINK);

        if (station != null) {
            marker(graphics, screenX(at, station.x()), screenY(at, station.z()),
                (int) Math.ceil(reach() * scale));
            mapTip = List.of(Component.literal(station.name()), CommonComponents.EMPTY,
                hint("create_transit.route.map.add"), hint("create_transit.route.map.copy"));
        }
        graphics.disableScissor();
    }

    /**
     * A station if the cursor is on one, and the map itself otherwise.
     *
     * <p>No threshold and no delay: a station is a target four pixels across and
     * everything around it is somewhere to take hold of. The two never want the
     * same press, so neither has to wait to find out what the other meant.
     *
     * <p>Whether the cursor is idle over the map at all is the caller's to ask —
     * that is a question about the schedule editor's own layout and editing
     * state, not about the map.
     */
    public boolean grab(double mouseX, double mouseY, int button) {
        if (station != null) {
            if (button == 0) {
                // Its exact name, so a second station called the same is a second
                // answer and not a mistake — which is what the filter means, and
                // what the navigation does with it.
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
        // Truncated, because the drag that follows is measured against the whole
        // pixels {@code render} is given while a press arrives with the fraction
        // still on it. Kept as it came, the first frame of every press moved the
        // map by that fraction — always the same way, since dropping the tail of
        // a positive number always makes the difference negative.
        panning = true;
        grabX = (int) mouseX;
        grabY = (int) mouseY;
        return true;
    }

    /**
     * The wheel, over the map.
     *
     * <p>A block per pixel at 1; the lower bound is what it takes to see a line
     * that spans a few thousand blocks at once. The upper one is four because the
     * map is a pixel a block and nothing past 1:1 adds anything to look at — only
     * Create's station sprite, which is drawn in blocks, grows with it.
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
     * <p>Sprite zero of {@code map_icons}, which is the marker every player has
     * already learnt to look for — and it points, which a dot cannot.
     *
     * <p>Eight blocks across, which is what it is worth on a vanilla map too:
     * eight pixels of a map that draws a block to the pixel.
     *
     * <p>Turned by the facing plus half a turn: the sprite points up the sheet,
     * up the map is north, and a yaw of zero is south. Vanilla's own map does
     * the same sum on the way in, where it packs the facing into sixteenths.
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
     * <p>The station itself cannot be recoloured. Tinting multiplies, and its
     * sprite is cream on brown — every colour asked of it comes back a darker
     * yellow, which is how a blue station came out the colour of mud. The ring
     * is seven by seven of pure white, and white multiplied is the colour
     * itself.
     *
     * <p>Turned like the station it goes round: the sprite is drawn to the track
     * and there are two of them, one squared to the world and one at forty five
     * degrees, so a ring drawn straight sits crooked on half the stations there
     * are. The sum that chooses is Create's, copied where the station is read.
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

    /**
     * A ring around the station a click would mean.
     *
     * <p>Drawn in screen space and at exactly the reach that found it, so that
     * the ring is not a decoration near the station but the outline of what a
     * click will act on.
     */
    private static void marker(GuiGraphics graphics, int x, int y, int reach) {
        graphics.fill(x - reach, y - reach, x + reach + 1, y - reach + 1, MARKER);
        graphics.fill(x - reach, y + reach, x + reach + 1, y + reach + 1, MARKER);
        graphics.fill(x - reach, y - reach, x - reach + 1, y + reach + 1, MARKER);
        graphics.fill(x + reach, y - reach, x + reach + 1, y + reach + 1, MARKER);
    }

    /**
     * The box every station a filter means stands in, in blocks, or null when
     * it means none of them.
     *
     * <p>Zero sized for a single station, which is a box all the same — what
     * asks for this wants a middle, and a route of one stop has one.
     */
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

    /**
     * Puts every station a filter means on screen at once, centred and at the
     * zoom that fits them.
     *
     * <p>The zoom moves too. A view the player panned to is worth less than it
     * sounds — the window is a hundred pixels tall and a route is thousands of
     * blocks, so at any zoom that shows a station there is no route on screen to
     * have kept your place in.
     */
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
     * <p>A hundred pixels at scale one is a hundred blocks, and a route is
     * thousands — so centred on the player it showed the ground under their feet
     * and none of the line they came here to edit. Framed on the stations the
     * stops mean, the first thing the window says is what the route looks like.
     *
     * <p>Falls back to the player when nothing matches, which is a route with no
     * stops yet or one whose stations are all somewhere else. Zoom is capped at
     * one either way: a single stop has no extent to fit, and filling the window
     * with one station would be a lie about how much there is.
     */
    private void frame(Minecraft minecraft, List<Predicate<String>> filters) {
        mapX = minecraft.player.getX();
        mapZ = minecraft.player.getZ();
        look(name -> filters.stream()
            .anyMatch(filter -> filter.test(name)));
    }

    /**
     * Pans while the button is held.
     *
     * <p>{@code ScheduleScreen} declares no {@code mouseDragged} and a mixin
     * cannot inject into a method its target does not declare, so the drag is
     * measured while drawing instead — the button is asked of the window
     * directly.
     */
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
