package com.lastreality.dialogue.client;

import com.lastreality.dialogue.LastRealityMod;
import com.lastreality.dialogue.client.editor.CaptureController;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = LastRealityMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    /** Клавиша «снять кадр» в режиме съёмки ракурса (по умолчанию Enter). */
    public static final KeyMapping CAPTURE_KEY = new KeyMapping("key.lastreality.capture", InputConstants.KEY_RETURN, "key.categories.lastreality");

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(CAPTURE_KEY);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("capture_hint", (gui, g, partialTick, width, height) -> {
            if (!CaptureController.isActive()) {
                return;
            }
            String key = CAPTURE_KEY.getTranslatedKeyMessage().getString();
            g.drawCenteredString(Minecraft.getInstance().font, "Выберите ракурс и нажмите [" + key + "] — снять кадр. Esc — отмена.", width / 2, 24, 0xFFFFFF);
        });
    }
}
