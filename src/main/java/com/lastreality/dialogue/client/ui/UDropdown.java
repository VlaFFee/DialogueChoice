package com.lastreality.dialogue.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Выпадающий список. Открытый список рисуется поверх всего (renderOverlay) и забирает клик (overlayClick),
 * поэтому экран должен: 1) вызывать renderOverlay в конце render, 2) отдавать клики/колёсико открытому списку первыми.
 */
public class UDropdown extends UWidget {
    private static final int ROW = 16;
    private static final int MAX_VISIBLE = 8;

    /** Единственный открытый список (или null). */
    @Nullable
    public static UDropdown open;

    private final List<String> labels;
    private final IntConsumer onChange;
    private int selected;
    private int scroll;
    private int listTop;
    private int listHeight;

    public UDropdown(int x, int y, int w, int h, List<String> labels, int selected, IntConsumer onChange) {
        super(x, y, w, h);
        this.labels = labels;
        this.selected = Mth.clamp(selected, 0, Math.max(0, labels.size() - 1));
        this.onChange = onChange;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (open == this) {
            open = null;
        } else {
            open = this;
            scroll = Mth.clamp(selected - 3, 0, Math.max(0, labels.size() - MAX_VISIBLE));
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float h = animate(this.isHovered && this.active);
        int x = getX();
        int y = getY();
        boolean isOpen = open == this;
        Theme.frame(g, x, y, width, height, isOpen ? Theme.ACCENT : Theme.mix(Theme.BORDER, 0xFF4A5278, h),
                Theme.mix(Theme.FIELD, 0xFF161A26, h));
        String s = labels.isEmpty() ? "" : Theme.font().plainSubstrByWidth(labels.get(selected), width - 26);
        Theme.text(g, s, x + 6, textY(), Theme.TEXT);
        Icons.draw(g, Icons.ARROW_DOWN, x + width - 12, y + (height - 3) / 2, isOpen ? Theme.ACCENT : Theme.DIM);
    }

    /** Рисует выпавший список (вызывать в самом конце render экрана). */
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY) {
        if (open != this) {
            return;
        }
        int n = labels.size();
        int vis = Math.min(MAX_VISIBLE, n);
        listHeight = vis * ROW + 4;
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        listTop = getY() + height + 2;
        if (listTop + listHeight > screenH - 4) {
            listTop = getY() - listHeight - 2;
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        Theme.frame(g, getX(), listTop, width, listHeight, Theme.ACCENT, Theme.PANEL_2 | 0xFF000000);
        for (int i = 0; i < vis; i++) {
            int idx = i + scroll;
            int ry = listTop + 2 + i * ROW;
            boolean hov = mouseX >= getX() && mouseX < getX() + width && mouseY >= ry && mouseY < ry + ROW;
            if (hov) {
                g.fill(getX() + 2, ry, getX() + width - 2, ry + ROW, 0xFF2A3350);
            }
            String s = Theme.font().plainSubstrByWidth(labels.get(idx), width - 16);
            Theme.text(g, s, getX() + 7, ry + 4, idx == selected ? Theme.ACCENT : Theme.TEXT);
        }
        if (n > vis) {
            int trackH = listHeight - 4;
            int thumbH = Math.max(8, trackH * vis / n);
            int thumbY = listTop + 2 + (trackH - thumbH) * scroll / Math.max(1, n - vis);
            g.fill(getX() + width - 4, thumbY, getX() + width - 2, thumbY + thumbH, Theme.DIM);
        }
        g.pose().popPose();
    }

    /** Клик при открытом списке. Всегда «съедает» клик и закрывает список. */
    public boolean overlayClick(double mouseX, double mouseY) {
        boolean inside = mouseX >= getX() && mouseX < getX() + width && mouseY >= listTop + 2 && mouseY < listTop + listHeight - 2;
        if (inside) {
            int idx = scroll + (int) ((mouseY - listTop - 2) / ROW);
            if (idx >= 0 && idx < labels.size()) {
                selected = idx;
                onChange.accept(idx);
            }
        }
        open = null;
        return true;
    }

    public void overlayScroll(double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, labels.size() - MAX_VISIBLE));
    }
}
