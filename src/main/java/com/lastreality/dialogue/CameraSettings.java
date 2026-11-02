package com.lastreality.dialogue;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

/**
 * Ракурс камеры относительно NPC. Один объект = один «кадр».
 * Лежит либо в диалоге (общий ракурс), либо в узле (свой ракурс, переопределяет общий).
 */
public class CameraSettings {
    /** Расстояние от глаз NPC до камеры (блоки, масштабируется по росту NPC). */
    public double distance = 1.3;
    /** Высота камеры относительно глаз NPC. */
    public double height = -0.08;
    /** Смещение точки прицеливания влево от NPC: больше = NPC правее в кадре. */
    public double sideShift = 0.55;
    /** Смещение точки прицеливания по высоте (отрицательное = лицо выше центра кадра). */
    public double aimY = -0.20;
    /** Угол камеры вокруг NPC относительно базового направления, градусы. */
    public double angle = 12;
    /** Множитель FOV (меньше = ближе). */
    public float fov = 0.72f;
    /** Плавность: чем больше, тем резче камера догоняет цель. */
    public double speed = 7.0;
    /** false: база угла = направление «NPC -> игрок». true: база = куда смотрит NPC (фиксированный кадр). */
    public boolean anchorNpc = false;
    /** Поворачивать NPC лицом к камере (только при anchorNpc = false). */
    public boolean faceNpc = true;

    public CameraSettings copy() {
        CameraSettings c = new CameraSettings();
        c.set(this);
        return c;
    }

    public void set(CameraSettings o) {
        distance = o.distance;
        height = o.height;
        sideShift = o.sideShift;
        aimY = o.aimY;
        angle = o.angle;
        fov = o.fov;
        speed = o.speed;
        anchorNpc = o.anchorNpc;
        faceNpc = o.faceNpc;
    }

    /** Защита от мусорных значений (в т.ч. из руками написанных json). */
    public void clamp() {
        distance = Mth.clamp(distance, 0.3, 16);
        height = Mth.clamp(height, -8, 8);
        sideShift = Mth.clamp(sideShift, -8, 8);
        aimY = Mth.clamp(aimY, -8, 8);
        angle = Mth.clamp(angle, -360, 360);
        fov = Mth.clamp(fov, 0.1f, 1.5f);
        speed = Mth.clamp(speed, 0.5, 30);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("distance", round(distance));
        o.addProperty("height", round(height));
        o.addProperty("side_shift", round(sideShift));
        o.addProperty("aim_y", round(aimY));
        o.addProperty("angle", round(angle));
        o.addProperty("fov", round(fov));
        o.addProperty("speed", round(speed));
        o.addProperty("anchor", anchorNpc ? "npc" : "player");
        o.addProperty("face_npc", faceNpc);
        return o;
    }

    public static CameraSettings fromJson(JsonObject o) {
        CameraSettings c = new CameraSettings();
        c.distance = GsonHelper.getAsDouble(o, "distance", c.distance);
        c.height = GsonHelper.getAsDouble(o, "height", c.height);
        c.sideShift = GsonHelper.getAsDouble(o, "side_shift", c.sideShift);
        c.aimY = GsonHelper.getAsDouble(o, "aim_y", c.aimY);
        c.angle = GsonHelper.getAsDouble(o, "angle", c.angle);
        c.fov = GsonHelper.getAsFloat(o, "fov", c.fov);
        c.speed = GsonHelper.getAsDouble(o, "speed", c.speed);
        c.anchorNpc = "npc".equals(GsonHelper.getAsString(o, "anchor", "player"));
        c.faceNpc = GsonHelper.getAsBoolean(o, "face_npc", c.faceNpc);
        c.clamp();
        return c;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
