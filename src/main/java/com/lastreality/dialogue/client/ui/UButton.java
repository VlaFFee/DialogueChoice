package com.lastreality.dialogue.client.ui;

import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;

/** Плоская кнопка: стили DEFAULT / PRIMARY (акцент) / DANGER / GHOST (без фона), иконка слева, режим «включено». */
public class UButton extends UWidget {
    public enum Style {DEFAULT, PRIMARY, DANGER, GHOST}

    private final String label;
    @Nullable
    private final String[] icon;
    private final Style style;
    private final Runnable action;
    /** Подсвечивает кнопку как «включённую» (для переключателей и активных чипов). */
    public boolean toggled;

    public UButton(int x, int y, int w, int h, String label, @Nullable String[] icon, Style style, Runnable action) {
        super(x, y, w, h);
        this.label = label;
        this.icon = icon;
        this.style = style;
        this.action = action;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float h = animate(this.isHovered && this.active);
        int bg;
        int border;
        int txt;
        switch (style) {
            case PRIMARY -> {
                bg = Theme.mix(0xFF2F73D9, Theme.ACCENT, h);
                border = bg;
                txt = 0xFFFFFFFF;
            }
            case DANGER -> {
                bg = Theme.mix(0xFF2A1D22, 0xFF4A2429, h);
                border = Theme.mix(0xFF4A2A30, Theme.DANGER, h);
                txt = Theme.DANGER;
            }
            case GHOST -> {
                bg = Theme.mix(0x00252A3A, 0xFF252A3A, h);
                border = bg;
                txt = Theme.mix(Theme.DIM, Theme.TEXT, h);
            }
            default -> {
                bg = Theme.mix(0xFF22263A, 0xFF2C3250, h);
                border = Theme.mix(Theme.BORDER, 0xFF4A5278, h);
                txt = Theme.TEXT;
            }
        }
        if (toggled) {
            bg = Theme.mix(bg, Theme.ACCENT_DIM, 0.9f);
            border = Theme.ACCENT;
            txt = 0xFFFFFFFF;
        }
        if (!this.active) {
            txt = Theme.FAINT;
            bg = Theme.mix(bg, Theme.FIELD, 0.6f);
        }
        Theme.frame(g, getX(), getY(), width, height, border, bg);

        int tw = label.isEmpty() ? 0 : Theme.font().width(label);
        int iw = icon == null ? 0 : Icons.width(icon);
        int gap = (icon != null && tw > 0) ? 5 : 0;
        int sx = getX() + (width - (iw + gap + tw)) / 2;
        if (icon != null) {
            Icons.draw(g, icon, sx, getY() + (height - Icons.height(icon)) / 2, txt);
            sx += iw + gap;
        }
        if (tw > 0) {
            Theme.text(g, label, sx, textY(), txt);
        }
    }
}
