package com.lastreality.dialogue.client;

import com.lastreality.dialogue.Dialogue;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/** Копия диалогов сервера на клиенте (приходит при входе и при каждом изменении). */
public final class ClientDialogues {
    private ClientDialogues() {
    }

    private static final Map<String, Dialogue> MAP = new TreeMap<>();
    /** Растёт при любом изменении — экраны используют, чтобы обновить список. */
    private static int version;

    @Nullable
    public static Dialogue get(String name) {
        return MAP.get(name);
    }

    public static void put(Dialogue d) {
        MAP.put(d.name, d);
        version++;
    }

    public static void remove(String name) {
        MAP.remove(name);
        version++;
    }

    public static void clear() {
        MAP.clear();
        version++;
    }

    public static Collection<String> names() {
        return MAP.keySet();
    }

    public static int version() {
        return version;
    }
}
