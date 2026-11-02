package com.lastreality.dialogue.network;

import com.lastreality.dialogue.client.ClientNet;
import com.lastreality.dialogue.server.DialogueServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Все пакеты мода.
 *  S2C: SyncDialogue, RemoveDialogue, StartDialogue, ShowNode, OpenEditor
 *  C2S: Advance, UploadChunk, DeleteDialogue, BindEntity
 *
 * Важно: серверный пакет custom payload ограничен ~32 КБ, поэтому json диалога при сохранении
 * режется на куски (UploadChunk). Клиентские (S2C) пакеты — до 1 МБ, диалоги шлются по одному.
 */
public final class Packets {
    private Packets() {
    }

    public static final int MAX_JSON = 200_000;
    public static final int CHUNK = 8_000;
    public static final int CONTINUE = -1;
    public static final int ABORT = -2;

    // =============================================================== S2C

    public record SyncDialogue(String name, String json) {
        public void encode(FriendlyByteBuf b) {
            b.writeUtf(name, 64);
            b.writeUtf(json, MAX_JSON);
        }

        public static SyncDialogue decode(FriendlyByteBuf b) {
            return new SyncDialogue(b.readUtf(64), b.readUtf(MAX_JSON));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNet.onSync(name, json));
        }
    }

    public record RemoveDialogue(String name) {
        public void encode(FriendlyByteBuf b) {
            b.writeUtf(name, 64);
        }

        public static RemoveDialogue decode(FriendlyByteBuf b) {
            return new RemoveDialogue(b.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNet.onRemove(name));
        }
    }

    public record StartDialogue(String name, int npcId, String nodeId) {
        public void encode(FriendlyByteBuf b) {
            b.writeUtf(name, 64);
            b.writeVarInt(npcId);
            b.writeUtf(nodeId, 64);
        }

        public static StartDialogue decode(FriendlyByteBuf b) {
            return new StartDialogue(b.readUtf(64), b.readVarInt(), b.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNet.onStart(name, npcId, nodeId));
        }
    }

    /** Сервер говорит, какой узел показать дальше. Пустая строка = диалог закончен. */
    public record ShowNode(String nodeId) {
        public void encode(FriendlyByteBuf b) {
            b.writeUtf(nodeId, 64);
        }

        public static ShowNode decode(FriendlyByteBuf b) {
            return new ShowNode(b.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNet.onNode(nodeId));
        }
    }

    /** Пустое имя = открыть список диалогов. */
    public record OpenEditor(String name) {
        public void encode(FriendlyByteBuf b) {
            b.writeUtf(name, 64);
        }

        public static OpenEditor decode(FriendlyByteBuf b) {
            return new OpenEditor(b.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientNet.onOpenEditor(name));
        }
    }

    // =============================================================== C2S

    /** choice >= 0: выбран ответ; CONTINUE: «дальше»; ABORT: игрок закрыл диалог. */
    public record Advance(int choice) {
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(choice + 2); // -2..N -> 0..N+2
        }

        public static Advance decode(FriendlyByteBuf b) {
            return new Advance(b.readVarInt() - 2);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                DialogueServer.onAdvance(p, choice);
            }
        }
    }

    public record UploadChunk(String name, int index, int total, String chunk) {
        public void encode(FriendlyByteBuf b) {
            b.writeUtf(name, 64);
            b.writeVarInt(index);
            b.writeVarInt(total);
            b.writeUtf(chunk, CHUNK);
        }

        public static UploadChunk decode(FriendlyByteBuf b) {
            return new UploadChunk(b.readUtf(64), b.readVarInt(), b.readVarInt(), b.readUtf(CHUNK));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                DialogueServer.onUploadChunk(p, name, index, total, chunk);
            }
        }
    }

    public record DeleteDialogue(String name) {
        public void encode(FriendlyByteBuf b) {
            b.writeUtf(name, 64);
        }

        public static DeleteDialogue decode(FriendlyByteBuf b) {
            return new DeleteDialogue(b.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                DialogueServer.onDelete(p, name);
            }
        }
    }

    /** Привязать диалог к сущности (тег dialogue.&lt;name&gt;). Пустое имя = отвязать. */
    public record BindEntity(int entityId, String name) {
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(entityId);
            b.writeUtf(name, 64);
        }

        public static BindEntity decode(FriendlyByteBuf b) {
            return new BindEntity(b.readVarInt(), b.readUtf(64));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                DialogueServer.onBind(p, entityId, name);
            }
        }
    }
}
