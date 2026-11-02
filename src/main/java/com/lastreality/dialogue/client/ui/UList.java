package com.lastreality.dialogue.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.IntConsumer;

/** Прокручиваемый список карточек (узлы диалога, список диалогов). Звезда = «стартовый», крестик при наведении = удалить. */
public class UList extends UWidget {
    public record Item(String title, String sub, boolean star) {
    }

    private static final int ITEM_H = 36;

    private final IntConsumer onSelect;
    @Nullable
    private final IntConsumer onDelete;
    private List<Item> items = List.of();
    private int selected = -1;
    private float scroll;

    public UList(int x, int y, int w, int h, IntConsumer onSelect, @Nullable IntConsumer onDelete) {
        super(x, y, w, h);
        this.onSelect = onSelect;
        this.onDelete = onDelete;
    }

    public void setItems(List<Item> items, int selected) {
        this.items = items;
        this.selected = selected;
        clampScroll();
    }

    /** Прокрутить так, чтобы элемент был виден. */
    public void reveal(int idx) {
        ensureVisible(idx);
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, items.size() * ITEM_H - height));
    }

    private void ensureVisible(int idx) {
        float top = idx * ITEM_H;
        if (top < scroll) {
            scroll = top;
        } else if (top + ITEM_H > scroll + height) {
            scroll = top + ITEM_H - height;
        }
        clampScroll();
    }

    private int indexAt(double mouseY) {
        int idx = (int) ((mouseY - getY() + scroll) / ITEM_H);
        return idx >= 0 && idx < items.size() ? idx : -1;
    }

    private boolean inDeleteZone(double mouseX) {
        return onDelete != null && mouseX >= getX() + width - 26;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int idx = indexAt(mouseY);
        if (idx < 0) {
            return;
        }
        if (inDeleteZone(mouseX)) {
            onDelete.accept(idx);
        } else {
            onSelect.accept(idx);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroll -= (float) delta * 20f;
        clampScroll();
        return true;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int cw = width - 6; // место под полосу прокрутки
        int hoverIdx = this.isHovered ? indexAt(mouseY) : -1;

        g.enableScissor(x, y, x + width, y + height);
        for (int i = 0; i < items.size(); i++) {
            int ry = y + i * ITEM_H - (int) scroll;
            if (ry + ITEM_H < y || ry > y + height) {
                continue;
            }
            Item it = items.get(i);
            boolean sel = i == selected;
            boolean hov = i == hoverIdx;
            Theme.frame(g, x, ry, cw, ITEM_H - 4,
                    sel ? Theme.ACCENT : (hov ? 0xFF3A4260 : Theme.BORDER),
                    sel ? 0xFF1C2A47 : (hov ? 0xFF222740 : Theme.CARD));

            String title = Theme.font().plainSubstrByWidth(it.title(), cw - 36);
            Theme.text(g, title, x + 8, ry + 6, sel ? 0xFFFFFFFF : Theme.TEXT);
            if (it.star()) {
                Icons.draw(g, Icons.STAR, x + 8 + Theme.font().width(title) + 5, ry + 6, Theme.WARN);
            }
            Theme.text(g, Theme.font().plainSubstrByWidth(it.sub(), cw - 16), x + 8, ry + 19, Theme.DIM);

            if (hov && onDelete != null) {
                boolean overX = inDeleteZone(mouseX);
                Icons.draw(g, Icons.CROSS, x + cw - 14, ry + 6, overX ? Theme.DANGER : Theme.FAINT);
            }
        }
        g.disableScissor();

        int total = items.size() * ITEM_H;
        if (total > height) {
            int thumbH = Math.max(12, height * height / total);
            int thumbY = y + (int) ((height - thumbH) * (scroll / (total - height)));
            g.fill(x + width - 3, thumbY, x + width - 1, thumbY + thumbH, Theme.FAINT);
        }
    }
}
