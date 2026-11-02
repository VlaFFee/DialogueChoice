package com.lastreality.dialogue.client.editor;

import com.lastreality.dialogue.CameraSettings;
import com.lastreality.dialogue.Dialogue;
import com.lastreality.dialogue.client.ClientDialogues;
import com.lastreality.dialogue.client.DialogueCamera;
import com.lastreality.dialogue.client.ui.Icons;
import com.lastreality.dialogue.client.ui.Theme;
import com.lastreality.dialogue.client.ui.UButton;
import com.lastreality.dialogue.client.ui.UDropdown;
import com.lastreality.dialogue.client.ui.UList;
import com.lastreality.dialogue.client.ui.UPreviews;
import com.lastreality.dialogue.client.ui.USegmented;
import com.lastreality.dialogue.client.ui.USwitch;
import com.lastreality.dialogue.client.ui.UTextField;
import com.lastreality.dialogue.client.ui.UTrackpad;
import com.lastreality.dialogue.client.ui.UWidget;
import com.lastreality.dialogue.network.Net;
import com.lastreality.dialogue.network.Packets;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Редактор диалога.
 *
 * Слева — «док»: шапка (имя, Играть, Сохранить, Закрыть), список узлов и инспектор с вкладками
 * «Реплика / Ответы / Камера / Диалог». Остальной экран остаётся свободным — там идёт живой предпросмотр кадра
 * (с направляющими «правило третей»). Панель можно свернуть, чтобы увидеть кадр целиком.
 */
public class DialogueEditorScreen extends Screen {
    private static final int HH = 26; // высота шапки
    private static final String[] TABS = {"Реплика", "Ответы", "Камера", "Диалог"};
    private static final String ICON_DIR = "lastreality:textures/gui/icons/";
    private static final String[] BUILTIN = {"ecology", "math", "art", "sociology", "translation", "biology"};

    private record Label(String text, int x, int baseY, int color) {
    }

    private final String name;
    private final Dialogue draft;
    @Nullable
    private final Entity target;

    private String nodeId;
    private int tab;
    private int choiceIdx;
    private boolean preview;
    private boolean dirty;
    private boolean collapsed;

    // раскладка
    private int lw, iw, dw, contentTop;
    private float scroll;
    private int maxScroll;

    // содержимое инспектора (прокручивается)
    private final List<Label> labels = new ArrayList<>();
    private final List<AbstractWidget> content = new ArrayList<>();
    private final Set<AbstractWidget> statics = new HashSet<>();
    private final Map<AbstractWidget, Integer> bases = new HashMap<>();
    private final Map<AbstractWidget, Boolean> baseActive = new HashMap<>();
    @Nullable
    private UTextField idField;
    @Nullable
    private UList nodeList;

    private String toast = "";
    private long toastUntil;
    private int toastColor = Theme.OK;

    public DialogueEditorScreen(String name, Dialogue draft, @Nullable Entity target) {
        super(Component.literal("Редактор диалога"));
        this.name = name;
        this.draft = draft;
        this.draft.sanitize();
        this.target = target;
        this.nodeId = draft.start;
    }

    // ===================================================== вспомогательное

    private Dialogue.Node node() {
        return draft.nodes.get(nodeId);
    }

    private CameraSettings activeCamera() {
        Dialogue.Node n = node();
        return n.camera != null ? n.camera : draft.camera;
    }

    private boolean hasTarget() {
        return target != null && target.isAlive();
    }

    private void toast(String text, boolean ok) {
        toast = text;
        toastColor = ok ? Theme.OK : Theme.DANGER;
        toastUntil = Util.getMillis() + 3200;
    }

    private void markDirty() {
        dirty = true;
        refreshNodeList(false);
    }

    private <T extends AbstractWidget> T add(T w, int baseY) {
        w.setY(baseY - (int) scroll);
        addWidget(w);
        content.add(w);
        bases.put(w, baseY);
        baseActive.put(w, w.active);
        return w;
    }

    private <T extends AbstractWidget> T addStatic(T w, int baseY) {
        statics.add(w);
        return add(w, baseY);
    }

    /** Подпись над полем; возвращает y, с которого рисовать само поле. */
    private int label(String text, int x, int y) {
        labels.add(new Label(text, x, y, Theme.DIM));
        return y + 11;
    }

    private void note(String text, int x, int y) {
        labels.add(new Label(text, x, y, Theme.FAINT));
    }

    private UTextField field(int x, int y, int w, String value, int max, String hint, Consumer<String> onChange) {
        return add(new UTextField(this.font, x, y, w, 18, hint, max, value, v -> {
            onChange.accept(v);
            markDirty();
        }), y);
    }

    private UTrackpad pad(int x, int y, int w, String label, double min, double max, double def, double val, int dec,
                          Consumer<Double> set, String tip) {
        UTrackpad p = new UTrackpad(x, y, w, 18, label, min, max, def, val, dec, v -> {
            set.accept(v);
            markDirty();
        });
        p.tip(tip + " (двойной клик — сброс)");
        return add(p, y);
    }

    private UDropdown nextDropdown(int x, int y, int w, @Nullable String current, Consumer<String> setter) {
        List<String> ids = new ArrayList<>(draft.nodes.keySet());
        List<String> options = new ArrayList<>();
        options.add("— конец диалога —");
        options.addAll(ids);
        int sel = current == null ? 0 : ids.indexOf(current) + 1;
        return add(new UDropdown(x, y, w, 18, options, Math.max(0, sel), i -> {
            setter.accept(i == 0 ? null : ids.get(i - 1));
            markDirty();
        }), y);
    }

    private static String joinCmds(List<String> l) {
        return String.join(" ;; ", l);
    }

    private static List<String> parseCmds(String s) {
        List<String> out = new ArrayList<>();
        for (String part : s.split(";;")) {
            String t = part.trim();
            if (!t.isEmpty() && out.size() < Dialogue.MAX_CMDS) {
                out.add(t.length() > Dialogue.MAX_CMD_LEN ? t.substring(0, Dialogue.MAX_CMD_LEN) : t);
            }
        }
        return out;
    }

    // ===================================================== построение

    @Override
    protected void init() {
        UDropdown.open = null;
        labels.clear();
        content.clear();
        statics.clear();
        bases.clear();
        baseActive.clear();
        idField = null;
        nodeList = null;

        if (collapsed) {
            addRenderableWidget(new UButton(6, 4, 22, 20, "", Icons.CHEVRON_R, UButton.Style.DEFAULT,
                    () -> {
                        collapsed = false;
                        rebuildWidgets();
                    }).tip("Показать панель"));
            return;
        }

        lw = Mth.clamp((int) (this.width * 0.15), 118, 150);
        iw = Mth.clamp((int) (this.width * 0.30), 250, 300);
        dw = lw + iw;
        contentTop = HH + 30;

        buildHeader();
        buildRail();
        addRenderableWidget(new USegmented(lw + 10, HH + 4, iw - 20, 20, TABS, tab, i -> {
            commitId();
            tab = i;
            scroll = 0;
            rebuildWidgets();
        }));

        int endY = switch (tab) {
            case 0 -> buildText(contentTop + 6);
            case 1 -> buildChoices(contentTop + 6);
            case 2 -> buildCamera(contentTop + 6);
            default -> buildDialogueTab(contentTop + 6);
        };
        int viewH = this.height - contentTop;
        maxScroll = Math.max(0, endY + 10 - contentTop - viewH);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        applyScroll();
    }

    private void buildHeader() {
        int x = dw - 28;
        addRenderableWidget(new UButton(x, 3, 22, 20, "", Icons.CROSS, UButton.Style.GHOST, this::onClose).tip("Закрыть (Esc)"));
        x -= 82;
        addRenderableWidget(new UButton(x, 3, 78, 20, "Сохранить", null, UButton.Style.PRIMARY, this::save).tip("Сохранить на сервере (Ctrl+S)"));
        x -= 70;
        addRenderableWidget(new UButton(x, 3, 66, 20, "Играть", Icons.PLAY, UButton.Style.DEFAULT, this::play)
                .tip("Запустить диалог на вас (сначала сохраните)"));
        x -= 26;
        addRenderableWidget(new UButton(x, 3, 22, 20, "", Icons.CHEVRON_L, UButton.Style.GHOST, () -> {
            commitId();
            collapsed = true;
            rebuildWidgets();
        }).tip("Свернуть панель — видно весь кадр"));
    }

    private void buildRail() {
        addRenderableWidget(new UButton(lw - 28, HH + 4, 22, 18, "", Icons.PLUS, UButton.Style.DEFAULT, this::addNode).tip("Новый узел"));
        nodeList = new UList(6, HH + 28, lw - 10, this.height - HH - 34, this::selectNode, this::askDeleteNode);
        addRenderableWidget(nodeList);
        refreshNodeList(true);
    }

    private void refreshNodeList(boolean reveal) {
        if (nodeList == null) {
            return;
        }
        List<UList.Item> items = new ArrayList<>();
        int sel = -1;
        int i = 0;
        for (Map.Entry<String, Dialogue.Node> e : draft.nodes.entrySet()) {
            Dialogue.Node n = e.getValue();
            String sub = (n.speaker.isEmpty() ? "" : n.speaker + ": ") + n.text;
            items.add(new UList.Item(e.getKey(), sub.isEmpty() ? "(пусто)" : sub, e.getKey().equals(draft.start)));
            if (e.getKey().equals(nodeId)) {
                sel = i;
            }
            i++;
        }
        nodeList.setItems(items, sel);
        if (reveal && sel >= 0) {
            nodeList.reveal(sel);
        }
    }

    // ---- вкладка «Реплика»

    private int buildText(int y) {
        Dialogue.Node n = node();
        int x = lw + 10;
        int w = iw - 20;

        y = label("ID узла  (Enter — применить)", x, y);
        idField = add(new UTextField(this.font, x, y, w, 18, "id", 32, nodeId, v -> {
        }), y);
        y += 25;

        y = label("Говорящий", x, y);
        field(x, y, w, n.speaker, 64, "имя на табличке", v -> n.speaker = v);
        y += 25;

        y = label("Текст реплики", x, y);
        field(x, y, w, n.text, Dialogue.MAX_TEXT, "что скажет персонаж", v -> n.text = v);
        y += 22;
        addStatic(new UPreviews.Text(x, y, w, 46, () -> n.text), y);
        y += 54;

        if (n.choices.isEmpty()) {
            y = label("Что дальше", x, y);
            nextDropdown(x, y, w, n.next, v -> n.next = v);
            y += 25;
        } else {
            note("Переход выбирается ответами (вкладка «Ответы»).", x, y);
            y += 16;
        }

        y = label("Команды при входе в узел", x, y);
        field(x, y, w, joinCmds(n.commands), 1500, "give %player% apple", v -> n.commands = parseCmds(v));
        y += 21;
        note("Несколько команд через ;;    %player% — ник, %npc% — UUID", x, y);
        return y + 14;
    }

    // ---- вкладка «Ответы»

    private int buildChoices(int y) {
        Dialogue.Node n = node();
        int x = lw + 10;
        int w = iw - 20;

        int cx = x;
        for (int i = 0; i < n.choices.size(); i++) {
            final int idx = i;
            UButton chip = new UButton(cx, y, 24, 20, String.valueOf(i + 1), null, UButton.Style.DEFAULT, () -> {
                choiceIdx = idx;
                rebuildWidgets();
            });
            chip.toggled = i == choiceIdx;
            add(chip, y);
            cx += 26;
        }
        if (n.choices.size() < Dialogue.MAX_CHOICES) {
            add(new UButton(cx, y, 24, 20, "", Icons.PLUS, UButton.Style.DEFAULT, this::addChoice).tip("Добавить ответ"), y);
        }
        y += 30;

        if (n.choices.isEmpty()) {
            note("Ответов нет: игрок просто нажмёт «дальше».", x, y + 2);
            return y + 20;
        }
        choiceIdx = Mth.clamp(choiceIdx, 0, n.choices.size() - 1);
        Dialogue.Choice c = n.choices.get(choiceIdx);

        y = label("Текст ответа", x, y);
        field(x, y, w, c.text, 200, "что выберет игрок", v -> c.text = v);
        y += 25;

        y = label("Иконка (текстура 32×32)", x, y);
        addStatic(new UPreviews.Icon(x, y, 18, () -> c.icon), y);
        field(x + 22, y, w - 22 - 24, c.icon, 200, ICON_DIR + "…", v -> c.icon = v.trim());
        add(new UButton(x + w - 20, y, 20, 18, "", Icons.CHEVRON_R, UButton.Style.DEFAULT, () -> {
            int cur = -1;
            for (int i = 0; i < BUILTIN.length; i++) {
                if (c.icon.equals(ICON_DIR + BUILTIN[i] + ".png")) {
                    cur = i;
                }
            }
            c.icon = cur + 1 >= BUILTIN.length ? "" : ICON_DIR + BUILTIN[cur + 1] + ".png";
            markDirty();
            rebuildWidgets();
        }).tip("Следующая встроенная иконка"), y);
        y += 25;

        y = label("Куда ведёт", x, y);
        nextDropdown(x, y, w, c.next, v -> c.next = v);
        y += 25;

        y = label("Команды при выборе", x, y);
        field(x, y, w, joinCmds(c.commands), 1500, "scoreboard players add %player% quest 1", v -> c.commands = parseCmds(v));
        y += 21;
        note("Несколько команд через ;;", x, y);
        y += 20;

        add(new UButton(x, y, w, 20, "Удалить этот ответ", Icons.TRASH, UButton.Style.DANGER, this::removeChoice), y);
        return y + 26;
    }

    // ---- вкладка «Камера»

    private int buildCamera(int y) {
        Dialogue.Node n = node();
        CameraSettings cs = activeCamera();
        boolean own = n.camera != null;
        CameraSettings def = new CameraSettings();
        int x = lw + 10;
        int w = iw - 20;
        int cw = (w - 6) / 2;
        int x2 = x + cw + 6;

        y = label("Ракурс применяется к", x, y);
        add(new USegmented(x, y, w, 20, new String[]{"Всему диалогу", "Только этому узлу"}, own ? 1 : 0, i -> setOwnCamera(i == 1)), y);
        y += 30;

        y = label("Положение камеры", x, y);
        pad(x, y, cw, "Дистанция", 0.5, 6, def.distance, cs.distance, 2, v -> cs.distance = v, "Расстояние до собеседника");
        pad(x2, y, cw, "Высота", -1.5, 1.5, def.height, cs.height, 2, v -> cs.height = v, "Высота камеры относительно глаз NPC");
        y += 22;
        pad(x, y, cw, "Угол", -180, 180, def.angle, cs.angle, 0, v -> cs.angle = v, "Поворот камеры вокруг NPC, градусы");
        pad(x2, y, cw, "Плавность", 0.5, 20, def.speed, cs.speed, 1, v -> cs.speed = v, "Чем больше, тем резче камера догоняет кадр");
        y += 28;

        y = label("Кадрирование", x, y);
        pad(x, y, cw, "Смещение", -2, 2, def.sideShift, cs.sideShift, 2, v -> cs.sideShift = v, "Больше = NPC правее в кадре");
        pad(x2, y, cw, "Наклон", -1.5, 1.5, def.aimY, cs.aimY, 2, v -> cs.aimY = v, "Выше/ниже лицо в кадре");
        y += 22;
        pad(x, y, cw, "FOV", 0.3, 1.2, def.fov, cs.fov, 2, v -> cs.fov = v.floatValue(), "Масштаб кадра: меньше = ближе");
        y += 28;

        y = label("Поведение", x, y);
        add(new USegmented(x, y, w, 20, new String[]{"От игрока", "От взгляда NPC"}, cs.anchorNpc ? 1 : 0, i -> {
            cs.anchorNpc = i == 1;
            markDirty();
            rebuildWidgets();
        }).tip("От чего отсчитывается угол: от игрока (над плечом) или от того, куда смотрит NPC (фикс. кадр)"), y);
        y += 24;
        add(new USwitch(x, y, w, 20, "NPC смотрит в камеру", cs.faceNpc && !cs.anchorNpc, v -> {
            cs.faceNpc = v;
            markDirty();
        }).tip("Работает при режиме «От игрока»"), y);
        y += 30;

        UButton prev = new UButton(x, y, cw, 22, preview ? "Предпросмотр ВКЛ" : "Предпросмотр", Icons.CAMERA,
                UButton.Style.DEFAULT, this::togglePreview);
        prev.toggled = preview;
        prev.active = hasTarget();
        prev.tip(hasTarget() ? "Живая камера на цели — слайдеры меняют кадр сразу"
                : "Нет цели: посмотрите на NPC и откройте редактор заново");
        add(prev, y);
        UButton cap = new UButton(x2, y, cw, 22, "Снять с вида", null, UButton.Style.DEFAULT, this::startCapture);
        cap.active = hasTarget();
        cap.tip("Встаньте/летайте где нужно, нажмите Enter — вид станет ракурсом");
        add(cap, y);
        y += 28;
        add(new UButton(x, y, w, 20, "Сбросить ракурс", null, UButton.Style.GHOST, () -> {
            cs.set(new CameraSettings());
            markDirty();
            rebuildWidgets();
        }), y);
        return y + 26;
    }

    // ---- вкладка «Диалог»

    private int buildDialogueTab(int y) {
        int x = lw + 10;
        int w = iw - 20;

        y = label("Имя диалога", x, y);
        labels.add(new Label(name, x + 2, y, Theme.TEXT));
        y += 20;

        y = label("Стартовый узел", x, y);
        List<String> ids = new ArrayList<>(draft.nodes.keySet());
        add(new UDropdown(x, y, w, 18, ids, Math.max(0, ids.indexOf(draft.start)), i -> {
            draft.start = ids.get(i);
            markDirty();
        }), y);
        y += 28;

        y = label("Привязка к NPC", x, y);
        labels.add(new Label(hasTarget() ? "Цель: " + target.getName().getString() : "Цели нет — посмотрите на NPC и откройте редактор",
                x + 2, y, hasTarget() ? Theme.TEXT : Theme.WARN));
        y += 16;
        int cw = (w - 6) / 2;
        UButton bind = new UButton(x, y, cw, 20, "Привязать", null, UButton.Style.PRIMARY, () -> bind(name));
        bind.active = hasTarget();
        add(bind, y);
        UButton unbind = new UButton(x + cw + 6, y, cw, 20, "Отвязать", null, UButton.Style.DEFAULT, () -> bind(""));
        unbind.active = hasTarget();
        add(unbind, y);
        y += 26;
        note("ПКМ по привязанному NPC запускает диалог.", x, y);
        y += 11;
        note("(тег dialogue." + name + "; Shift+ПКМ в креативе — обычное действие)", x, y);
        y += 24;

        y = label("Сводка", x, y);
        int choices = 0;
        for (Dialogue.Node n : draft.nodes.values()) {
            choices += n.choices.size();
        }
        labels.add(new Label("Узлов: " + draft.nodes.size() + "    Ответов: " + choices, x + 2, y, Theme.TEXT));
        return y + 14;
    }

    // ===================================================== прокрутка

    private void applyScroll() {
        for (AbstractWidget w : content) {
            int y = bases.get(w) - (int) scroll;
            w.setY(y);
            if (!statics.contains(w)) {
                // вне видимой области (под вкладками/за краем) клики не принимаем
                boolean inside = y >= contentTop - 1 && y + w.getHeight() <= this.height + 1;
                w.active = baseActive.getOrDefault(w, true) && inside;
            }
        }
    }

    // ===================================================== узлы

    private void selectNode(int idx) {
        commitId();
        List<String> ids = new ArrayList<>(draft.nodes.keySet());
        if (idx < 0 || idx >= ids.size()) {
            return;
        }
        nodeId = ids.get(idx);
        choiceIdx = 0;
        scroll = 0;
        refreshPreview();
        rebuildWidgets();
    }

    private void askDeleteNode(int idx) {
        List<String> ids = new ArrayList<>(draft.nodes.keySet());
        if (idx < 0 || idx >= ids.size()) {
            return;
        }
        if (draft.nodes.size() <= 1) {
            toast("Нельзя удалить единственный узел", false);
            return;
        }
        String id = ids.get(idx);
        this.minecraft.setScreen(new ConfirmScreen(yes -> {
            this.minecraft.setScreen(this);
            if (yes) {
                deleteNode(id);
            }
        }, Component.literal("Удалить узел «" + id + "»?"), Component.literal("Ссылки на него будут сброшены в «конец диалога».")));
    }

    private void deleteNode(String id) {
        draft.nodes.remove(id);
        draft.sanitize();
        if (nodeId.equals(id)) {
            nodeId = draft.start;
        }
        choiceIdx = 0;
        markDirty();
        refreshPreview();
        rebuildWidgets();
    }

    private void addNode() {
        commitId();
        if (draft.nodes.size() >= Dialogue.MAX_NODES) {
            return;
        }
        int i = draft.nodes.size() + 1;
        String id;
        do {
            id = "node_" + i++;
        } while (draft.nodes.containsKey(id));
        Dialogue.Node nn = new Dialogue.Node();
        nn.speaker = node().speaker;
        nn.text = "...";
        draft.nodes.put(id, nn);
        nodeId = id;
        choiceIdx = 0;
        scroll = 0;
        tab = 0;
        markDirty();
        refreshPreview();
        rebuildWidgets();
    }

    /** Применяет новый id из поля (если изменён и корректен). */
    private void commitId() {
        if (idField == null) {
            return;
        }
        String v = idField.getValue().trim();
        if (v.equals(nodeId)) {
            return;
        }
        if (!Dialogue.NODE_ID.matcher(v).matches() || draft.nodes.containsKey(v)) {
            toast("Некорректный или занятый id (A-Z a-z 0-9 _, до 32)", false);
            idField.setValue(nodeId);
            return;
        }
        Map<String, Dialogue.Node> copy = new LinkedHashMap<>(draft.nodes);
        draft.nodes.clear();
        for (Map.Entry<String, Dialogue.Node> e : copy.entrySet()) {
            draft.nodes.put(e.getKey().equals(nodeId) ? v : e.getKey(), e.getValue());
        }
        for (Dialogue.Node n : draft.nodes.values()) {
            if (nodeId.equals(n.next)) {
                n.next = v;
            }
            for (Dialogue.Choice c : n.choices) {
                if (nodeId.equals(c.next)) {
                    c.next = v;
                }
            }
        }
        if (nodeId.equals(draft.start)) {
            draft.start = v;
        }
        nodeId = v;
        dirty = true;
        toast("Узел переименован", true);
    }

    // ===================================================== ответы

    private void addChoice() {
        Dialogue.Node n = node();
        if (n.choices.size() >= Dialogue.MAX_CHOICES) {
            toast("Максимум ответов: " + Dialogue.MAX_CHOICES, false);
            return;
        }
        Dialogue.Choice c = new Dialogue.Choice();
        c.text = "Ответ";
        n.choices.add(c);
        choiceIdx = n.choices.size() - 1;
        tab = 1;
        markDirty();
        rebuildWidgets();
    }

    private void removeChoice() {
        Dialogue.Node n = node();
        if (!n.choices.isEmpty()) {
            n.choices.remove(choiceIdx);
            choiceIdx = Math.max(0, choiceIdx - 1);
            markDirty();
            rebuildWidgets();
        }
    }

    // ===================================================== камера

    private void setOwnCamera(boolean own) {
        Dialogue.Node n = node();
        n.camera = own ? (n.camera != null ? n.camera : draft.camera.copy()) : null;
        markDirty();
        refreshPreview();
        rebuildWidgets();
    }

    private void refreshPreview() {
        if (preview) {
            DialogueCamera.INSTANCE.setSettings(activeCamera());
        }
    }

    private void togglePreview() {
        if (preview) {
            DialogueCamera.INSTANCE.end();
            preview = false;
        } else if (!hasTarget()) {
            toast("Нет цели: посмотрите на NPC и откройте редактор заново", false);
        } else if (DialogueCamera.INSTANCE.begin(target, activeCamera())) {
            preview = true;
        } else {
            toast("Камера ещё возвращается, повторите через секунду", false);
        }
        rebuildWidgets();
    }

    private void startCapture() {
        if (!hasTarget()) {
            toast("Нет цели", false);
            return;
        }
        CaptureController.begin(this); // закроет экран; removed() остановит предпросмотр
    }

    /** Вызывается CaptureController-ом после нажатия клавиши съёмки. */
    public void applyCapture() {
        if (!hasTarget()) {
            toast("Цель пропала", false);
            return;
        }
        CameraSettings cur = activeCamera();
        cur.set(DialogueCamera.captureFromView(this.minecraft, target, cur));
        dirty = true;
        tab = 2;
        toast("Ракурс снят с вашего вида", true);
    }

    // ===================================================== сервер

    private void save() {
        commitId();
        draft.sanitize();
        String json = draft.toJson().toString();
        if (json.length() > Packets.MAX_JSON) {
            toast("Диалог слишком большой", false);
            return;
        }
        List<String> chunks = new ArrayList<>();
        int i = 0;
        while (i < json.length()) {
            int end = Math.min(json.length(), i + Packets.CHUNK);
            if (end < json.length() && Character.isHighSurrogate(json.charAt(end - 1))) {
                end--;
            }
            chunks.add(json.substring(i, end));
            i = end;
        }
        for (int k = 0; k < chunks.size(); k++) {
            Net.toServer(new Packets.UploadChunk(name, k, chunks.size(), chunks.get(k)));
        }
        dirty = false;
        toast("Отправлено на сервер (результат — в чате)", true);
    }

    private void bind(String dialogueName) {
        if (!hasTarget()) {
            toast("Нет цели", false);
            return;
        }
        if (!dialogueName.isEmpty() && ClientDialogues.get(dialogueName) == null) {
            toast("Сначала сохраните диалог", false);
            return;
        }
        Net.toServer(new Packets.BindEntity(target.getId(), dialogueName));
        toast(dialogueName.isEmpty() ? "Запрос на отвязку отправлен" : "Запрос на привязку отправлен", true);
    }

    private void play() {
        if (ClientDialogues.get(name) == null) {
            toast("Сначала сохраните диалог", false);
            return;
        }
        if (dirty) {
            toast("Есть несохранённые изменения — играется сохранённая версия", false);
        }
        String npc = hasTarget() ? " " + target.getStringUUID() : "";
        this.minecraft.player.connection.sendCommand("dialogue start @s " + name + npc);
        this.minecraft.setScreen(null);
    }

    // ===================================================== ввод и окно

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (UDropdown.open != null) {
            return UDropdown.open.overlayClick(mx, my);
        }
        setFocused(null); // клик вне поля снимает фокус
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (UDropdown.open != null) {
            UDropdown.open.overlayScroll(delta);
            return true;
        }
        if (super.mouseScrolled(mx, my, delta)) {
            return true;
        }
        if (!collapsed && mx >= lw && mx < dw && my >= contentTop && maxScroll > 0) {
            scroll = Mth.clamp(scroll - (float) delta * 18f, 0, maxScroll);
            applyScroll();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && UDropdown.open != null) {
            UDropdown.open = null;
            return true;
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        if (idField != null && idField.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            commitId();
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(key, scancode, mods);
    }

    @Override
    public void onClose() {
        if (dirty) {
            this.minecraft.setScreen(new ConfirmScreen(yes -> {
                if (yes) {
                    dirty = false;
                    this.minecraft.setScreen(null);
                } else {
                    this.minecraft.setScreen(this);
                }
            }, Component.literal("Выйти без сохранения?"), Component.literal("Несохранённые изменения будут потеряны.")));
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        UDropdown.open = null;
        if (preview) {
            DialogueCamera.INSTANCE.end();
            preview = false;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ===================================================== отрисовка

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        if (preview) {
            drawGuides(g);
        }

        if (!collapsed) {
            g.fill(0, 0, dw, this.height, Theme.PANEL);
            g.fill(0, HH + 1, lw, this.height, Theme.PANEL_2);
            g.fill(lw, HH + 1, lw + 1, this.height, Theme.BORDER);
            g.fill(0, HH, dw, HH + 1, Theme.BORDER);
            g.fill(dw, 0, dw + 1, this.height, Theme.BORDER);

            String title = this.font.plainSubstrByWidth(name, dw - 250);
            Theme.text(g, title, 10, 9, Theme.TEXT);
            if (dirty) {
                int dx = 10 + this.font.width(title) + 7;
                g.fill(dx, 11, dx + 5, 16, Theme.WARN);
            }
            Theme.text(g, "УЗЛЫ", 10, HH + 10, Theme.FAINT);
        }

        super.render(g, mx, my, partialTick); // шапка, список узлов, вкладки

        if (!collapsed) {
            g.enableScissor(lw + 1, contentTop, dw, this.height);
            for (Label l : labels) {
                Theme.text(g, this.font.plainSubstrByWidth(l.text(), iw - 16), l.x(), l.baseY() - (int) scroll, l.color());
            }
            for (AbstractWidget w : content) {
                w.render(g, mx, my, partialTick);
            }
            g.disableScissor();

            if (maxScroll > 0) {
                int viewH = this.height - contentTop;
                int thumbH = Math.max(14, viewH * viewH / (viewH + maxScroll));
                int thumbY = contentTop + (int) ((viewH - thumbH) * (scroll / maxScroll));
                g.fill(dw - 3, thumbY, dw - 1, thumbY + thumbH, Theme.FAINT);
            }
        }

        if (UDropdown.open != null) {
            UDropdown.open.renderOverlay(g, mx, my);
        } else {
            renderTooltips(g, mx, my);
        }
        drawToast(g);
    }

    private void renderTooltips(GuiGraphics g, int mx, int my) {
        for (GuiEventListener c : this.children()) {
            if (c instanceof UWidget u && u.tooltip != null && u.visible
                    && mx >= u.getX() && mx < u.getX() + u.getWidth() && my >= u.getY() && my < u.getY() + u.getHeight()
                    && (!content.contains(u) || my >= contentTop)) {
                g.renderTooltip(this.font, Component.literal(u.tooltip), mx, my);
                return;
            }
        }
    }

    /** «Правило третей» и центр кадра для композиции при предпросмотре. */
    private void drawGuides(GuiGraphics g) {
        int c = 0x38FFFFFF;
        g.fill(this.width / 3, 0, this.width / 3 + 1, this.height, c);
        g.fill(this.width * 2 / 3, 0, this.width * 2 / 3 + 1, this.height, c);
        g.fill(0, this.height / 3, this.width, this.height / 3 + 1, c);
        g.fill(0, this.height * 2 / 3, this.width, this.height * 2 / 3 + 1, c);
        int cx = this.width / 2;
        int cy = this.height / 2;
        g.fill(cx - 5, cy, cx + 6, cy + 1, 0x80FFFFFF);
        g.fill(cx, cy - 5, cx + 1, cy + 6, 0x80FFFFFF);
    }

    private void drawToast(GuiGraphics g) {
        long left = toastUntil - Util.getMillis();
        if (left <= 0) {
            return;
        }
        float a = Math.min(1f, left / 400f);
        if (a < 0.08f) {
            return;
        }
        int tw = this.font.width(toast) + 24;
        int cx = collapsed ? this.width / 2 : (dw + this.width) / 2;
        int x = cx - tw / 2;
        int y = this.height - 36;
        Theme.frame(g, x, y, tw, 22, Theme.alpha(toastColor, a), Theme.alpha(0xFF14161E, a));
        g.fill(x + 1, y + 2, x + 3, y + 20, Theme.alpha(toastColor, a));
        g.drawString(this.font, toast, x + 12, y + 7, Theme.alpha(Theme.TEXT, a), false);
    }
}
