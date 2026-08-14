package me.xiaoeyun.createtransit.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The schedule window's chrome, at whatever size it is asked for.
 *
 * <p>Create ships that background as one 256x226 image with its title plaque,
 * checkered field and footer baked in, so it cannot be resized and slicing it
 * apart leaves visible seams. Every band of it is a solid colour though, so
 * measuring them and drawing them back is both exact and free of texture
 * bookkeeping. Only the checker is still a texture, because a one-pixel pattern
 * is cheaper to tile than to fill.
 *
 * <p>Uniform along its width it is <em>not</em> — that was written here once and
 * it was wrong, and the window was flat for as long as it stood. The panel in
 * {@code create:textures/gui/schedule.png} runs x13..238, and a plaque row of it
 * reads black, two of white, the face, two of #555555, black: a plate lit from
 * the top left, not a stack of bands. The field below is the same idea inverted,
 * #787878 along its top and bottom and the lighter #B8B8B8 down its sides.
 *
 * <p>Vertically, column 40: black at y0, white at y1, the face to y12, its
 * shadow at y13, black at y14, the field's border at y15 and the checker from
 * y16. The plate here is two rows shorter in the face so that the outline costs
 * the window nothing — the title bar is the same 13 it always was.
 *
 * <p>Tables are the exception: they are not Create's, they are the departure
 * board's. Nothing is drawn for a table but a rule under its headings and a
 * translucent band on every other row, so the field shows through and the list
 * stays as tall as its text. Those two are the whole style.
 */
public class CtSkin {

    private static final ResourceLocation SCHEDULE =
        ResourceLocation.fromNamespaceAndPath("create", "textures/gui/schedule.png");

    private static final int HIGHLIGHT = 0xFFFFFFFF;
    /** Also the sheet's own footer face, which is why it is not private. */
    public static final int PLAQUE = 0xFFC6C6C6;
    private static final int SHADOW = 0xFF555555;
    private static final int BORDER = 0xFF787878;
    private static final int OUTLINE = 0xFF000000;
    /** The field's sides, which are lighter than its top and bottom. */
    private static final int FIELD_EDGE = 0xFFB8B8B8;

    /** The outline and the pixel of edging inside it, on every side. */
    private static final int RIM = 2;

    /**
     * Banding and selection are white at low alpha rather than three fixed
     * greys, so they read the same over the checkered field, over a header, and
     * over whatever a window is given next.
     */
    private static final int ROW_BAND = 0x33FFFFFF;
    private static final int ROW_SELECTED = 0x66FFFFFF;

    public static final int PLAQUE_HEIGHT = 13;

    /** The first usable row inside a window whose frame starts at y. */
    public static final int BODY_TOP = PLAQUE_HEIGHT + 1;

    /** What a cut column ends with. One glyph, so it costs four pixels of the room. */
    private static final String ELLIPSIS = "…";

    /**
     * A table row's height, which an item icon decides: a stack renders 16 wide
     * and 16 tall and does not scale without going soft. Text is centred in it
     * rather than the row being shrunk to the text.
     *
     * <p>The icon and one clear pixel either side of it. The icon itself stays
     * 16, because the GUI scale is always a whole number and 1:1 is the only
     * ratio that is sharp at every one of them — three quarters of 16 lands on
     * half a physical pixel at scale 2 and a quarter of one at scale 3. So the
     * gap comes out of the row, never out of the icon.
     */
    public static final int ROW_HEIGHT = 18;

    /** Dark on the plaque, light on the field — the two places text can land. */
    public static final int PLAQUE_TEXT = 0x3D3C48;
    public static final int FIELD_TEXT = 0xFFFFFF;
    public static final int MUTED_TEXT = 0xA6A6A6;

    /**
     * The largest square of pure checker in the sheet, and even-sided so that
     * tiling it cannot shift the pattern's phase.
     */
    private static final int CHECKER_U = 16;
    private static final int CHECKER_V = 16;
    private static final int CHECKER_SIZE = 128;

    private CtSkin() {}

    /**
     * How tall a window has to be to give its content that much room — the
     * inverse of what {@link #frame} returns, for a layout that has to size
     * itself before anything is drawn.
     */
    public static int windowHeight(int content) {
        return PLAQUE_HEIGHT + content + 2;
    }

    /**
     * A titled window, optionally standing its buttons on a footer. Returns the
     * area inside it that content may use.
     *
     * <p>A box rather than a y, because the rim is two pixels now and was one
     * before. Every caller that answered that question for itself had to be
     * found and changed when it moved; the ones that ask get it right by not
     * knowing.
     *
     * @param footer how tall a strip to reserve at the foot, or zero for none
     */
    public static Box frame(GuiGraphics graphics, Font font, int x, int y, int width, int height,
        Component title, int footer) {
        plate(graphics, x, y, width, PLAQUE_HEIGHT);
        graphics.drawString(font, title, x + (width - font.width(title)) / 2, y + 2, PLAQUE_TEXT, false);

        int fieldY = y + PLAQUE_HEIGHT;
        int fieldHeight = height - PLAQUE_HEIGHT - footer;
        field(graphics, x, fieldY, width, fieldHeight);
        if (footer > 0)
            plate(graphics, x, fieldY + fieldHeight, width, footer);

        return new Box(x + RIM, fieldY + 1, width - RIM * 2, fieldHeight - 2);
    }

    /**
     * A raised plate: an outline, a lit top and left, a shaded bottom and right,
     * and the face between them. The title bar and the footer are both one.
     *
     * <p>The lighting is not symmetric and neither is Create's — one row along
     * the top and bottom, two columns down the sides. Matching it matters more
     * than tidying it, because the two are drawn thirty pixels apart.
     */
    private static void plate(GuiGraphics graphics, int x, int y, int width, int height) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, bottom, OUTLINE);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 1, PLAQUE);
        graphics.fill(x + 1, y + 1, right - 1, y + 2, HIGHLIGHT);
        graphics.fill(x + 1, y + 2, x + 3, bottom - 2, HIGHLIGHT);
        graphics.fill(x + 1, bottom - 2, right - 1, bottom - 1, SHADOW);
        graphics.fill(right - 3, y + 2, right - 1, bottom - 2, SHADOW);
    }

    /** The checkered field, sunk into the window the way the plate stands out of it. */
    private static void field(GuiGraphics graphics, int x, int y, int width, int height) {
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x, y, right, bottom, OUTLINE);
        graphics.fill(x + 1, y, right - 1, bottom, FIELD_EDGE);
        graphics.fill(x + 1, y, right - 1, y + 1, BORDER);
        graphics.fill(x + 1, bottom - 1, right - 1, bottom, BORDER);
        checker(graphics, x + RIM, y + 1, width - RIM * 2, height - 2);
    }

    /**
     * One row of a table. Odd rows are banded and even ones are left alone, so
     * half the list costs nothing to draw and the field still shows through.
     *
     * <p>Create draws a schedule entry as a card — a rimmed, inset plate with a
     * gap either side. That reads well for four entries and badly for twenty,
     * because every card spends four pixels of height on its own edges and two
     * more on the gap. Banding tells rows apart with none of it, so they can
     * touch and the whole list gets shorter.
     */
    public static void row(GuiGraphics graphics, int x, int y, int width, int height, int index,
        boolean selected) {
        if (selected)
            graphics.fill(x, y, x + width, y + height, ROW_SELECTED);
        else if (index % 2 == 1)
            graphics.fill(x, y, x + width, y + height, ROW_BAND);
    }

    /**
     * Text in a column that ends, with a mark where it was cut.
     *
     * <p>A bare stop is worse than no room at all: "Cargo ≥ 1 st" and "貨物 ≥ 1"
     * are read as what the field says rather than as what is left of it, and a
     * player acts on the wrong number without ever knowing there was more. The
     * ellipsis costs four pixels and is the only thing that says so.
     */
    public static void clipped(GuiGraphics graphics, Font font, Component text, int x, int y, int width,
        int colour) {
        if (font.width(text) <= width) {
            graphics.drawString(font, text, x, y, colour, false);
            return;
        }
        String cut = font.plainSubstrByWidth(text.getString(), width - font.width(ELLIPSIS));
        graphics.drawString(font, cut + ELLIPSIS, x, y, colour, false);
    }

    /** The line under a table's headings, which is all a heading row gets. */
    public static void rule(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, HIGHLIGHT);
    }

    /**
     * Tiles the sheet's checkered field. Clipped rather than scaled, because a
     * one-pixel pattern turns to mush the moment it is stretched.
     */
    private static void checker(GuiGraphics graphics, int x, int y, int width, int height) {
        for (int dy = 0; dy < height; dy += CHECKER_SIZE) {
            int tileHeight = Math.min(CHECKER_SIZE, height - dy);
            for (int dx = 0; dx < width; dx += CHECKER_SIZE) {
                int tileWidth = Math.min(CHECKER_SIZE, width - dx);
                graphics.blit(SCHEDULE, x + dx, y + dy, CHECKER_U, CHECKER_V, tileWidth, tileHeight, 256, 256);
            }
        }
    }

}
