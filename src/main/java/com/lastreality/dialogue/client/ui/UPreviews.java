package com.lastreality.dialogue.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Supplier;

/** Небольшие «только для показа» виджеты редактора. */
public final class UPreviews {
    private UPreviews() {
    }

    /** Карточка с переносом строк: как реплика ляжет в окне (до 4 строк). */
    public static class Text extends UWidget {
        private final Supplier<String> source;
        private String lastText = null;
        private int lastWidth = -1;
        private List<FormattedCharSequence> lines = List.of();

        public Text(int x, int y, int w, int h, Supplier<String> source) {
            super(x, y, w, h);
            this.source = source;
            this.active = false; // не принимает клики
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Theme.frame(g, getX(), getY(), width, height, Theme.BORDER, Theme.CARD);
            String text = source.get();
            if (text.isEmpty()) {
                Theme.text(g, "Пустая реплика", getX() + 6, getY() + 6, Theme.FAINT);
                return;
            }
            if (!text.equals(lastText) || lastWidth != width) {
                lastText = text;
                lastWidth = width;
                lines = Theme.font().split(Component.literal(text), width - 12);
            }
            int y = getY() + 6;
            for (int i = 0; i < Math.min(4, lines.size()); i++) {
                g.drawString(Theme.font(), lines.get(i), getX() + 6, y, Theme.TEXT, false);
                y += 10;
            }
            if (lines.size() > 4) {
                Theme.text(g, "…", getX() + width - 14, getY() + height - 12, Theme.DIM);
            }
        }
    }

    /** Миниатюра иконки ответа (по пути из поля). */
    public static class Icon extends UWidget {
        private final Supplier<String> source;

        public Icon(int x, int y, int size, Supplier<String> source) {
            super(x, y, size, size);
            this.source = source;
            this.active = false;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Theme.frame(g, getX(), getY(), width, height, Theme.BORDER, 0xFF2A2E3C);
            String s = source.get();
            ResourceLocation rl = s.isEmpty() ? null : ResourceLocation.tryParse(s);
            if (rl == null) {
                Theme.text(g, "-", getX() + width / 2 - 2, textY(), Theme.FAINT);
                return;
            }
            RenderSystem.enableBlend();
            g.blit(rl, getX() + 1, getY() + 1, width - 2, height - 2, 0f, 0f, 32, 32, 32, 32);
        }
    }
}
