package me.xiaoeyun.createnestnetwork.content.transit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The transit label side of a package port's address, edited as structure
 * rather than as text.
 *
 * The port's own address box keeps holding nothing but the path, and the label
 * lives in this panel with a field of its own; the wire string is only ever
 * assembled on the way out and taken apart on the way in. That is what keeps
 * the vanilla 25 character limit meaning one thing — it now applies to each
 * field the player can actually see, and never to the joined address, which
 * never passes through an {@link EditBox} to be silently truncated by it.
 *
 * Typing a label here and typing {@code <[name]> path} into the address box by
 * hand produce the same string, so neither way of working is privileged.
 */
@OnlyIn(Dist.CLIENT)
public class PortLabelEditor {

    private static final int PANEL_WIDTH = 124;
    private static final int PANEL_PADDING = 6;
    private static final int ROW_HEIGHT = 20;
    private static final int EXTRA_ROW_HEIGHT = 11;
    private static final int BUTTON_SIZE = 18;

    /** The same ceiling every address field in Create carries. */
    private static final int LABEL_LIMIT = 25;

    private static final int PANEL_BORDER = 0xFF3A3A47;
    private static final int PANEL_FILL = 0xFF23222B;
    private static final int FIELD_FILL = 0xFF13121A;
    private static final int TITLE_COLOR = 0xC0BDD4;
    private static final int HINT_COLOR = 0x5A5766;

    private final Font font;
    private final List<String> labels;

    private final IconButton toggleButton;
    private final IconButton removeButton;
    private final IconButton addButton;
    private final EditBox nameBox;

    private final int panelX;
    private final int panelY;

    private boolean open;
    private boolean focusRequested;
    /** Guards the editor's own writes into the box from bouncing back. */
    private boolean syncing;

    public PortLabelEditor(Font font, String address, int guiLeft, int guiTop, int windowWidth, int windowHeight,
        Consumer<AbstractWidget> register) {
        this.font = font;
        labels = new ArrayList<>(AddressLabels.labelNames(address));

        panelX = guiLeft + windowWidth + 4;
        panelY = guiTop + 6;

        toggleButton = new IconButton(guiLeft + 77, guiTop + windowHeight - 24, AllIcons.I_CONFIG_OPEN);
        toggleButton.withCallback(this::toggle);

        removeButton = new IconButton(panelX + PANEL_PADDING, rowY(0), AllIcons.I_TRASH);
        removeButton.withCallback(this::removeHeadLabel);
        removeButton.setToolTip(Component.translatable("create_nest_network.package_port.remove_label"));

        addButton = new IconButton(panelX + PANEL_PADDING, rowY(1), AllIcons.I_ADD);
        addButton.withCallback(this::addLabel);
        addButton.setToolTip(Component.translatable("create_nest_network.package_port.add_label"));

        nameBox = new EditBox(font, panelX + PANEL_PADDING + 22, rowY(0) + 5, PANEL_WIDTH - PANEL_PADDING * 2 - 24, 10,
            Component.empty());
        nameBox.setBordered(false);
        nameBox.setTextColor(0xEEEEEE);
        nameBox.setMaxLength(LABEL_LIMIT);
        // The closing delimiter would end the token early, so a name can never
        // hold one — rejecting it as it is typed beats silently editing it away.
        nameBox.setFilter(text -> !text.contains(AddressLabels.CLOSE));
        nameBox.setResponder(this::onNameTyped);

        register.accept(toggleButton);
        register.accept(removeButton);
        register.accept(addButton);
        register.accept(nameBox);

        syncBoxFromModel();
        refreshWidgets();
    }

    /** The wire address for a path the player left in the port's address box. */
    public String compose(String path) {
        return AddressLabels.compose(labels, path);
    }

    /** The head label as it would read inside an address, or null if there is none. */
    public Component headLabelChip() {
        if (labels.isEmpty())
            return null;
        String name = AddressLabels.sanitizeName(labels.get(0));
        return name.isEmpty() ? null : Component.literal(AddressLabels.OPEN + name + AddressLabels.CLOSE);
    }

    public void tick() {
        nameBox.tick();
        refreshWidgets();
    }

    /**
     * The field to hand the keyboard to, once.
     *
     * A widget cannot focus another one while it is being clicked — the screen
     * assigns focus to whatever handled the click once the callback returns, so
     * the add button would take it straight back. Asking a tick later is what
     * lets the player press + and simply start typing.
     */
    @Nullable
    public EditBox takePendingFocus() {
        if (!focusRequested)
            return null;
        focusRequested = false;
        return nameBox;
    }

    /**
     * Escape closes the panel instead of the whole screen — a player reaching
     * for it means the label edit, not the port.
     */
    public boolean escape() {
        if (!open)
            return false;
        setOpen(false);
        return true;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        toggleButton.render(graphics, mouseX, mouseY, partialTicks);
        if (!open)
            return;

        Rect2i panel = panelArea();
        graphics.fill(panel.getX() - 1, panel.getY() - 1, panel.getX() + panel.getWidth() + 1,
            panel.getY() + panel.getHeight() + 1, PANEL_BORDER);
        graphics.fill(panel.getX(), panel.getY(), panel.getX() + panel.getWidth(),
            panel.getY() + panel.getHeight(), PANEL_FILL);

        graphics.drawString(font, Component.translatable("create_nest_network.package_port.transit_label"),
            panelX + PANEL_PADDING, panelY + PANEL_PADDING, TITLE_COLOR, false);

        if (!labels.isEmpty())
            graphics.fill(nameBox.getX() - 3, nameBox.getY() - 3, nameBox.getX() + nameBox.getWidth() + 1,
                nameBox.getY() + 12, FIELD_FILL);

        removeButton.render(graphics, mouseX, mouseY, partialTicks);
        addButton.render(graphics, mouseX, mouseY, partialTicks);
        nameBox.render(graphics, mouseX, mouseY, partialTicks);

        if (labels.isEmpty())
            graphics.drawString(font, Component.translatable("create_nest_network.package_port.no_label")
                .withStyle(ChatFormatting.DARK_GRAY), panelX + PANEL_PADDING + 22, rowY(0) + 6, HINT_COLOR, false);

        graphics.drawString(font, Component.translatable(labels.isEmpty()
            ? "create_nest_network.package_port.add_label"
            : "create_nest_network.package_port.one_label_only")
            .withStyle(ChatFormatting.DARK_GRAY), panelX + PANEL_PADDING + 22, rowY(1) + 6, HINT_COLOR, false);

        renderInheritedLabels(graphics);
    }

    /**
     * Labels past the first are shown but not edited. Only the head label takes
     * part in matching, so the rest are somebody else's structure passing
     * through — listing them keeps the address honest, and deleting the head
     * promotes the next one when a player really wants them gone.
     */
    private void renderInheritedLabels(GuiGraphics graphics) {
        if (inheritedLines() == 0)
            return;

        int y = rowY(2);
        graphics.drawString(font, Component.translatable("create_nest_network.package_port.nested_labels")
            .withStyle(ChatFormatting.DARK_GRAY), panelX + PANEL_PADDING, y, HINT_COLOR, false);

        for (int i = 1; i < labels.size(); i++) {
            y += EXTRA_ROW_HEIGHT;
            graphics.drawString(font, AddressLabels.OPEN + labels.get(i) + AddressLabels.CLOSE,
                panelX + PANEL_PADDING + 4, y, HINT_COLOR, false);
        }
    }

    /** The panel's screen area, so JEI keeps its ingredients out of it. */
    public Rect2i panelArea() {
        int extraLines = inheritedLines();
        int bottom = extraLines == 0 ? rowY(1) + BUTTON_SIZE : rowY(2) + extraLines * EXTRA_ROW_HEIGHT;
        return new Rect2i(panelX, panelY, PANEL_WIDTH, bottom + PANEL_PADDING - panelY);
    }

    /** One heading plus one line per label the panel will not edit. */
    private int inheritedLines() {
        return labels.size() < 2 ? 0 : labels.size();
    }

    public boolean isOpen() {
        return open;
    }

    private void toggle() {
        setOpen(!open);
    }

    private void setOpen(boolean open) {
        this.open = open;
        if (!open)
            nameBox.setFocused(false);
        refreshWidgets();
    }

    private void addLabel() {
        if (!labels.isEmpty())
            return;
        labels.add("");
        syncBoxFromModel();
        refreshWidgets();
        focusRequested = true;
    }

    private void removeHeadLabel() {
        if (labels.isEmpty())
            return;
        labels.remove(0);
        syncBoxFromModel();
        refreshWidgets();
    }

    private void onNameTyped(String text) {
        if (syncing || labels.isEmpty())
            return;
        labels.set(0, text);
    }

    private void syncBoxFromModel() {
        syncing = true;
        nameBox.setValue(labels.isEmpty() ? "" : labels.get(0));
        syncing = false;
        // The field clamps exactly as every other address field does, and the
        // responder is handed what setValue was called with rather than what it
        // kept, so the label is read back off the box to keep the two the same.
        if (!labels.isEmpty())
            labels.set(0, nameBox.getValue());
    }

    private void refreshWidgets() {
        boolean hasRow = !labels.isEmpty();
        removeButton.visible = open && hasRow;
        addButton.visible = open;
        addButton.active = !hasRow;
        nameBox.setVisible(open && hasRow);

        // A row holding a blank name produces no label, so the button reports
        // what the port will actually be addressed as, not what is on screen.
        Component chip = headLabelChip();
        toggleButton.green = chip != null;

        List<Component> tooltip = toggleButton.getToolTip();
        tooltip.clear();
        tooltip.add(Component.translatable("create_nest_network.package_port.transit_label"));
        if (chip != null)
            tooltip.add(chip.copy()
                .withStyle(ChatFormatting.GRAY));
    }

    private int rowY(int row) {
        return panelY + PANEL_PADDING + 14 + row * ROW_HEIGHT;
    }

}
