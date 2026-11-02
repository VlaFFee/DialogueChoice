package com.lastreality.dialogue.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Тёмная плоская тема редактора (в духе современных редакторов: панели, акцентный синий, скруглённые углы). */
public final class Theme {
    private Theme() {
    }

    public static final int PANEL = 0xF2141720;
    public static final int PANEL_2 = 0xF2191C27;
    public static final int CARD = 0xFF1E2230;
    public static final int FIELD = 0xFF0D0F15;
    public static final int BORDER = 0xFF2B3042;
    public static final int ACCENT = 0xFF3D8BFF;
    public static final int ACCENT_DIM = 0xFF1F4A85;
    public static final int TEXT = 0xFFE8EBF4;
    public static final int DIM = 0xFF8D94A9;
    public static final int FAINT = 0xFF5E6478;
    public static final int DANGER = 0xFFE5534B;
    public static final int WARN = 0xFFF5A623;
    public static final int OK = 0xFF49C27A;

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Прямоугольник со срезанными углами (псевдо-скругление). */
    public static void rounded(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 2 || h <= 2) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    /** Рамка + заливка (оба цвета непрозрачные). */
    public static void frame(GuiGraphics g, int x, int y, int w, int h, int border, int fill) {
        rounded(g, x, y, w, h, border);
        rounded(g, x + 1, y + 1, w - 2, h - 2, fill);
    }

    public static int mix(int a, int b, float t) {
        t = t < 0 ? 0 : Math.min(t, 1);
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int ar = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int ag = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int ab = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    /** Умножает альфу. Для текста не используйте a &lt; 0.05 (шрифт считает почти нулевую альфу непрозрачной). */
    public static int alpha(int argb, float a) {
        a = a < 0 ? 0 : Math.min(a, 1);
        return ((int) (((argb >>> 24) & 0xFF) * a) << 24) | (argb & 0xFFFFFF);
    }

    public static void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font(), s, x, y, color, false);
    }
}
