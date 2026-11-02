package com.lastreality.dialogue.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/** Маленькие пиксельные иконки, рисуются заливкой (без текстур). '#' = пиксель. */
public final class Icons {
    private Icons() {
    }

    public static final String[] PLUS = {
            "...#...",
            "...#...",
            "...#...",
            "#######",
            "...#...",
            "...#...",
            "...#...",
    };
    public static final String[] CROSS = {
            "##...##",
            ".##.##.",
            "..###..",
            "...#...",
            "..###..",
            ".##.##.",
            "##...##",
    };
    public static final String[] PLAY = {
            "#......",
            "###....",
            "#####..",
            "#######",
            "#####..",
            "###....",
            "#......",
    };
    public static final String[] STAR = {
            "...#...",
            "...#...",
            "#######",
            ".#####.",
            "..###..",
            ".##.##.",
            ".#...#.",
    };
    public static final String[] CAMERA = {
            "..###....",
            "#########",
            "##.###.##",
            "##.#.#.##",
            "##.###.##",
            "#########",
    };
    public static final String[] TRASH = {
            "..###..",
            "#######",
            ".#.#.#.",
            ".#.#.#.",
            ".#.#.#.",
            ".#####.",
    };
    public static final String[] CHEVRON_L = {
            "...#",
            "..#.",
            ".#..",
            "#...",
            ".#..",
            "..#.",
            "...#",
    };
    public static final String[] CHEVRON_R = {
            "#...",
            ".#..",
            "..#.",
            "...#",
            "..#.",
            ".#..",
            "#...",
    };
    public static final String[] ARROW_DOWN = {
            "#####",
            ".###.",
            "..#..",
    };

    public static int width(String[] icon) {
        return icon[0].length();
    }

    public static int height(String[] icon) {
        return icon.length;
    }

    public static void draw(GuiGraphics g, String[] icon, int x, int y, int color) {
        for (int r = 0; r < icon.length; r++) {
            String row = icon[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) == '#') {
                    int start = c;
                    while (c < row.length() && row.charAt(c) == '#') {
                        c++;
                    }
                    g.fill(x + start, y + r, x + c, y + r + 1, color);
                } else {
                    c++;
                }
            }
        }
    }
}
