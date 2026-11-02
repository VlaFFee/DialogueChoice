package com.lastreality.dialogue.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;

/** Сегментированный переключатель (вкладки): выбранный сегмент подсвечен акцентом. */
public class USegmented extends UWidget {
    private final String[] options;
    private final IntConsumer onChange;
    private int selected;

    public USegmented(int x, int y, int w, int h, String[] options, int selected, IntConsumer onChange) {
        super(x, y, w, h);
        this.options = options;
        this.selected = selected;
        this.onChange = onChange;
    }

    private int indexAt(double mouseX) {
        return Mth.clamp((int) ((mouseX - getX()) * options.length / width), 0, options.length - 1);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int i = indexAt(mouseX);
        if (i != selected) {
            selected = i;
            onChange.accept(i);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        animate(this.isHovered && this.active);
        int x = getX();
        int y = getY();
        Theme.frame(g, x, y, width, height, Theme.BORDER, Theme.FIELD);

        int hoverIdx = this.isHovered ? indexAt(mouseX) : -1;
        for (int i = 0; i < options.length; i++) {
            int sx = x + 2 + (width - 4) * i / options.length;
            int ex = x + 2 + (width - 4) * (i + 1) / options.length;
            boolean sel = i == selected;
            if (sel) {
                Theme.rounded(g, sx, y + 2, ex - sx, height - 4, Theme.ACCENT);
            } else if (i == hoverIdx) {
                Theme.rounded(g, sx, y + 2, ex - sx, height - 4, 0xFF1E2230);
            }
            String s = Theme.font().plainSubstrByWidth(options[i], ex - sx - 4);
            int tw = Theme.font().width(s);
            Theme.text(g, s, sx + (ex - sx - tw) / 2, textY(), sel ? 0xFFFFFFFF : Theme.DIM);
        }
    }
}
