package com.lastreality.dialogue.network;

import com.lastreality.dialogue.LastRealityMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Net {
    private Net() {
    }

    private static final String PROTOCOL = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(LastRealityMod.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private static int nextId = 0;

    public static void init() {
        s2c(Packets.SyncDialogue.class, Packets.SyncDialogue::encode, Packets.SyncDialogue::decode, Packets.SyncDialogue::handle);
        s2c(Packets.RemoveDialogue.class, Packets.RemoveDialogue::encode, Packets.RemoveDialogue::decode, Packets.RemoveDialogue::handle);
        s2c(Packets.StartDialogue.class, Packets.StartDialogue::encode, Packets.StartDialogue::decode, Packets.StartDialogue::handle);
        s2c(Packets.ShowNode.class, Packets.ShowNode::encode, Packets.ShowNode::decode, Packets.ShowNode::handle);
        s2c(Packets.OpenEditor.class, Packets.OpenEditor::encode, Packets.OpenEditor::decode, Packets.OpenEditor::handle);

        c2s(Packets.Advance.class, Packets.Advance::encode, Packets.Advance::decode, Packets.Advance::handle);
        c2s(Packets.UploadChunk.class, Packets.UploadChunk::encode, Packets.UploadChunk::decode, Packets.UploadChunk::handle);
        c2s(Packets.DeleteDialogue.class, Packets.DeleteDialogue::encode, Packets.DeleteDialogue::decode, Packets.DeleteDialogue::handle);
        c2s(Packets.BindEntity.class, Packets.BindEntity::encode, Packets.BindEntity::decode, Packets.BindEntity::handle);
    }

    private static <T> void s2c(Class<T> type, BiConsumer<T, FriendlyByteBuf> enc, Function<FriendlyByteBuf, T> dec,
                                BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(enc).decoder(dec).consumerMainThread(handler).add();
    }

    private static <T> void c2s(Class<T> type, BiConsumer<T, FriendlyByteBuf> enc, Function<FriendlyByteBuf, T> dec,
                                BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(enc).decoder(dec).consumerMainThread(handler).add();
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toAll(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }

    public static void toServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
