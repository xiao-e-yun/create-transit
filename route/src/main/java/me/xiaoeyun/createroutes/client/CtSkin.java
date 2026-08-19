package me.xiaoeyun.createroutes.client;

import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** The schedule window's chrome at any size, as flat fills measured off Create's one fixed 256x226 {@code schedule.png}. */
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

    /** White at low alpha rather than fixed greys, so they read the same over whatever they land on. */
    private static final int ROW_BAND = 0x33FFFFFF;
    private static final int ROW_SELECTED = 0x66FFFFFF;

    /**
     * Measured down column 40 of schedule.png: black y0, white y1, the face to
     * y12, its shadow y13, black y14, the field border y15, checker from y16.
     */
    public static final int PLAQUE_HEIGHT = 13;

    /** Where things sit on {@code AllGuiTextures.SCHEDULE} — Create's numbers, taken from {@code ScheduleScreen}, not measured off the picture. */
    static final int LIST_AT = 16;

    static final int LIST_WIDTH = 220;

    static final int LIST_HEIGHT = 173;

    static final int CONFIRM_X = 214;

    static final int CONFIRM_Y = 196;

    static final int CYCLIC_X = 21;

    static final int CYCLIC_Y = 196;

    /** Both sockets are one IconButton square. */
    static final int BUTTON_SIZE = 18;

    /** What a cut column ends with — one glyph, costing four pixels of the room. */
    private static final String ELLIPSIS = "…";

    /** A table row's height, sized around a 16px item icon that is only sharp at 1:1 on an integer GUI scale. */
    public static final int ROW_HEIGHT = 18;

    /** Dark on the plaque, light on the field — the two places text can land. */
    public static final int PLAQUE_TEXT = 0x3D3C48;
    public static final int FIELD_TEXT = 0xFFFFFF;
    public static final int MUTED_TEXT = 0xA6A6A6;

    /** The largest square of pure checker in the sheet; even-sided, so tiling cannot shift the pattern's phase. */
    private static final int CHECKER_U = 16;
    private static final int CHECKER_V = 16;
    private static final int CHECKER_SIZE = 128;

    private CtSkin() {}

    /** How tall a window has to be to give its content that much room — the inverse of what {@link #frame} returns. */
    public static int windowHeight(int content) {
        return PLAQUE_HEIGHT + content + 2;
    }

    /**
     * A titled window, optionally standing its buttons on a footer; returns the
     * area inside it that content may use.
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
     * Create's own schedule panel, whole, centred on a screen this size.
     *
     * <p>The cyclic socket and the confirm tick are painted into the sheet: the socket is filled back
     * over (a route has no cyclic schedule), and a live plate laid over the tick, which cannot light up
     * under the cursor by itself.
     *
     * @return the panel's origin and size, for whatever is drawn relative to it
     */
    public static Box schedulePanel(GuiGraphics graphics, Font font, Component title, int screenWidth,
        int screenHeight, double mouseX, double mouseY) {
        AllGuiTextures panel = AllGuiTextures.SCHEDULE;
        int x = (screenWidth - panel.getWidth()) / 2;
        int y = (screenHeight - panel.getHeight()) / 2;
        panel.render(graphics, x, y);
        graphics.drawString(font, title, x + (panel.getWidth() - 8) / 2 - font.width(title) / 2, y + 4,
            PLAQUE_TEXT, false);

        graphics.fill(x + CYCLIC_X, y + CYCLIC_Y, x + CYCLIC_X + BUTTON_SIZE, y + CYCLIC_Y + BUTTON_SIZE,
            PLAQUE);

        Box confirm = confirm(x, y);
        Strip.plate(graphics, confirm.x(), confirm.y(), AllIcons.I_CONFIRM, confirm.holds(mouseX, mouseY));

        return new Box(x, y, panel.getWidth(), panel.getHeight());
    }

    /** Where a panel's confirm button is, for a caller that wants its own hit test. */
    public static Box confirm(int panelX, int panelY) {
        return new Box(panelX + CONFIRM_X, panelY + CONFIRM_Y, BUTTON_SIZE, BUTTON_SIZE);
    }

    /** A raised plate, measured off a plaque row of {@code create:textures/gui/schedule.png}, columns x13..238. */
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

    /** One row of a table: lit when selected, banded when odd, and otherwise left alone. */
    public static void row(GuiGraphics graphics, int x, int y, int width, int height, int index,
        boolean selected) {
        if (selected)
            graphics.fill(x, y, x + width, y + height, ROW_SELECTED);
        else if (index % 2 == 1)
            graphics.fill(x, y, x + width, y + height, ROW_BAND);
    }

    /** The same text, cut to a width with a mark where it was cut, keeping whatever style it had. */
    public static Component clip(Font font, Component text, int width) {
        if (font.width(text) <= width)
            return text;
        String cut = font.plainSubstrByWidth(text.getString(), width - font.width(ELLIPSIS));
        return Component.literal(cut + ELLIPSIS)
            .withStyle(text.getStyle());
    }

    /** Text in a column that ends, with a mark where it was cut. */
    public static void clipped(GuiGraphics graphics, Font font, Component text, int x, int y, int width,
        int colour) {
        graphics.drawString(font, clip(font, text, width), x, y, colour, false);
    }

    /** Ours, in the same voice as Create's own hints. */
    public static Component hint(String key) {
        return Component.translatable(key)
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    /** The line under a table's headings, which is all a heading row gets. */
    public static void rule(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, HIGHLIGHT);
    }

    /** Tiles the sheet's checkered field. Clipped rather than scaled: a one-pixel pattern smears the moment it is stretched. */
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
