package com.lastreality.dialogue.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lastreality.dialogue.Dialogue;
import com.lastreality.dialogue.LastRealityMod;
import com.lastreality.dialogue.api.DialogueChoiceEvent;
import com.lastreality.dialogue.network.Net;
import com.lastreality.dialogue.network.Packets;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Серверная часть. Сервер — источник истины:
 *  - хранит диалоги в &lt;мир&gt;/lastreality_dialogues/*.json и рассылает их клиентам;
 *  - ведёт «сессию» каждого игрока (какой диалог, какой узел), проверяет каждый выбор;
 *  - выполняет команды из диалога от имени сервера (уровень 2) — клиент команды не присылает, только индекс ответа;
 *  - редактировать диалоги могут только игроки с OP (уровень 2+) в креативе.
 */
@Mod.EventBusSubscriber(modid = LastRealityMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogueServer {
    private DialogueServer() {
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String TAG_PREFIX = "dialogue.";
    private static final double MAX_NPC_DISTANCE_SQ = 48.0 * 48.0;

    private static final Map<String, Dialogue> DIALOGUES = new LinkedHashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static MinecraftServer server;
    private static Path dir;

    private static final class Session {
        final String dialogue;
        final UUID npc;
        String node;
        long lastAdvanceTick;

        Session(String dialogue, UUID npc, String node) {
            this.dialogue = dialogue;
            this.npc = npc;
            this.node = node;
        }
    }

    private static final class Upload {
        final String name;
        final int total;
        final StringBuilder sb = new StringBuilder();
        int next;

        Upload(String name, int total) {
            this.name = name;
            this.total = total;
        }
    }

    // =========================================================== права

    public static boolean canEdit(ServerPlayer p) {
        return p.hasPermissions(2) && p.isCreative();
    }

    // =========================================================== жизненный цикл / хранение

    @SubscribeEvent
    public static void onStarting(ServerStartingEvent e) {
        server = e.getServer();
        dir = server.getWorldPath(LevelResource.ROOT).resolve("lastreality_dialogues");
        loadAll();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        DIALOGUES.clear();
        SESSIONS.clear();
        UPLOADS.clear();
        server = null;
    }

    private static void loadAll() {
        DIALOGUES.clear();
        try {
            Files.createDirectories(dir);
            boolean empty;
            try (Stream<Path> s = Files.list(dir)) {
                empty = s.noneMatch(p -> p.getFileName().toString().endsWith(".json"));
            }
            if (empty) {
                copyDefaults();
            }
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(p -> {
                    String fn = p.getFileName().toString();
                    String name = fn.substring(0, fn.length() - 5);
                    if (!Dialogue.validName(name)) {
                        LOGGER.warn("Пропускаю файл с недопустимым именем диалога: {}", fn);
                        return;
                    }
                    try {
                        JsonObject o = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                        DIALOGUES.put(name, Dialogue.fromJson(name, o));
                    } catch (Exception ex) {
                        LOGGER.error("Не удалось прочитать диалог {}", fn, ex);
                    }
                });
            }
        } catch (IOException ex) {
            LOGGER.error("Ошибка загрузки диалогов", ex);
        }
        LOGGER.info("Загружено диалогов: {}", DIALOGUES.size());
    }

    private static void copyDefaults() throws IOException {
        try (InputStream in = LastRealityMod.class.getResourceAsStream("/default_dialogues/jenna_intro.json")) {
            if (in != null) {
                Files.write(dir.resolve("jenna_intro.json"), in.readAllBytes());
            }
        }
    }

    private static void saveToDisk(Dialogue d) {
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(d.name + ".json"), PRETTY.toJson(d.toJson()), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOGGER.error("Не удалось сохранить диалог {}", d.name, ex);
        }
    }

    // =========================================================== события

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            for (Dialogue d : DIALOGUES.values()) {
                Net.toPlayer(p, new Packets.SyncDialogue(d.name, d.toJson().toString()));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SESSIONS.remove(e.getEntity().getUUID());
        UPLOADS.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            end(p, true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            end(p, true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || server == null || SESSIONS.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (s == null) {
                continue;
            }
            if (p == null) {
                SESSIONS.remove(id);
                continue;
            }
            Entity npc = p.serverLevel().getEntity(s.npc);
            if (npc == null || !npc.isAlive() || npc.distanceToSqr(p) > MAX_NPC_DISTANCE_SQ) {
                end(p, true);
            }
        }
    }

    /** ПКМ по сущности с тегом dialogue.&lt;имя&gt; запускает диалог. Присядьте (Shift) в креативе, чтобы взаимодействовать как обычно. */
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (e.getLevel().isClientSide || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        if (p.isShiftKeyDown() && canEdit(p)) {
            return;
        }
        Dialogue d = boundDialogue(e.getTarget());
        if (d != null && start(p, d, e.getTarget())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @Nullable
    private static Dialogue boundDialogue(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                Dialogue d = DIALOGUES.get(tag.substring(TAG_PREFIX.length()));
                if (d != null) {
                    return d;
                }
            }
        }
        return null;
    }

    // =========================================================== сессии

    public static boolean start(ServerPlayer p, Dialogue d, Entity npc) {
        if (npc == p || !d.nodes.containsKey(d.start)) {
            return false;
        }
        end(p, false);
        Session s = new Session(d.name, npc.getUUID(), d.start);
        SESSIONS.put(p.getUUID(), s);
        runCommands(p, s, d.nodes.get(d.start).commands);
        Net.toPlayer(p, new Packets.StartDialogue(d.name, npc.getId(), d.start));
        return true;
    }

    public static void end(ServerPlayer p, boolean notifyClient) {
        if (SESSIONS.remove(p.getUUID()) != null && notifyClient) {
            Net.toPlayer(p, new Packets.ShowNode(""));
        }
    }

    public static void onAdvance(ServerPlayer p, int choice) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) {
            return;
        }
        if (choice == Packets.ABORT) {
            end(p, false);
            return;
        }
        long now = p.serverLevel().getGameTime();
        if (now - s.lastAdvanceTick < 3) { // анти-спам
            return;
        }
        Dialogue d = DIALOGUES.get(s.dialogue);
        Dialogue.Node n = d == null ? null : d.nodes.get(s.node);
        if (n == null) {
            end(p, true);
            return;
        }

        String next;
        if (!n.choices.isEmpty()) {
            if (choice < 0 || choice >= n.choices.size()) {
                return;
            }
            Dialogue.Choice c = n.choices.get(choice);
            MinecraftForge.EVENT_BUS.post(new DialogueChoiceEvent(p, s.dialogue, s.node, choice));
            runCommands(p, s, c.commands);
            next = c.next;
        } else {
            if (choice != Packets.CONTINUE) {
                return;
            }
            next = n.next;
        }
        s.lastAdvanceTick = now;

        Dialogue.Node nextNode = next == null ? null : d.nodes.get(next);
        if (nextNode == null) {
            end(p, true);
            return;
        }
        s.node = next;
        runCommands(p, s, nextNode.commands);
        // команды могли завершить диалог (например /dialogue stop)
        if (SESSIONS.get(p.getUUID()) == s) {
            Net.toPlayer(p, new Packets.ShowNode(next));
        }
    }

    /** Подстановки в командах: %player% — ник игрока, %npc% — UUID собеседника. */
    private static void runCommands(ServerPlayer p, Session s, List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }
        CommandSourceStack src = p.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        for (String raw : commands) {
            String cmd = raw.replace("%player%", p.getGameProfile().getName()).replace("%npc%", s.npc.toString());
            try {
                server.getCommands().performPrefixedCommand(src, cmd);
            } catch (Exception ex) {
                LOGGER.warn("Ошибка команды диалога '{}': {}", cmd, ex.toString());
            }
        }
    }

    // =========================================================== C2S: редактор

    public static void onUploadChunk(ServerPlayer p, String name, int index, int total, String chunk) {
        if (!canEdit(p)) {
            deny(p);
            return;
        }
        if (!Dialogue.validName(name) || total < 1 || total > 64 || index < 0 || index >= total) {
            UPLOADS.remove(p.getUUID());
            return;
        }
        Upload u = index == 0 ? new Upload(name, total) : UPLOADS.get(p.getUUID());
        if (index == 0) {
            UPLOADS.put(p.getUUID(), u);
        }
        if (u == null || !u.name.equals(name) || u.total != total || u.next != index) {
            UPLOADS.remove(p.getUUID());
            return;
        }
        u.sb.append(chunk);
        u.next++;
        if (u.sb.length() > Packets.MAX_JSON) {
            UPLOADS.remove(p.getUUID());
            p.sendSystemMessage(Component.literal("Диалог слишком большой."));
            return;
        }
        if (u.next == total) {
            UPLOADS.remove(p.getUUID());
            finishUpload(p, name, u.sb.toString());
        }
    }

    private static void finishUpload(ServerPlayer p, String name, String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            Dialogue d = Dialogue.fromJson(name, o);
            DIALOGUES.put(name, d);
            saveToDisk(d);
            Net.toAll(new Packets.SyncDialogue(name, d.toJson().toString()));
            p.sendSystemMessage(Component.literal("Диалог «" + name + "» сохранён."));
            LOGGER.info("{} сохранил диалог {}", p.getGameProfile().getName(), name);
        } catch (Exception ex) {
            p.sendSystemMessage(Component.literal("Не удалось сохранить диалог: " + ex.getMessage()));
        }
    }

    public static void onDelete(ServerPlayer p, String name) {
        if (!canEdit(p)) {
            deny(p);
            return;
        }
        if (!Dialogue.validName(name) || DIALOGUES.remove(name) == null) {
            return;
        }
        try {
            Files.deleteIfExists(dir.resolve(name + ".json"));
        } catch (IOException ex) {
            LOGGER.error("Не удалось удалить файл диалога {}", name, ex);
        }
        Net.toAll(new Packets.RemoveDialogue(name));
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            if (SESSIONS.get(id).dialogue.equals(name)) {
                ServerPlayer sp = server.getPlayerList().getPlayer(id);
                if (sp != null) {
                    end(sp, true);
                } else {
                    SESSIONS.remove(id);
                }
            }
        }
        p.sendSystemMessage(Component.literal("Диалог «" + name + "» удалён."));
    }

    public static void onBind(ServerPlayer p, int entityId, String name) {
        if (!canEdit(p)) {
            deny(p);
            return;
        }
        Entity target = p.serverLevel().getEntity(entityId);
        if (target == null || target == p || target.distanceToSqr(p) > 32 * 32) {
            return;
        }
        if (!name.isEmpty() && (!Dialogue.validName(name) || !DIALOGUES.containsKey(name))) {
            p.sendSystemMessage(Component.literal("Сначала сохраните диалог."));
            return;
        }
        for (String tag : List.copyOf(target.getTags())) {
            if (tag.startsWith(TAG_PREFIX)) {
                target.removeTag(tag);
            }
        }
        if (name.isEmpty()) {
            p.sendSystemMessage(Component.literal("Диалог отвязан от сущности."));
        } else {
            target.addTag(TAG_PREFIX + name);
            p.sendSystemMessage(Component.literal("Диалог «" + name + "» привязан (ПКМ по сущности запускает его)."));
        }
    }

    private static void deny(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("Редактировать диалоги можно только с правами оператора и в креативе."));
    }

    // =========================================================== команды

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("dialogue")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("start")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("dialogue", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(DIALOGUES.keySet(), b))
                                        .executes(c -> cmdStart(c, null))
                                        .then(Commands.argument("npc", EntityArgument.entity())
                                                .executes(c -> cmdStart(c, EntityArgument.getEntity(c, "npc")))))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> {
                                    int n = 0;
                                    for (ServerPlayer p : EntityArgument.getPlayers(c, "targets")) {
                                        if (SESSIONS.containsKey(p.getUUID())) {
                                            end(p, true);
                                            n++;
                                        }
                                    }
                                    return n;
                                })))
                .then(Commands.literal("reload")
                        .executes(c -> {
                            loadAll();
                            for (Dialogue d : DIALOGUES.values()) {
                                Net.toAll(new Packets.SyncDialogue(d.name, d.toJson().toString()));
                            }
                            c.getSource().sendSuccess(() -> Component.literal("Диалогов загружено: " + DIALOGUES.size()), true);
                            return DIALOGUES.size();
                        }))
                .then(Commands.literal("edit")
                        .executes(c -> cmdEdit(c, ""))
                        .then(Commands.argument("dialogue", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(DIALOGUES.keySet(), b))
                                .executes(c -> cmdEdit(c, StringArgumentType.getString(c, "dialogue"))))));
    }

    private static int cmdStart(CommandContext<CommandSourceStack> ctx, @Nullable Entity npc) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "dialogue");
        Dialogue d = DIALOGUES.get(name);
        if (d == null) {
            ctx.getSource().sendFailure(Component.literal("Диалог не найден: " + name));
            return 0;
        }
        int ok = 0;
        for (ServerPlayer p : EntityArgument.getPlayers(ctx, "targets")) {
            Entity n = npc != null ? npc : nearestNpc(p);
            if (n != null && start(p, d, n)) {
                ok++;
            }
        }
        final int count = ok;
        ctx.getSource().sendSuccess(() -> Component.literal("Диалог запущен для игроков: " + count), false);
        return ok;
    }

    @Nullable
    private static Entity nearestNpc(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8.0),
                        e -> !(e instanceof Player) && e.isAlive())
                .stream().min(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(p))).orElse(null);
    }

    private static int cmdEdit(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!canEdit(p)) {
            deny(p);
            return 0;
        }
        Net.toPlayer(p, new Packets.OpenEditor(name));
        return 1;
    }

    public static Set<String> names() {
        return DIALOGUES.keySet();
    }
}
