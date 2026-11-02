package com.lastreality.dialogue.client.editor;

import com.lastreality.dialogue.Dialogue;
import com.lastreality.dialogue.client.ClientDialogues;
import com.lastreality.dialogue.client.ui.Icons;
import com.lastreality.dialogue.client.ui.Theme;
import com.lastreality.dialogue.client.ui.UButton;
import com.lastreality.dialogue.client.ui.UList;
import com.lastreality.dialogue.client.ui.UTextField;
import com.lastreality.dialogue.network.Net;
import com.lastreality.dialogue.network.Packets;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Список диалогов: карточки, поле «новый диалог». Клик по карточке открывает редактор. */
public class DialogueListScreen extends Screen {
    @Nullable
    private final Entity target;
    private UTextField newField;
    private UList list;
    private List<String> names = List.of();
    private int shownVersion = -1;
    private int px, py, pw, ph;
    private String error = "";
    private long errorUntil;

    public DialogueListScreen(@Nullable Entity target) {
        super(Component.literal("Диалоги"));
        this.target = target;
    }

    @Override
    protected void init() {
        shownVersion = ClientDialogues.version();
        pw = Math.min(380, this.width - 40);
        ph = this.height - 40;
        px = (this.width - pw) / 2;
        py = 20;

        String keep = newField == null ? "" : newField.getValue();
        newField = new UTextField(this.font, px + 16, py + 52, pw - 32 - 96, 20, "новый диалог: имя_латиницей", 40, keep, v -> {
        });
        addRenderableWidget(newField);
        addRenderableWidget(new UButton(px + pw - 16 - 90, py + 52, 90, 20, "Создать", Icons.PLUS, UButton.Style.PRIMARY,
                () -> openByName(newField.getValue())).tip("Создать новый диалог (или открыть, если имя уже есть)"));

        names = new ArrayList<>(ClientDialogues.names());
        List<UList.Item> items = new ArrayList<>();
        for (String n : names) {
            Dialogue d = ClientDialogues.get(n);
            int choices = 0;
            if (d != null) {
                for (Dialogue.Node node : d.nodes.values()) {
                    choices += node.choices.size();
                }
            }
            items.add(new UList.Item(n, d == null ? "" : "Узлов: " + d.nodes.size() + "   Ответов: " + choices, false));
        }
        list = new UList(px + 16, py + 86, pw - 32, ph - 86 - 44, i -> openByName(names.get(i)), this::confirmDelete);
        list.setItems(items, -1);
        addRenderableWidget(list);

        addRenderableWidget(new UButton(px + pw - 16 - 90, py + ph - 32, 90, 20, "Закрыть", null, UButton.Style.DEFAULT, this::onClose));
    }

    @Override
    public void tick() {
        if (shownVersion != ClientDialogues.version()) {
            rebuildWidgets();
        }
    }

    private void openByName(String raw) {
        String name = raw.trim().toLowerCase(Locale.ROOT);
        if (!Dialogue.validName(name)) {
            error = "Имя: латиница a-z, цифры и _ (до 40 символов)";
            errorUntil = Util.getMillis() + 3500;
            return;
        }
        Dialogue d = ClientDialogues.get(name);
        this.minecraft.setScreen(new DialogueEditorScreen(name, d != null ? d.copy() : Dialogue.blank(name), target));
    }

    private void confirmDelete(int idx) {
        String name = names.get(Mth.clamp(idx, 0, names.size() - 1));
        this.minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                Net.toServer(new Packets.DeleteDialogue(name));
            }
            this.minecraft.setScreen(this);
        }, Component.literal("Удалить диалог «" + name + "»?"), Component.literal("Это действие нельзя отменить.")));
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        if (newField != null && newField.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            openByName(newField.getValue());
            return true;
        }
        return super.keyPressed(key, scancode, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        setFocused(null);
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);
        Theme.frame(g, px, py, pw, ph, Theme.BORDER, Theme.PANEL | 0xFF000000);
        Theme.text(g, "Диалоги", px + 16, py + 14, Theme.TEXT);
        Theme.text(g, "Хранятся на сервере и сразу доступны всем игрокам", px + 16, py + 28, Theme.DIM);
        Theme.text(g, "СУЩЕСТВУЮЩИЕ (" + names.size() + ")", px + 16, py + 76, Theme.FAINT);
        if (names.isEmpty()) {
            Theme.text(g, "Пока пусто — создайте первый диалог выше.", px + 16, py + 100, Theme.DIM);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (Util.getMillis() < errorUntil) {
            Theme.text(g, error, px + 16, py + ph - 26, Theme.DANGER);
        }
    }
}
