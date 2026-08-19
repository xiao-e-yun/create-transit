package me.xiaoeyun.createroutes.client;

import com.simibubi.create.foundation.gui.AllGuiTextures;

import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;

/** The run of buttons at the end of a row, drawn and recorded in one pass. */
public class Strip {

    /** A slot is a row tall and a row wide, so a glyph sits square in it. */
    public static final int SLOT = CtSkin.ROW_HEIGHT;

    /** What an icon measures; {@code AllIcons} keeps this private and exposes only the atlas size. */
    private static final int ICON = 16;

    /** One of Create's own buttons: its plate, and the icon a pixel inside it, copied out of {@code IconButton.doRender}. */
    public static void plate(GuiGraphics graphics, int x, int y, ScreenElement icon, boolean hovered) {
        (hovered ? AllGuiTextures.BUTTON_HOVER : AllGuiTextures.BUTTON).render(graphics, x, y);
        icon.render(graphics, x + 1, y + 1);
    }

    /** How much room a strip of this many slots needs. */
    public static int width(int slots) {
        return SLOT * slots;
    }

    /** Which slot a point falls in, or -1 for none of them. */
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

    /** A strip whose first slot starts at {@code left}, which is the same number {@link #slotAt} reads from. */
    public static Strip startingAt(GuiGraphics graphics, int left, int y) {
        return new Strip(graphics, left, y);
    }

    /** One button, its glyph centred in the slot. */
    public Strip button(ScreenElement glyph) {
        int x = left + slot * SLOT;
        int inset = (SLOT - ICON) / 2;

        glyph.render(graphics, x + inset, y + inset);
        return blank();
    }

    /** A slot left empty, so what follows still lands where it always does. */
    public Strip blank() {
        slot++;
        return this;
    }

}
