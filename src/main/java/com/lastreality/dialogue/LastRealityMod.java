package com.lastreality.dialogue;

import com.lastreality.dialogue.network.Net;
import net.minecraftforge.fml.common.Mod;

/**
 * Мод нужен и на сервере, и на клиенте:
 *  - сервер хранит диалоги, ведёт сессии, проверяет права редакторов и выполняет команды;
 *  - клиент показывает камеру/GUI и редактор.
 * Клиентский код живёт в пакете .client и подключается через @Mod.EventBusSubscriber(value = Dist.CLIENT).
 */
@Mod(LastRealityMod.MOD_ID)
public class LastRealityMod {
    public static final String MOD_ID = "lastreality";

    public LastRealityMod() {
        Net.init();
    }
}
