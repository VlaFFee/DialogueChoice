package com.lastreality.dialogue.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Публикуется на MinecraftForge.EVENT_BUS (на СЕРВЕРЕ), когда игрок выбрал вариант ответа.
 * Удобно для своих модов/аддонов (квесты, флаги и т.д.). Для карт достаточно полей "commands" в диалоге.
 */
public class DialogueChoiceEvent extends Event {
    private final ServerPlayer player;
    private final String dialogue;
    private final String nodeId;
    private final int choiceIndex;

    public DialogueChoiceEvent(ServerPlayer player, String dialogue, String nodeId, int choiceIndex) {
        this.player = player;
        this.dialogue = dialogue;
        this.nodeId = nodeId;
        this.choiceIndex = choiceIndex;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getDialogue() { return dialogue; }
    public String getNodeId() { return nodeId; }
    public int getChoiceIndex() { return choiceIndex; }
}
