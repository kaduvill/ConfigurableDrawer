package com.kaduvill.configurabledrawer.client;

import java.awt.Rectangle;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.kaduvill.configurabledrawer.client.render.DrawerPreview;
import com.kaduvill.configurabledrawer.drawer.DrawerNumberFormat;
import com.kaduvill.configurabledrawer.drawer.DrawerStorage;
import com.kaduvill.configurabledrawer.drawer.SideRules;
import com.kaduvill.configurabledrawer.menu.ContainerDrawer;
import com.kaduvill.configurabledrawer.menu.DrawerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiUtils;
import org.lwjgl.input.Keyboard;

public final class GuiDrawer extends GuiContainer {
    public final ContainerDrawer drawer;
    private static final int STORAGE = 0, SIDES = 1, OPTIONS = 2;
    private int view;
    private boolean draggingPreview;
    private boolean rightOptionClick;
    private GuiTextField capacity, threshold;
    private long displayedCapacity = -1, displayedThreshold = -1;
    private boolean thresholdEdited;
    private static final int[] SIDE_CELLS = {4, 1, 5, 7, 3, 8};
    private static final String[] SIDE_KEYS = {"front", "top", "right", "bottom", "left", "back", "unsided"};
    private int dragX, dragY;
    private float previewYaw, previewPitch = 20;
    private int lastShiftClickSlot = -1;
    private long lastShiftClickTime;



    public GuiDrawer(ContainerDrawer drawer) {
        super(drawer); this.drawer = drawer; xSize = 256; ySize = 222;
        previewYaw = drawer.tile.front().getHorizontalAngle() + 25;
    }
    @Override public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        capacity = new GuiTextField(0, fontRenderer, guiLeft + 16, guiTop + 88, 176, 18);
        capacity.setMaxStringLength(19);
        capacity.setValidator(text -> text.matches("[0-9]{0,19}"));
        capacity.setText(Long.toString(drawer.shownCapacity));
        threshold = new GuiTextField(1, fontRenderer, guiLeft + 16, guiTop + 108, 224, 18) {
            @Override public void writeText(String text) {
                if ("0".equals(getText()) && text.matches("[0-9]+")) {
                    setCursorPositionEnd();
                    setSelectionPos(0);
                }
                super.writeText(text);
            }
        };
        threshold.setMaxStringLength(19);
        threshold.setValidator(text -> text.matches("[0-9]{0,19}"));
        threshold.setText(Long.toString(drawer.shownThreshold));
        thresholdEdited = false;
        displayedThreshold = drawer.shownThreshold;
        displayedCapacity = drawer.shownCapacity;
        buttonList.add(new GuiButton(0, guiLeft + 200, guiTop + 87, 40, 20, I18n.format("configurabledrawer.apply")));
        buttonList.add(new GuiButton(2, guiLeft + 200, guiTop + 27, 40, 20, I18n.format("configurabledrawer.take")));
        // TABS
        int tabY = Math.max(0, guiTop - 12);
        buttonList.add(new DrawerTab(3, guiLeft + 82, tabY, 52,
                I18n.format("configurabledrawer.storage")));
        buttonList.add(new DrawerTab(4, guiLeft + 136, tabY, 48,
                I18n.format("configurabledrawer.sides")));
        buttonList.add(new DrawerTab(5, guiLeft + 186, tabY, 54,
                I18n.format("configurabledrawer.options")));

        for (int entry = 0; entry < SIDE_CELLS.length; entry++) {
            int cell = SIDE_CELLS[entry];
            buttonList.add(new SideButton(entry, guiLeft + 16 + cell % 3 * 31,
                    guiTop + 32 + cell / 3 * 24));
        }
        buttonList.add(new SideButton(SideRules.UNSIDED, guiLeft + 112, guiTop + 56));
        buttonList.add(new GuiButton(20, guiLeft + 16, guiTop + 27, 224, 20, ""));
        buttonList.add(new GuiButton(21, guiLeft + 16, guiTop + 52, 224, 20, ""));
        buttonList.add(new GuiButton(22, guiLeft + 16, guiTop + 87, 110, 20, ""));
        buttonList.add(new GuiButton(23, guiLeft + 129, guiTop + 87, 26, 20, "1/4"));
        buttonList.add(new GuiButton(24, guiLeft + 157, guiTop + 87, 26, 20, "1/3"));
        buttonList.add(new GuiButton(25, guiLeft + 185, guiTop + 87, 26, 20, "1/2"));
        buttonList.add(new GuiButton(26, guiLeft + 213, guiTop + 87, 27, 20,
                I18n.format("configurabledrawer.full")));
        updateViewButtons();
    }
    public boolean isSidesView() { return view == SIDES; }
    public boolean isStorageView() { return view == STORAGE; }
    public Rectangle ghostArea() {
        return view == STORAGE ? new Rectangle(guiLeft + 16, guiTop + 28, 18, 18) : new Rectangle();
    }
    public void setGhost(ItemStack stack) {
        if (view == STORAGE) action(DrawerNetwork.JEI_FILTER, 0, stack);
    }
    private void action(int action, long value, ItemStack filter) {
        DrawerNetwork.CHANNEL.sendToServer(new DrawerNetwork.Action(drawer.windowId, action, value, filter));
    }
    @Override protected void actionPerformed(GuiButton button) {
        if (button.id >= 3 && button.id <= 5) {
            int next = button.id - 3;
            if (view == next) return;
            if (view == OPTIONS) applyThreshold();
            view = next;
            draggingPreview = false;
            capacity.setFocused(false);
            threshold.setFocused(false);
            capacity.setText(Long.toString(drawer.shownCapacity));
            threshold.setText(Long.toString(drawer.shownThreshold));
            displayedCapacity = drawer.shownCapacity;
            displayedThreshold = drawer.shownThreshold;
            updateViewButtons();
        } else if (button.id >= 10 && button.id < 10 + SideRules.ENTRIES) {
            if (view == SIDES) action(DrawerNetwork.CYCLE_SIDE, button.id - 10, ItemStack.EMPTY);
        } else if (view == STORAGE && button.id == 0) {
            applyCapacity();
        } else if (view == STORAGE && button.id == 2) {
            action(DrawerNetwork.TAKE_STACK, 0, ItemStack.EMPTY);
        } else if (view == OPTIONS) {
            switch (button.id) {
                case 20: case 21: case 22:
                    optionAction(button.id, false);
                    break;
                case 23: presetThreshold(4); break;
                case 24: presetThreshold(3); break;
                case 25: presetThreshold(2); break;
                case 26: presetThreshold(1); break;
                default: break;
            }
        }
    }
    private void optionAction(int button, boolean reverse) {
        applyThreshold();
        switch (button) {
            case 20: action(DrawerNetwork.TOGGLE_VOID, 0, ItemStack.EMPTY); break;
            case 21: action(DrawerNetwork.TOGGLE_ICON, 0, ItemStack.EMPTY); break;
            case 22: action(DrawerNetwork.CYCLE_REDSTONE, reverse ? 1 : 0, ItemStack.EMPTY); break;
            default: break;
        }
    }
    private void applyCapacity() {
        try {
            BigInteger requested = new BigInteger(capacity.getText());
            BigInteger maximum = BigInteger.valueOf(drawer.shownMaximum);

            if (requested.compareTo(BigInteger.ONE) < 0) {
                return;
            }

            long applied = requested.min(maximum).longValue();

            drawer.shownCapacity = applied;
            displayedCapacity = applied;
            capacity.setText(Long.toString(applied));
            capacity.setFocused(false);

            action(DrawerNetwork.CAPACITY, applied, ItemStack.EMPTY);
        } catch (NumberFormatException ignored) {
        }
    }
    private boolean amountCondition() {
        return drawer.shownRedstone == DrawerStorage.REDSTONE_AT_LEAST
                || drawer.shownRedstone == DrawerStorage.REDSTONE_AT_MOST;
    }
    private void presetThreshold(int divisor) {
        long value = drawer.shownCapacity / divisor;
        if (drawer.shownCapacity % divisor != 0) value++;
        threshold.setText(Long.toString(value));
        thresholdEdited = true;
        applyThreshold();
    }
    private boolean validThreshold() {
        try {
            BigInteger value = new BigInteger(threshold.getText());
            return value.signum() >= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
    private void applyThreshold() {
        long value = drawer.shownThreshold;
        if (thresholdEdited && view == OPTIONS && amountCondition() && validThreshold())
            value = new BigInteger(threshold.getText())
                    .min(BigInteger.valueOf(drawer.shownMaximum)).longValue();
        threshold.setText(Long.toString(value));
        threshold.setFocused(false);
        displayedThreshold = value;
        thresholdEdited = false;
        if (value != drawer.shownThreshold) {
            drawer.shownThreshold = value;
            action(DrawerNetwork.SET_THRESHOLD, value, ItemStack.EMPTY);
        }
    }
    private void handleShiftDoubleClick(int x, int y, int button) {
        if (button != 0 || !isShiftKeyDown() || !mc.player.inventory.getItemStack().isEmpty()) {
            lastShiftClickSlot = -1;
            return;
        }

        int slotNumber = -1;
        for (Slot slot : drawer.inventorySlots) {
            if (slot.isEnabled() && isPointInRegion(slot.xPos, slot.yPos, 16, 16, x, y)) {
                slotNumber = slot.slotNumber;
                break;
            }
        }
        long now = Minecraft.getSystemTime();
        if (slotNumber >= 0 && slotNumber == lastShiftClickSlot
                && now >= lastShiftClickTime && now - lastShiftClickTime < 250) {
            action(DrawerNetwork.BULK_INSERT, 0, ItemStack.EMPTY);
            lastShiftClickSlot = -1;
        } else {
            lastShiftClickSlot = slotNumber;
            lastShiftClickTime = now;
        }
    }
    @Override protected void mouseClicked(int x, int y, int button) throws IOException {
        handleShiftDoubleClick(x, y, button);
        if (button == 1 && view == OPTIONS) {
            for (GuiButton guiButton : buttonList) {
                if (guiButton.id >= 20 && guiButton.id <= 22 && guiButton.mousePressed(mc, x, y)) {
                    rightOptionClick = true;
                    guiButton.playPressSound(mc.getSoundHandler());
                    optionAction(guiButton.id, true);
                    return;
                }
            }
        }
        if (view == SIDES) {
            if (button == 0 && previewArea().contains(x, y)) {
                draggingPreview = true;
                dragX = x; dragY = y;
                return;
            }
            super.mouseClicked(x, y, button);
            return;
        }

        if (button == 0) {
            for (GuiButton guiButton : buttonList) {
                // A preset replaces the draft without applying its old value first.
                if ((view == STORAGE && guiButton.id == 0
                        || view == OPTIONS && guiButton.id >= 23 && guiButton.id <= 26)
                        && guiButton.mousePressed(mc, x, y)) {
                    super.mouseClicked(x, y, button);
                    return;
                }
            }
        }

        if (view == OPTIONS) {
            boolean wasFocused = threshold.isFocused();
            if (threshold.getVisible()) threshold.mouseClicked(x, y, button);
            else threshold.setFocused(false);
            if (wasFocused && !threshold.isFocused()) applyThreshold();
            super.mouseClicked(x, y, button);
            return;
        }

        boolean wasFocused = capacity.isFocused();
        capacity.mouseClicked(x, y, button);
        if (wasFocused && !capacity.isFocused()) {
            capacity.setText(Long.toString(drawer.shownCapacity));
            displayedCapacity = drawer.shownCapacity;
        }
        if (ghostArea().contains(x, y)) {
            if (button == 0) action(DrawerNetwork.CURSOR_FILTER, 0, ItemStack.EMPTY);
            return;
        }
        super.mouseClicked(x, y, button);
    }
    @Override protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (rightOptionClick && button == 1) return;
        if (draggingPreview) {
            previewYaw = (previewYaw + (x - dragX) * 1.5F) % 360;
            previewPitch = Math.max(-89, Math.min(89, previewPitch + (y - dragY) * 1.5F));
            dragX = x; dragY = y;
            return;
        }
        super.mouseClickMove(x, y, button, elapsed);
    }
    @Override protected void mouseReleased(int x, int y, int button) {
        if (rightOptionClick && button == 1) {
            rightOptionClick = false;
            return;
        }
        if (draggingPreview) {
            draggingPreview = false;
            return;
        }
        super.mouseReleased(x, y, button);
    }
    @Override protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE
                || view == OPTIONS && mc.gameSettings.keyBindInventory.isActiveAndMatches(key)) {
            // Save before vanilla sends the close-window packet.
            if (view == OPTIONS) applyThreshold();
            super.keyTyped(typed, key);
            return;
        }
        if (view == STORAGE && capacity.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) applyCapacity();
            else capacity.textboxKeyTyped(typed, key);
            return;
        }
        if (view == OPTIONS && threshold.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER
                    || key == Keyboard.KEY_TAB) applyThreshold();
            else {
                String previous = threshold.getText();
                threshold.textboxKeyTyped(typed, key);
                if (!previous.equals(threshold.getText())) thresholdEdited = true;
            }
            return;
        }
        super.keyTyped(typed, key);
    }

    @Override public void updateScreen() {
        super.updateScreen();
        capacity.updateCursorCounter();
        threshold.updateCursorCounter();
        updateViewButtons();
        for (GuiButton button : buttonList) {
            if (button.id == 0) button.enabled = validCapacity();
            if (button.id == 20)
                button.displayString = I18n.format("configurabledrawer.void",
                        I18n.format(drawer.shownVoidOverflow ? "configurabledrawer.on" : "configurabledrawer.off"));
            if (button.id == 21)
                button.displayString = I18n.format("configurabledrawer.icon",
                        I18n.format(drawer.shownFrontIcon ? "configurabledrawer.shown" : "configurabledrawer.hidden"));
            if (button.id == 22) {
                button.displayString = I18n.format("configurabledrawer.redstone." + drawer.shownRedstone);
                button.width = amountCondition() ? 110 : 224;
            }
        }
    }

    @Override public void onGuiClosed() { Keyboard.enableRepeatEvents(false); super.onGuiClosed(); }
    @Override public void drawScreen(int x, int y, float partial) {
        drawDefaultBackground();
        if (!capacity.isFocused() && displayedCapacity != drawer.shownCapacity) {
            capacity.setText(Long.toString(drawer.shownCapacity)); displayedCapacity = drawer.shownCapacity;
        }
        if (!threshold.isFocused() && displayedThreshold != drawer.shownThreshold) {
            threshold.setText(Long.toString(drawer.shownThreshold));
            displayedThreshold = drawer.shownThreshold;
        }
        super.drawScreen(x, y, partial);
        renderHoveredToolTip(x, y);
        if (view == SIDES) {
            sideTooltip(x, y);
            return;
        }
        if (view == OPTIONS) {
            optionTooltip(x, y);
            return;
        }
        String stored = storedText();
        int storedX = guiLeft + 16;
        int storedY = guiTop + 57;
        int storedWidth = fontRenderer.getStringWidth(stored);

        if (x >= storedX && x < storedX + storedWidth && y >= storedY && y < storedY + fontRenderer.FONT_HEIGHT) {
            drawHoveringText(I18n.format("configurabledrawer.stored_exact", fullNumber(drawer.shownCount)), x, y);
        }
        if (ghostArea().contains(x, y)) {
            if (drawer.shownFilter.isEmpty()) {
                drawHoveringText(Arrays.asList(I18n.format("configurabledrawer.ghost.set")), x, y);
            } else {
                ItemStack filter = drawer.shownFilter;
                List<String> tooltip = new ArrayList<>(getItemToolTip(filter));
                tooltip.add(TextFormatting.GRAY + I18n.format(drawer.shownCount == 0
                        ? "configurabledrawer.ghost.clear"
                        : "configurabledrawer.ghost.locked"));

                FontRenderer font = filter.getItem().getFontRenderer(filter);
                GuiUtils.drawHoveringText(filter, tooltip, x, y, width, height, -1,
                        font != null ? font : fontRenderer);
            }
        }
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xffc6c6c6);
        drawRect(guiLeft + 1, guiTop + 1, guiLeft + xSize - 1, guiTop + 3, 0xffeeeeee);

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) slotBackground(47 + col * 18, 139 + row * 18);
        for (int col = 0; col < 9; col++) slotBackground(47 + col * 18, 197);

        if (view == SIDES) {
            Rectangle area = previewArea();
            drawRect(area.x, area.y, area.x + area.width, area.y + area.height, 0xff777777);
            DrawerPreview.draw(drawer.tile, drawer.shownFilter, drawer.shownSides,
                    drawer.shownFrontIcon, area, previewYaw, previewPitch);
            return;
        }
        if (view == OPTIONS) {
            if (amountCondition()) {
                threshold.setTextColor(validThreshold() ? 0xffeeeeee : 0xffff7777);
                threshold.drawTextBox();
            }
            return;
        }
        slotBackground(16, 28);
        capacity.setTextColor(validCapacity() ? 0xffeeeeee : 0xffff7777);
        capacity.drawTextBox();
        if (!drawer.shownFilter.isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            itemRender.renderItemAndEffectIntoGUI(drawer.shownFilter, guiLeft + 17, guiTop + 29);
            RenderHelper.disableStandardItemLighting();
        }
    }
    private boolean validCapacity() {
        try {
            BigInteger requested = new BigInteger(capacity.getText());
            return requested.compareTo(BigInteger.ONE) >= 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
    private void slotBackground(int x, int y) {
        drawRect(guiLeft + x, guiTop + y, guiLeft + x + 18, guiTop + y + 18, 0xffffffff);
        drawRect(guiLeft + x, guiTop + y, guiLeft + x + 17, guiTop + y + 17, 0xff373737);
        drawRect(guiLeft + x + 1, guiTop + y + 1, guiLeft + x + 17, guiTop + y + 17, 0xff8b8b8b);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(fontRenderer.trimStringToWidth(
                I18n.format("tile.configurabledrawer.configurable_drawer.name"), 110), 16, 10, 0x404040);
        if (view == SIDES) return;
        if (view == OPTIONS) {
            fontRenderer.drawString(I18n.format("configurabledrawer.redstone"), 16, 77, 0x404040);
            return;
        }
        String name = drawer.shownFilter.isEmpty() ? I18n.format("configurabledrawer.no_filter") : drawer.shownFilter.getDisplayName();
        fontRenderer.drawString(fontRenderer.trimStringToWidth(name, 148), 42, 34, 0x404040);
        fontRenderer.drawString(storedText(), 16, 57, 0x404040);
        fontRenderer.drawString(I18n.format("configurabledrawer.capacity"), 16, 76, 0x404040);
        fontRenderer.drawString(I18n.format("configurabledrawer.maximum", Long.toString(drawer.shownMaximum)), 16, 111, 0x606060);
        fontRenderer.drawString(I18n.format("configurabledrawer.inventory"), 48, 128, 0x404040);
    }

    private static String fullNumber(long value) {return String.format(Locale.ROOT, "%,d", value);}
    private String storedText() {return I18n.format("configurabledrawer.stored", DrawerNumberFormat.compact(drawer.shownCount));}

    private Rectangle previewArea() {
        return new Rectangle(guiLeft + 148, guiTop + 24, 92, 88);
    }
    private void updateViewButtons() {
        capacity.setVisible(view == STORAGE);
        threshold.setVisible(view == OPTIONS && amountCondition());
        for (GuiButton button : buttonList) {
            if (button.id == 0 || button.id == 2) button.visible = view == STORAGE;
            else if (button.id >= 10 && button.id < 10 + SideRules.ENTRIES)
                button.visible = view == SIDES;
            else if (button.id >= 20 && button.id <= 26)
                button.visible = view == OPTIONS && (button.id <= 22 || amountCondition());
        }
    }
    @Override protected boolean hasClickedOutside(int x, int y, int left, int top) {
        for (GuiButton button : buttonList) {
            if (button.id >= 3 && button.id <= 5 && button.visible
                    && x >= button.x && x < button.x + button.width
                    && y >= button.y && y < button.y + button.height)
                return false;
        }
        return super.hasClickedOutside(x, y, left, top);
    }
    private void sideTooltip(int x, int y) {
        for (GuiButton button : buttonList) {
            if (button.id < 10 || button.id >= 10 + SideRules.ENTRIES || !button.visible
                    || x < button.x || x >= button.x + button.width
                    || y < button.y || y >= button.y + button.height) continue;

            int entry = button.id - 10;
            EnumFacing face = SideRules.face(entry, drawer.tile.front());
            String name = I18n.format("configurabledrawer.side." + SIDE_KEYS[entry]);
            String world = face == null ? I18n.format("configurabledrawer.unsided")
                    : face.getName().toUpperCase(Locale.ROOT);
            String mode = I18n.format("configurabledrawer.mode." + SideRules.mode(drawer.shownSides, entry));
            drawHoveringText(Arrays.asList(name + " (" + world + ")", mode), x, y);
            return;
        }
    }
    private void optionTooltip(int x, int y) {
        for (GuiButton button : buttonList) {
            if (!button.visible || (button.id != 20 && button.id != 21)
                    || x < button.x || x >= button.x + button.width
                    || y < button.y || y >= button.y + button.height) continue;
            drawHoveringText(I18n.format(button.id == 20
                    ? "configurabledrawer.void.tooltip" : "configurabledrawer.icon.tooltip"), x, y);
            return;
        }
    }
    private final class SideButton extends GuiButton {
        private final int entry;

        SideButton(int entry, int x, int y) {
            super(10 + entry, x, y, 30, 20, I18n.format("configurabledrawer.side." + SIDE_KEYS[entry]));
            this.entry = entry;
        }
        @Override public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partial) {
            super.drawButton(minecraft, mouseX, mouseY, partial);
            int mode = SideRules.mode(drawer.shownSides, entry);
            if (visible && mode != SideRules.BOTH)
                drawRect(x + 2, y + height - 2, x + width - 2, y + height,
                        0xff000000 | DrawerPreview.color(mode));
        }
    }
    private final class DrawerTab extends GuiButton {
        DrawerTab(int id, int x, int y, int width, String label) {
            super(id, x, y, width, 20, label);
        }

        @Override public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partial) {
            if (!visible) return;
            hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
            boolean selected = id - 3 == view;
            int fill = selected ? 0xffc6c6c6 : hovered ? 0xffb8b8b8 : 0xffa8a8a8;

            // Dark outline with clipped top corners.
            drawRect(x + 2, y, x + width - 2, y + 1, 0xff373737);
            drawRect(x + 1, y + 1, x + width - 1, y + 2, 0xff373737);
            drawRect(x, y + 2, x + width, y + height, 0xff373737);
            drawRect(x + 1, y + 2, x + width - 1, y + height - 1, fill);

            // Small grey bands form the tab texture and raised edge.
            drawRect(x + 2, y + 2, x + width - 2, y + 3,
                    selected ? 0xfff0f0f0 : 0xffdedede);
            drawRect(x + 1, y + 3, x + 2, y + height - 2,
                    selected ? 0xffededed : 0xffcecece);
            drawRect(x + width - 2, y + 3, x + width - 1, y + height - 2,
                    selected ? 0xff8a8a8a : 0xff686868);
            drawRect(x + 2, y + height - 4, x + width - 2, y + height - 3,
                    selected ? 0xffbdbdbd : 0xff919191);

            // Remove the active tab's bottom seam so it joins the panel.
            if (selected)
                drawRect(x + 1, y + height - 2, x + width - 1, y + height,
                        0xffc6c6c6);

            String label = minecraft.fontRenderer.trimStringToWidth(displayString, width - 8);
            drawCenteredString(minecraft.fontRenderer, label,
                    x + width / 2, y + 6, 0xffffffff);
        }
    }
}
