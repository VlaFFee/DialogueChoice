package com.lastreality.dialogue.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Плоское текстовое поле: свой фон/рамка (акцент при фокусе), отступы, подсказка-плейсхолдер. */
public class UTextField extends EditBox {
    private static final int PAD = 5;

    public UTextField(Font font, int x, int y, int w, int h, String hint, int maxLength, String value, Consumer<String> onChange) {
        super(font, x, y, w, h, Component.empty());
        setBordered(false);
        setMaxLength(maxLength);
        setValue(value);
        setHint(Component.literal(hint));
        setTextColor(Theme.TEXT);
        setResponder(onChange); // после setValue, чтобы не считать начальное значение правкой
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = width;
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + height;
        Theme.frame(g, x, y, w, height, isFocused() ? Theme.ACCENT : (hov ? 0xFF4A5278 : Theme.BORDER), Theme.FIELD);

        // Без рамки EditBox рисует текст у верхнего края — сдвигаем внутрь и центрируем по вертикали
        setX(x + PAD);
        setY(y + (height - 8) / 2);
        setWidth(w - PAD * 2);
        super.renderWidget(g, mouseX, mouseY, partialTick);
        setX(x);
        setY(y);
        setWidth(w);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int x = getX();
        int w = width;
        setX(x + PAD);
        setWidth(w - PAD * 2);
        super.onClick(mouseX, mouseY);
        setX(x);
        setWidth(w);
    }
}
