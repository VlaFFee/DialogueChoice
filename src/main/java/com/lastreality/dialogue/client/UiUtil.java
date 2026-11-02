package com.lastreality.dialogue.client;

/** Easing-функции и работа с цветами ARGB. */
final class UiUtil {
    private UiUtil() {
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    static float easeInOutCubic(float t) {
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    static float smoothstep(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    /** Умножает альфа-канал цвета на a (0..1). */
    static int mulAlpha(int argb, float a) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * clamp01(a));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    static int lerpColor(int c1, int c2, float t) {
        t = clamp01(t);
        int a = (int) (((c1 >>> 24) & 0xFF) + ((((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t));
        int r = (int) (((c1 >> 16) & 0xFF) + ((((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t));
        int g = (int) (((c1 >> 8) & 0xFF) + ((((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t));
        int b = (int) ((c1 & 0xFF) + (((c2 & 0xFF) - (c1 & 0xFF)) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
