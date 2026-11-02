package com.lastreality.dialogue.client.ui;

import net.minecraft.Util;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import javax.annotation.Nullable;

/** Базовый класс кастомных виджетов: плавная анимация наведения, подсказка, тихий звук клика. */
public abstract class UWidget extends AbstractWidget {
    @Nullable
    public String tooltip;
    protected float hover;
    private long lastMs;

    protected UWidget(int x, int y, int w, int h) {
        super(x, y, w, h, Component.empty());
    }

    public UWidget tip(String text) {
        this.tooltip = text;
        return this;
    }

    /** Плавно ведёт hover к цели (0..1); вызывать раз за кадр. */
    protected float animate(boolean target) {
        long now = Util.getMillis();
        float dt = lastMs == 0 ? 0f : Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;
        hover += ((target ? 1f : 0f) - hover) * (1f - (float) Math.exp(-dt * 18f));
        return hover;
    }

    protected int textY() {
        return getY() + (height - 8) / 2 + 1;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    @Override
    public void playDownSound(SoundManager manager) {
        manager.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f, 0.35f));
    }
}
