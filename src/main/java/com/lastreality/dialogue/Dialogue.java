package com.lastreality.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Диалог = граф узлов. Узел: реплика + (варианты ответа | next | конец), свой ракурс, команды при входе.
 * Одна и та же модель используется на сервере (хранение, проверка), на клиенте (показ) и в редакторе.
 */
public class Dialogue {
    public static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,40}");
    public static final Pattern NODE_ID = Pattern.compile("[A-Za-z0-9_]{1,32}");

    public static final int MAX_NODES = 200;
    public static final int MAX_CHOICES = 8;
    public static final int MAX_TEXT = 800;
    public static final int MAX_CMDS = 16;
    public static final int MAX_CMD_LEN = 300;

    public final String name;
    public String start = "start";
    public CameraSettings camera = new CameraSettings();
    public final Map<String, Node> nodes = new LinkedHashMap<>();

    public static class Node {
        public String speaker = "";
        public String text = "";
        /** null = конец диалога (используется, только если нет ответов) */
        public String next;
        public List<String> commands = new ArrayList<>();
        /** null = использовать общий ракурс диалога */
        public CameraSettings camera;
        public List<Choice> choices = new ArrayList<>();
    }

    public static class Choice {
        public String text = "";
        /** ResourceLocation текстуры иконки, пусто = без иконки */
        public String icon = "";
        public String next;
        public List<String> commands = new ArrayList<>();
    }

    public Dialogue(String name) {
        this.name = name;
    }

    public static boolean validName(String s) {
        return s != null && NAME.matcher(s).matches();
    }

    public static Dialogue blank(String name) {
        Dialogue d = new Dialogue(name);
        Node n = new Node();
        n.speaker = "NPC";
        n.text = "Привет!";
        d.nodes.put("start", n);
        return d;
    }

    public CameraSettings cameraFor(Node n) {
        return n != null && n.camera != null ? n.camera : camera;
    }

    public Dialogue copy() {
        return fromJson(name, toJson());
    }

    /** Чинит битые ссылки и пустой диалог. */
    public void sanitize() {
        if (nodes.isEmpty()) {
            nodes.put("start", new Node());
        }
        if (!nodes.containsKey(start)) {
            start = nodes.keySet().iterator().next();
        }
        for (Node n : nodes.values()) {
            if (n.next != null && !nodes.containsKey(n.next)) {
                n.next = null;
            }
            for (Choice c : n.choices) {
                if (c.next != null && !nodes.containsKey(c.next)) {
                    c.next = null;
                }
            }
        }
    }

    // ------------------------------------------------------------------ JSON

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("start", start);
        o.add("camera", camera.toJson());
        JsonObject nodesJson = new JsonObject();
        for (Map.Entry<String, Node> e : nodes.entrySet()) {
            Node n = e.getValue();
            JsonObject nj = new JsonObject();
            nj.addProperty("speaker", n.speaker);
            nj.addProperty("text", n.text);
            if (n.next != null) {
                nj.addProperty("next", n.next);
            }
            if (!n.commands.isEmpty()) {
                nj.add("commands", strings(n.commands));
            }
            if (n.camera != null) {
                nj.add("camera", n.camera.toJson());
            }
            if (!n.choices.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (Choice c : n.choices) {
                    JsonObject cj = new JsonObject();
                    cj.addProperty("text", c.text);
                    if (!c.icon.isEmpty()) {
                        cj.addProperty("icon", c.icon);
                    }
                    if (c.next != null) {
                        cj.addProperty("next", c.next);
                    }
                    if (!c.commands.isEmpty()) {
                        cj.add("commands", strings(c.commands));
                    }
                    arr.add(cj);
                }
                nj.add("choices", arr);
            }
            nodesJson.add(e.getKey(), nj);
        }
        o.add("nodes", nodesJson);
        return o;
    }

    public static Dialogue fromJson(String name, JsonObject o) {
        Dialogue d = new Dialogue(name);
        d.start = GsonHelper.getAsString(o, "start", "start");
        if (o.has("camera") && o.get("camera").isJsonObject()) {
            d.camera = CameraSettings.fromJson(o.getAsJsonObject("camera"));
        }
        JsonObject nodesJson = GsonHelper.getAsJsonObject(o, "nodes", new JsonObject());
        for (Map.Entry<String, JsonElement> e : nodesJson.entrySet()) {
            if (d.nodes.size() >= MAX_NODES || !NODE_ID.matcher(e.getKey()).matches() || !e.getValue().isJsonObject()) {
                continue;
            }
            JsonObject nj = e.getValue().getAsJsonObject();
            Node n = new Node();
            n.speaker = trunc(GsonHelper.getAsString(nj, "speaker", ""), 64);
            n.text = trunc(GsonHelper.getAsString(nj, "text", ""), MAX_TEXT);
            n.next = nullable(nj, "next");
            n.commands = readStrings(nj, "commands");
            if (nj.has("camera") && nj.get("camera").isJsonObject()) {
                n.camera = CameraSettings.fromJson(nj.getAsJsonObject("camera"));
            }
            if (nj.has("choices") && nj.get("choices").isJsonArray()) {
                for (JsonElement ce : nj.getAsJsonArray("choices")) {
                    if (n.choices.size() >= MAX_CHOICES || !ce.isJsonObject()) {
                        continue;
                    }
                    JsonObject cj = ce.getAsJsonObject();
                    Choice c = new Choice();
                    c.text = trunc(GsonHelper.getAsString(cj, "text", ""), 200);
                    c.icon = trunc(GsonHelper.getAsString(cj, "icon", ""), 200);
                    c.next = nullable(cj, "next");
                    c.commands = readStrings(cj, "commands");
                    n.choices.add(c);
                }
            }
            d.nodes.put(e.getKey(), n);
        }
        d.sanitize();
        return d;
    }

    // ------------------------------------------------------------------ helpers

    private static String nullable(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        String s = o.get(key).getAsString();
        return s.isEmpty() ? null : s;
    }

    private static JsonArray strings(List<String> list) {
        JsonArray a = new JsonArray();
        list.forEach(a::add);
        return a;
    }

    private static List<String> readStrings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) {
                if (out.size() >= MAX_CMDS) {
                    break;
                }
                String s = e.getAsString().trim();
                if (!s.isEmpty()) {
                    out.add(trunc(s, MAX_CMD_LEN));
                }
            }
        }
        return out;
    }

    private static String trunc(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
