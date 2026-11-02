package com.lastreality.dialogue.client.editor;

import com.lastreality.dialogue.LastRealityMod;
import com.lastreality.dialogue.client.ClientModEvents;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Режим «снять ракурс с моего вида»: редактор закрывается, игрок летает (креатив) и выбирает кадр,
 * по нажатию клавиши (Enter) вид превращается в настройки камеры и редактор открывается снова.
 * Любой экран (Esc/чат) отменяет съёмку и возвращает в редактор без изменений.
 */
@Mod.EventBusSubscriber(modid = LastRealityMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CaptureController {
    private CaptureController() {
    }

    private static DialogueEditorScreen pending;

    public static boolean isActive() {
        return pending != null;
    }

    public static void begin(DialogueEditorScreen screen) {
        pending = screen;
        Minecraft.getInstance().setScreen(null);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || pending == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            pending = null;
            return;
        }
        if (ClientModEvents.CAPTURE_KEY.consumeClick()) {
            DialogueEditorScreen s = pending;
            pending = null;
            s.applyCapture();
            mc.setScreen(s);
        } else if (mc.screen != null) {
            DialogueEditorScreen s = pending;
            pending = null;
            mc.setScreen(s);
        }
    }
}
