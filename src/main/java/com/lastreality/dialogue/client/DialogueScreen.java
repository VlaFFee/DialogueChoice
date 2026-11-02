package com.lastreality.dialogue.client;

import com.lastreality.dialogue.Dialogue;
import com.lastreality.dialogue.network.Net;
import com.lastreality.dialogue.network.Packets;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Экран диалога. Вёрстка в «виртуальном холсте» высотой 360 (= скриншот 1440p / 4):
 * одинаково выглядит на любом разрешении и GUI scale.
 *
 * Сервер — источник истины: клиент шлёт индекс ответа (Advance), сервер проверяет, выполняет команды
 * и присылает следующий узел (ShowNode). Пока ответ летит на сервер, играет анимация выбора.
 */
public class DialogueScreen extends Screen {
    // ---------- вёрстка (виртуальные единицы) ----------
    private static final float VH = 360f;
    private static final int ROW_X = 5, ROW_TOP = 6, ROW_PITCH = 33, ROW_H = 28;
    private static final int ICON_BOX_W = 30, ICON_SIZE = 20, TEXT_PAD = 5;
    private static final float TEXT_SCALE = 1.25f;

    private static final int BOX_LEFT = 107, BOX_TOP = 275, BOX_MID = 290, BOX_BOTTOM = 358;
    private static final int SLOPE_C = 37; // скос: x = y + 37 (от (312,275) до (327,290))
    private static final int PORTRAIT_L = 2, PORTRAIT_T = 265;
    private static final int TAB_L = 382, TAB_T = 275, TAB_R = 539, TAB_B = 296;
    private static final int TEXT_X = 114, LINE_H = 11, TEXT_CENTER_Y = 326;

    private static final int PORTRAIT_FEET_X = 54, PORTRAIT_FEET_Y = 384, PORTRAIT_SCALE = 78;
    private static final float PORTRAIT_LOOK_X = -20f, PORTRAIT_LOOK_Y = -6f;

    // ---------- тайминги ----------
    private static final long BOX_DELAY_MS = 450, BOX_ANIM_MS = 450;
    private static final long ROW_STAGGER_MS = 85, ROW_APPEAR_MS = 380, SELECT_MS = 450;
    private static final long SERVER_TIMEOUT_MS = 6000;
    private static final float CHARS_PER_SECOND = 38f, TEXT_START_DELAY = 0.25f;

    private static final class Row {
        final Dialogue.Choice choice;
        final ResourceLocation icon;
        final float textW;
        float hover;
        boolean wasHovered;

        Row(Dialogue.Choice choice, float textW) {
            this.choice = choice;
            this.icon = choice.icon.isEmpty() ? null : ResourceLocation.tryParse(choice.icon);
            this.textW = textW;
        }
    }

    private final Dialogue dialogue;
    private final String startNode;

    private String nodeId;
    private Dialogue.Node node;
    private final List<Row> rows = new ArrayList<>();

    private final long openTime = Util.getMillis();
    private long lastFrame = -1;
    private long nodeStart;
    private long choicesStart = -1;
    private long selectTime;
    private int selected = -1;

    // ожидание ответа сервера
    private boolean waiting;
    private long requestTime;
    @Nullable
    private String pendingNode;

    // печатная машинка
    private List<String> lines = List.of();
    private float[] charTimes = new float[0];
    private int totalChars;
    private int visibleChars;
    private int lastBlip;
    private boolean typingDone;
    private int layoutWidth = -1;

    public DialogueScreen(Dialogue dialogue, String startNode) {
        super(Component.empty());
        this.dialogue = dialogue;
        this.startNode = startNode;
    }

    /** Запускает диалог: камера -> экран. */
    public static void open(Dialogue dialogue, Entity npc, String startNode) {
        Dialogue.Node first = dialogue.nodes.get(startNode);
        if (first == null) {
            Net.toServer(new Packets.Advance(Packets.ABORT));
            return;
        }
        if (DialogueCamera.INSTANCE.begin(npc, dialogue.cameraFor(first))) {
            Minecraft.getInstance().setScreen(new DialogueScreen(dialogue, startNode));
        } else {
            Net.toServer(new Packets.Advance(Packets.ABORT));
        }
    }

    // ===================================================== жизненный цикл

    @Override
    protected void init() {
        if (node == null) {
            enterNode(startNode, true);
        }
    }

    @Override
    public void removed() {
        DialogueCamera.INSTANCE.end();
    }

    /** Закрытие игроком (Esc) — сообщаем серверу. */
    @Override
    public void onClose() {
        Net.toServer(new Packets.Advance(Packets.ABORT));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Сервер прислал следующий узел ("" = конец). */
    public void onServerNode(String id) {
        this.pendingNode = id;
    }

    @Override
    public void tick() {
        long now = Util.getMillis();
        if (pendingNode != null) {
            boolean animDone = selected < 0 || now - selectTime >= SELECT_MS;
            if (animDone) {
                String id = pendingNode;
                pendingNode = null;
                if (id.isEmpty() || !dialogue.nodes.containsKey(id)) {
                    this.minecraft.setScreen(null); // сервер завершил диалог
                } else {
                    enterNode(id, false);
                }
            }
        } else if ((selected >= 0 || waiting) && now - requestTime > SERVER_TIMEOUT_MS) {
            this.minecraft.setScreen(null); // сервер молчит — выходим, чтобы игрок не «застрял»
        }
    }

    private void enterNode(String id, boolean first) {
        Dialogue.Node n = dialogue.nodes.get(id);
        if (n == null) {
            this.minecraft.setScreen(null);
            return;
        }
        this.nodeId = id;
        this.node = n;
        this.nodeStart = Util.getMillis() + (first ? BOX_DELAY_MS : 0);
        this.choicesStart = -1;
        this.selected = -1;
        this.waiting = false;
        this.typingDone = false;
        this.visibleChars = 0;
        this.lastBlip = 0;
        this.layoutWidth = -1;
        this.rows.clear();
        for (Dialogue.Choice c : n.choices) {
            rows.add(new Row(c, this.font.width(c.text) * TEXT_SCALE));
        }
        // новый ракурс — камера плавно доедет
        DialogueCamera.INSTANCE.setSettings(dialogue.cameraFor(n));
    }

    // ===================================================== ввод

    private float scale() {
        return this.height / VH;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        if (selected >= 0 || waiting) {
            return true;
        }
        if (!typingDone) {
            skipTyping();
            return true;
        }
        if (!rows.isEmpty()) {
            float s = scale();
            long now = Util.getMillis();
            for (int i = 0; i < rows.size(); i++) {
                if (rowAppear(i, now) >= 0.6f && inRow(rows.get(i), i, (float) (mx / s), (float) (my / s))) {
                    choose(i);
                    break;
                }
            }
            return true;
        }
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (selected >= 0 || waiting) {
                return true;
            }
            if (!typingDone) {
                skipTyping();
            } else if (rows.isEmpty()) {
                advance();
            }
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int idx = key - GLFW.GLFW_KEY_1;
            if (typingDone && selected < 0 && !waiting && idx < rows.size() && rowAppear(idx, Util.getMillis()) >= 0.6f) {
                choose(idx);
            }
            return true;
        }
        return super.keyPressed(key, scancode, mods); // ESC закрывает
    }

    private void skipTyping() {
        nodeStart = Util.getMillis() - 600_000L;
    }

    private void advance() {
        long now = Util.getMillis();
        waiting = true;
        requestTime = now;
        Net.toServer(new Packets.Advance(Packets.CONTINUE));
    }

    private void choose(int idx) {
        long now = Util.getMillis();
        this.selected = idx;
        this.selectTime = now;
        this.requestTime = now;
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f));
        Net.toServer(new Packets.Advance(idx));
    }

    // ===================================================== текст

    private void layoutText(float vw) {
        int wrapW = (int) ((vw - TEXT_X - 16) / TEXT_SCALE);
        List<FormattedText> split = this.font.getSplitter().splitLines(node.text, wrapW, Style.EMPTY);
        List<String> out = new ArrayList<>(split.size());
        int total = 0;
        for (FormattedText t : split) {
            String s = t.getString();
            out.add(s);
            total += s.length();
        }
        this.lines = out;

        float[] times = new float[total];
        float t = 0f;
        int i = 0;
        for (String l : out) {
            for (int c = 0; c < l.length(); c++) {
                char ch = l.charAt(c);
                t += 1f / CHARS_PER_SECOND;
                if (ch == '.' || ch == '!' || ch == '?' || ch == '…') {
                    t += 0.18f;
                } else if (ch == ',' || ch == ';' || ch == ':') {
                    t += 0.08f;
                }
                times[i++] = t;
            }
        }
        this.charTimes = times;
        this.totalChars = total;
        this.visibleChars = Math.min(visibleChars, total);
        this.layoutWidth = (int) vw;
    }

    private void updateTyping(long now) {
        if (typingDone) {
            return;
        }
        float el = (now - nodeStart) / 1000f - TEXT_START_DELAY;
        if (el < 0f) {
            return;
        }
        int n = visibleChars;
        while (n < totalChars && charTimes[n] <= el) {
            n++;
        }
        if (n > visibleChars) {
            if (n - lastBlip >= 3) {
                lastBlip = n;
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.9f, 0.10f));
            }
            visibleChars = n;
        }
        if (visibleChars >= totalChars) {
            typingDone = true;
            if (!rows.isEmpty() && choicesStart < 0) {
                choicesStart = now;
            }
        }
    }

    // ===================================================== рендер

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Фон не затемняем: за экраном живой кадр с NPC. Курсор — обычный системный.
        long now = Util.getMillis();
        float dt = lastFrame < 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        float s = scale();
        float vw = this.width / s;

        if (layoutWidth != (int) vw) {
            layoutText(vw);
        }
        updateTyping(now);

        RenderSystem.enableBlend();
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.scale(s, s, 1f);
        drawChoices(g, now, dt, mouseX / s, mouseY / s);
        drawDialogueBox(g, now, vw);
        ps.popPose();
    }

    // ---------------------------------------------- выбор ответа

    private float rowAppear(int i, long now) {
        if (choicesStart < 0) {
            return 0f;
        }
        return UiUtil.clamp01((now - choicesStart - i * ROW_STAGGER_MS) / (float) ROW_APPEAR_MS);
    }

    private boolean inRow(Row r, int i, float vmx, float vmy) {
        float y = ROW_TOP + i * ROW_PITCH;
        float w = ICON_BOX_W + 1 + (float) Math.ceil(r.textW) + TEXT_PAD * 2;
        return vmx >= ROW_X && vmx <= ROW_X + w && vmy >= y && vmy < y + ROW_H;
    }

    private void drawChoices(GuiGraphics g, long now, float dt, float vmx, float vmy) {
        if (choicesStart < 0) {
            return;
        }
        float hoverK = 1f - (float) Math.exp(-dt * 16f);
        float selK = selected >= 0 ? UiUtil.clamp01((now - selectTime) / (float) SELECT_MS) : 0f;
        boolean locked = selected >= 0 || waiting;

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float appear = rowAppear(i, now);
            float ea = UiUtil.easeOutCubic(appear);

            float alpha = ea;
            float slide = (1f - ea) * -56f;

            boolean hovered = !locked && appear >= 0.6f && inRow(r, i, vmx, vmy);
            if (selected == i) {
                hovered = true;
                slide += 10f * UiUtil.easeOutCubic(selK);
                alpha *= 1f - UiUtil.smoothstep((selK - 0.55f) / 0.45f);
            } else if (selected >= 0) {
                alpha *= 1f - UiUtil.easeOutCubic(Math.min(1f, selK * 1.8f));
                slide -= 40f * selK;
            }

            r.hover += ((hovered ? 1f : 0f) - r.hover) * hoverK;
            if (hovered && !r.wasHovered && !locked) {
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.25f));
            }
            r.wasHovered = hovered;

            if (alpha > 0.02f) {
                drawRow(g, r, ROW_X + slide, ROW_TOP + i * ROW_PITCH, alpha);
            }
        }
    }

    private void drawRow(GuiGraphics g, Row r, float x, float y, float alpha) {
        float h = UiUtil.smoothstep(r.hover);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x + 4f * h, y, 0f);

        int panelW = (int) Math.ceil(r.textW) + TEXT_PAD * 2;

        g.fill(0, 0, ICON_BOX_W, ROW_H, UiUtil.mulAlpha(UiUtil.lerpColor(0x99202026, 0xB0404050, h), alpha));
        g.fill(ICON_BOX_W, 0, ICON_BOX_W + 1, ROW_H, UiUtil.mulAlpha(UiUtil.lerpColor(0xFFE8E8F0, 0xFFFFE9A8, h), alpha));
        g.fill(ICON_BOX_W + 1, 0, ICON_BOX_W + 1 + panelW, ROW_H, UiUtil.mulAlpha(UiUtil.lerpColor(0x8C000000, 0xB02E2E3C, h), alpha));

        if (r.icon != null) {
            g.setColor(1f, 1f, 1f, alpha);
            g.blit(r.icon, (ICON_BOX_W - ICON_SIZE) / 2, (ROW_H - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE, 0f, 0f, 32, 32, 32, 32);
            g.setColor(1f, 1f, 1f, 1f);
        }

        ps.pushPose();
        ps.translate(ICON_BOX_W + 1 + TEXT_PAD, (ROW_H - 8f * TEXT_SCALE) / 2f + 0.5f, 0f);
        ps.scale(TEXT_SCALE, TEXT_SCALE, 1f);
        g.drawString(this.font, r.choice.text, 0, 0, UiUtil.mulAlpha(UiUtil.lerpColor(0xFFFFFFFF, 0xFFFFF3C4, h), alpha), true);
        ps.popPose();

        ps.popPose();
    }

    // ---------------------------------------------- диалоговое окно

    private void drawDialogueBox(GuiGraphics g, long now, float vw) {
        float in = UiUtil.clamp01((now - openTime - BOX_DELAY_MS) / (float) BOX_ANIM_MS);
        if (in <= 0f) {
            return;
        }
        float e = UiUtil.easeOutCubic(in);
        int right = (int) vw - 2;

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0f, (1f - e) * 120f, 0f);

        fillShape(g, 0, right, 0xFF14141C, 0xFF14141C);
        fillShape(g, 1, right, 0xFFF4F4FC, 0xFFE0E0EE);
        fillShape(g, 2, right, 0xFF8E8EA6, 0xFF8E8EA6);
        fillShape(g, 3, right, 0xFFD2D2E4, 0xFFB4B4CC);

        panelRect(g, PORTRAIT_L, PORTRAIT_T, BOX_LEFT, BOX_BOTTOM, 0xFFD6D6E8, 0xFFB8B8D0);
        g.fill(8, PORTRAIT_T + 6, BOX_LEFT - 6, BOX_BOTTOM - 4, 0xFF3A3A4C);
        g.fillGradient(9, PORTRAIT_T + 7, BOX_LEFT - 7, BOX_BOTTOM - 5, 0xFFC8C8DC, 0xFFA4A4BE);

        if (!node.speaker.isEmpty()) {
            panelRect(g, TAB_L, TAB_T, TAB_R, TAB_B, 0xFFC6C6DA, 0xFFA6A6C0);
            float nameW = this.font.width(node.speaker) * TEXT_SCALE;
            outlined(g, node.speaker, (TAB_L + TAB_R) / 2f - nameW / 2f, TAB_T + (TAB_B - TAB_T - 8f * TEXT_SCALE) / 2f + 0.5f,
                    TEXT_SCALE, 0xFFFFFFFF, 0xFF2A2A3A);
        }

        float ty = TEXT_CENTER_Y - lines.size() * LINE_H / 2f;
        int remaining = visibleChars;
        for (String l : lines) {
            int n = Math.min(l.length(), Math.max(0, remaining));
            remaining -= l.length();
            if (n > 0) {
                outlined(g, n == l.length() ? l : l.substring(0, n), TEXT_X, ty, TEXT_SCALE, 0xFFFFFFFF, 0xFF2A2A3A);
            }
            ty += LINE_H;
        }

        if (typingDone && rows.isEmpty() && selected < 0 && !waiting) {
            int bob = (int) Math.round(Math.sin(now / 180.0) * 1.5);
            int tx = right - 20, tyy = BOX_BOTTOM - 20 + bob;
            for (int i = 0; i < 5; i++) {
                g.fill(tx + i, tyy + i, tx + 9 - i, tyy + i + 1, 0xFF2A2A3A);
            }
        }

        if (this.minecraft.player != null) {
            DialogueCamera.portraitRender = true; // иначе RenderPlayerEvent скроет и портрет
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, PORTRAIT_FEET_X, PORTRAIT_FEET_Y, PORTRAIT_SCALE,
                        PORTRAIT_LOOK_X, PORTRAIT_LOOK_Y, this.minecraft.player);
            } finally {
                DialogueCamera.portraitRender = false;
            }
        }

        ps.popPose();
    }

    private static int shapeColor(int y, int cTop, int cBot) {
        return UiUtil.lerpColor(cTop, cBot, (y - BOX_TOP) / (float) (BOX_BOTTOM - BOX_TOP));
    }

    /**
     * Заливка основной рамки, «сжатой» на k пикселей.
     * Оптимизация: скос рисуется построчно (15 полос), а прямоугольная часть — ОДНИМ градиентом.
     */
    private void fillShape(GuiGraphics g, int k, int right, int cTop, int cBot) {
        int xl = BOX_LEFT + k;
        int xr = right - k;
        int yb = BOX_BOTTOM - k;
        int yMid = BOX_MID + k;
        int yEnd = Math.min(yMid, yb);
        for (int y = BOX_TOP + k; y < yEnd; y++) {
            int x2 = Math.min(xr, (int) Math.round(y + SLOPE_C - 1.414 * k));
            if (x2 > xl) {
                g.fill(xl, y, x2, y + 1, shapeColor(y, cTop, cBot));
            }
        }
        if (yb > yMid && xr > xl) {
            g.fillGradient(xl, yMid, xr, yb, shapeColor(yMid, cTop, cBot), shapeColor(yb, cTop, cBot));
        }
    }

    private void panelRect(GuiGraphics g, int x1, int y1, int x2, int y2, int bodyTop, int bodyBottom) {
        g.fill(x1, y1, x2, y2, 0xFF14141C);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xFFF4F4FC);
        g.fill(x1 + 2, y1 + 2, x2 - 2, y2 - 2, 0xFF8E8EA6);
        g.fillGradient(x1 + 3, y1 + 3, x2 - 3, y2 - 3, bodyTop, bodyBottom);
    }

    private void outlined(GuiGraphics g, String s, float x, float y, float sc, int color, int outline) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0f);
        ps.scale(sc, sc, 1f);
        g.drawString(this.font, s, 1, 0, outline, false);
        g.drawString(this.font, s, -1, 0, outline, false);
        g.drawString(this.font, s, 0, 1, outline, false);
        g.drawString(this.font, s, 0, -1, outline, false);
        g.drawString(this.font, s, 0, 0, color, false);
        ps.popPose();
    }
}
