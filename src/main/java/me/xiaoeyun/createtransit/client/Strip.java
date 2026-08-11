package me.xiaoeyun.createtransit.client;

import java.util.List;

import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.createmod.catnip.gui.TextureSheetSegment;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The run of buttons at the end of a row, drawn and recorded in one pass.
 *
 * <p>How many there are was written in four places before this — twice in the
 * stops table, and twice in the route list, where the drawing and the clicking
 * each kept their own copy. A fifth button would have had to be remembered in
 * all four, and the one that was forgotten would be the one that answers to a
 * click nobody can see.
 *
 * <p>Slots are counted from the right because that is the edge they are pinned
 * to; an empty one still takes its place, so a row missing a button does not
 * shuffle the others along.
 */
public class Strip {

    /** A slot is a row tall and a row wide, so a glyph sits square in it. */
    public static final int SLOT = CtSkin.ROW_HEIGHT;

    /**
     * What an icon measures. {@code AllIcons} blits a fixed square and keeps the
     * number to itself — only the atlas size is public — so it is written here
     * rather than reached for.
     */
    private static final int ICON = 16;

    /**
     * One of Create's own buttons: its plate, and the icon a pixel inside it.
     *
     * <p>Six lines of {@code IconButton.doRender}, which is the whole of what a
     * button on a plaque needed from it. Not a slot in a strip — the two exits
     * are a pair on a footer, not a run of glyphs at the end of a row, and a
     * plate is exactly what tells them apart from one.
     */
    public static void plate(GuiGraphics graphics, int x, int y, ScreenElement icon, boolean hovered) {
        (hovered ? AllGuiTextures.BUTTON_HOVER : AllGuiTextures.BUTTON).render(graphics, x, y);
        icon.render(graphics, x + 1, y + 1);
    }

    /** How much room a strip of this many slots needs. */
    public static int width(int slots) {
        return SLOT * slots;
    }

    /**
     * Which slot a point falls in, or -1 for none of them.
     *
     * <p>The one piece of arithmetic that drawing and clicking both need, so it
     * is written once. In the route list it was written three times — where the
     * glyphs go, where the cursor counts as being on one, and where a click
     * lands — and the three had to be changed together.
     */
    public static int slotAt(int left, int slots, double x) {
        int slot = (int) Math.floor((x - left) / SLOT);
        return slot >= 0 && slot < slots ? slot : -1;
    }

    private final GuiGraphics graphics;

    private final int left;

    private final int y;

    private int slot;

    private Strip(GuiGraphics graphics, int left, int y) {
        this.graphics = graphics;
        this.left = left;
        this.y = y;
    }

    /** A strip of {@code slots} whose last slot ends at {@code right}. */
    public static Strip endingAt(GuiGraphics graphics, int right, int y, int slots) {
        return new Strip(graphics, right - width(slots), y);
    }

    /** Where the strip starts, which is where whatever precedes it has to stop. */
    public int left() {
        return left;
    }

    /**
     * One button, its glyph centred at whatever size the sheet says it is.
     *
     * <p>Create's card glyphs are 12 and its icons are 16, and the sheet knows
     * which — {@code AllGuiTextures} carries its own measurements and
     * {@code AllIcons} is the atlas's fixed square. Neither is the caller's to
     * state, and the two that were stated here were both written as a bare 12.
     */
    public Strip button(ScreenElement glyph) {
        int size = glyph instanceof TextureSheetSegment sheet ? sheet.getWidth() : ICON;
        int x = left + slot * SLOT;
        int inset = (SLOT - size) / 2;

        glyph.render(graphics, x + inset, y + inset);
        return blank();
    }

    /** A slot left empty, so what follows still lands where it always does. */
    public Strip blank() {
        slot++;
        return this;
    }

}
