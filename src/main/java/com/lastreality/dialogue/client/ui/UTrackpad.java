package com.lastreality.dialogue.client.ui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Числовой «трекпад»: подпись слева, значение справа, заливка = положение.
 * Тянуть мышью, колёсико — шаг 1%, двойной клик — сброс на значение по умолчанию (метка на шкале).
 */
public class UTrackpad extends UWidget {
    private final String label;
    private final double min;
    private final double max;
    private final double def;
    private final int decimals;
    private final Consumer<Double> onChange;
    private double value;
    private long lastClick;

    public UTrackpad(int x, int y, int w, int h, String label, double min, double max, double def, double value,
                     int decimals, Consumer<Double> onChange) {
        super(x, y, w, h);
        this.label = label;
        this.min = min;
        this.max = max;
        this.def = def;
        this.decimals = decimals;
        this.onChange = onChange;
        this.value = Mth.clamp(value, min, max);
    }

    private void set(double v) {
        double f = Math.pow(10, decimals);
        v = Math.round(Mth.clamp(v, min, max) * f) / f;
        if (v != value) {
            value = v;
            onChange.accept(v);
        }
    }

    private void setFromMouse(double mouseX) {
        double t = Mth.clamp((mouseX - getX()) / (double) width, 0.0, 1.0);
        set(min + t * (max - min));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        long now = Util.getMillis();
        if (now - lastClick < 320) {
            set(def);
        } else {
            setFromMouse(mouseX);
        }
        lastClick = now;
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        setFromMouse(mouseX);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        set(value + Math.signum(delta) * (max - min) / 100.0);
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float h = animate(this.isHovered && this.active);
        int x = getX();
        int y = getY();
        Theme.frame(g, x, y, width, height, Theme.mix(Theme.BORDER, Theme.ACCENT, h * 0.8f), Theme.FIELD);

        int fillW = (int) ((width - 2) * (value - min) / (max - min));
        if (fillW > 0) {
            g.fill(x + 1, y + 1, x + 1 + fillW, y + height - 1, Theme.mix(0xFF1B3A66, 0xFF245090, h));
        }
        int defX = x + 1 + (int) ((width - 2) * (def - min) / (max - min));
        g.fill(defX, y + height - 4, defX + 1, y + height - 1, Theme.FAINT);

        Theme.text(g, label, x + 6, textY(), Theme.mix(Theme.DIM, Theme.TEXT, h));
        String s = String.format(Locale.ROOT, "%." + decimals + "f", value);
        Theme.text(g, s, x + width - 6 - Theme.font().width(s), textY(), Theme.TEXT);
    }
}
