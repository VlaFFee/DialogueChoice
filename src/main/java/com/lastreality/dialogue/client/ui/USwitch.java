package com.lastreality.dialogue.client.ui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/** Переключатель «вкл/выкл» с плавным движком. Клик по всей строке. */
public class USwitch extends UWidget {
    private final String label;
    private final Consumer<Boolean> onChange;
    private boolean value;
    private float knob;
    private long lastMs;

    public USwitch(int x, int y, int w, int h, String label, boolean value, Consumer<Boolean> onChange) {
        super(x, y, w, h);
        this.label = label;
        this.value = value;
        this.knob = value ? 1f : 0f;
        this.onChange = onChange;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        value = !value;
        onChange.accept(value);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float h = animate(this.isHovered && this.active);
        long now = Util.getMillis();
        float dt = lastMs == 0 ? 0f : Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;
        knob += ((value ? 1f : 0f) - knob) * (1f - (float) Math.exp(-dt * 22f));

        int x = getX();
        int y = getY();
        Theme.frame(g, x, y, width, height, Theme.mix(Theme.BORDER, 0xFF4A5278, h), Theme.mix(Theme.CARD, 0xFF252A3E, h));
        Theme.text(g, label, x + 8, textY(), Theme.mix(Theme.DIM, Theme.TEXT, Math.max(h, knob)));

        int pw = 24;
        int ph = 12;
        int px = x + width - pw - 8;
        int py = y + (height - ph) / 2;
        Theme.rounded(g, px, py, pw, ph, Theme.mix(0xFF3A4054, Theme.ACCENT, knob));
        int kx = px + 2 + (int) ((pw - ph) * knob);
        Theme.rounded(g, kx, py + 2, ph - 4, ph - 4, 0xFFFFFFFF);
    }
}
