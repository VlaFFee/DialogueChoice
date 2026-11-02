package com.lastreality.dialogue.client;

import com.google.gson.JsonParser;
import com.lastreality.dialogue.Dialogue;
import com.lastreality.dialogue.client.editor.DialogueEditorScreen;
import com.lastreality.dialogue.client.editor.DialogueListScreen;
import com.lastreality.dialogue.network.Net;
import com.lastreality.dialogue.network.Packets;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

import java.util.Comparator;

/** Обработчики серверных пакетов (вызываются из Packets через DistExecutor). */
public final class ClientNet {
    private ClientNet() {
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void onSync(String name, String json) {
        try {
            ClientDialogues.put(Dialogue.fromJson(name, JsonParser.parseString(json).getAsJsonObject()));
        } catch (Exception ex) {
            LOGGER.error("Некорректный диалог от сервера: {}", name, ex);
        }
    }

    public static void onRemove(String name) {
        ClientDialogues.remove(name);
    }

    public static void onStart(String name, int npcId, String nodeId) {
        Minecraft mc = Minecraft.getInstance();
        Dialogue d = ClientDialogues.get(name);
        Entity npc = mc.level == null ? null : mc.level.getEntity(npcId);
        if (d == null || npc == null || !d.nodes.containsKey(nodeId)) {
            Net.toServer(new Packets.Advance(Packets.ABORT));
            return;
        }
        DialogueScreen.open(d, npc, nodeId);
    }

    public static void onNode(String nodeId) {
        if (Minecraft.getInstance().screen instanceof DialogueScreen s) {
            s.onServerNode(nodeId);
        }
    }

    public static void onOpenEditor(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Entity target = pickTarget(mc);
        if (name.isEmpty()) {
            mc.setScreen(new DialogueListScreen(target));
        } else {
            Dialogue d = ClientDialogues.get(name);
            mc.setScreen(new DialogueEditorScreen(name, d != null ? d.copy() : Dialogue.blank(name), target));
        }
    }

    /** Сущность под прицелом, иначе ближайшая живая в радиусе 8 блоков. */
    public static Entity pickTarget(Minecraft mc) {
        LocalPlayer p = mc.player;
        Entity looked = mc.crosshairPickEntity;
        if (looked != null && looked != p) {
            return looked;
        }
        return mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8.0), e -> e != p && e.isAlive())
                .stream()
                .min(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(p)))
                .orElse(null);
    }
}
