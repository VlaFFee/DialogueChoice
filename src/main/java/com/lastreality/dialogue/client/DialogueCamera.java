package com.lastreality.dialogue.client;

import com.lastreality.dialogue.CameraSettings;
import com.lastreality.dialogue.LastRealityMod;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Камера диалога.
 *
 * Почему раньше «лагало» при движении моба: цель считалась по позиции ТИКА (20 раз/с), а кадров 60+.
 * Теперь:
 *  1) все позиции берутся интерполированными по partialTick (getEyePosition(pt), getPosition(pt));
 *  2) цель камеры дополнительно сглаживается экспоненциально (CameraSettings.speed) — камера «плывёт»,
 *     а не прыгает, в том числе при смене ракурса между узлами и при движении NPC;
 *  3) луч от NPC к камере (чтобы не уехать в стену) считается раз в тик, а не каждый кадр.
 */
@Mod.EventBusSubscriber(modid = LastRealityMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DialogueCamera {
    public static final DialogueCamera INSTANCE = new DialogueCamera();

    /** true только на время отрисовки портрета игрока в GUI (там модель игрока скрывать нельзя). */
    public static boolean portraitRender;

    public static long ENTER_MS = 900;
    public static long EXIT_MS = 700;

    private enum Phase {IDLE, ENTER, HOLD, EXIT}

    private record Pose(Vec3 pos, float yaw, float pitch) {
    }

    private Phase phase = Phase.IDLE;
    private CameraEntity cam;
    private Entity target;
    private CameraSettings settings = new CameraSettings();

    private long phaseStart;
    private long lastFrameMs;
    private Pose startPose;
    private Pose exitFrom;
    private Pose currentPose;
    private Pose smooth;
    private boolean smoothInit;

    private float goalFov = 1f;
    private float smoothFov = 1f;
    private float fovFactor = 1f;
    private float exitFovFactor = 1f;

    private CameraType prevCameraType;
    private float faceYaw;
    private float npcYaw;

    private long clipTick = Long.MIN_VALUE;
    private double clipFraction = 1.0;
    private double clipSmooth = 1.0;

    private DialogueCamera() {
    }

    // ------------------------------------------------------------------ API

    public boolean begin(Entity target, CameraSettings settings) {
        Minecraft mc = Minecraft.getInstance();
        if (phase != Phase.IDLE || mc.player == null || mc.level == null || target == null) {
            return false;
        }
        this.target = target;
        this.settings = settings;
        this.cam = new CameraEntity(mc.level);
        this.startPose = playerPose(mc);
        this.currentPose = startPose;
        this.smoothInit = false;
        this.fovFactor = 1f;
        this.clipTick = Long.MIN_VALUE;
        this.clipFraction = 1.0;
        this.clipSmooth = 1.0;
        this.lastFrameMs = 0;
        this.npcYaw = target instanceof LivingEntity le ? le.getYRot() : 0f;

        this.prevCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);

        apply(startPose);
        mc.setCameraEntity(cam);
        this.phase = Phase.ENTER;
        this.phaseStart = Util.getMillis();
        return true;
    }

    /** Меняет ракурс «на лету» — камера плавно доедет до нового кадра. */
    public void setSettings(CameraSettings settings) {
        this.settings = settings;
    }

    /** Плавно возвращает камеру игроку. Можно вызывать сколько угодно раз. */
    public void end() {
        if (phase == Phase.IDLE || phase == Phase.EXIT) {
            return;
        }
        this.exitFrom = currentPose;
        this.exitFovFactor = fovFactor;
        this.phase = Phase.EXIT;
        this.phaseStart = Util.getMillis();
    }

    public boolean isEngaged() {
        return phase != Phase.IDLE;
    }

    private boolean hidesHud() {
        return phase == Phase.ENTER || phase == Phase.HOLD;
    }

    // ------------------------------------------------------------------ кадр

    private void frame(float pt) {
        if (phase == Phase.IDLE) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }

        long now = Util.getMillis();
        float dt = lastFrameMs == 0 ? 0f : Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;

        if ((phase == Phase.ENTER || phase == Phase.HOLD) && (target == null || !target.isAlive())) {
            end();
        }

        Pose pose;
        if (phase == Phase.EXIT) {
            float t = UiUtil.clamp01((now - phaseStart) / (float) EXIT_MS);
            float e = UiUtil.easeInOutCubic(t);
            pose = lerp(exitFrom, playerPose(mc), e);
            fovFactor = Mth.lerp(e, exitFovFactor, 1f);
            if (t >= 1f) {
                finish();
                return;
            }
        } else {
            float k = 1f - (float) Math.exp(-dt * settings.speed);
            Pose goal = computeGoal(mc, pt, k);
            if (!smoothInit) {
                smooth = goal;
                smoothFov = goalFov;
                smoothInit = true;
            } else {
                smooth = lerp(smooth, goal, k);
                smoothFov = Mth.lerp(k, smoothFov, goalFov);
            }

            float t = phase == Phase.ENTER ? UiUtil.clamp01((now - phaseStart) / (float) ENTER_MS) : 1f;
            if (phase == Phase.ENTER && t >= 1f) {
                phase = Phase.HOLD;
            }
            float e = UiUtil.easeInOutCubic(t);
            pose = lerp(startPose, smooth, e);
            fovFactor = Mth.lerp(e, 1f, smoothFov);
            faceNpc(dt);
        }

        currentPose = pose;
        apply(pose);
    }

    /** Целевая поза камеры (по интерполированным позициям). */
    private Pose computeGoal(Minecraft mc, float pt, float k) {
        CameraSettings cs = this.settings;
        Vec3 eye = target.getEyePosition(pt);
        Vec3 tpos = target.getPosition(pt);

        double base;
        if (cs.anchorNpc) {
            base = Math.atan2(npcForward(target, pt).z, npcForward(target, pt).x);
        } else {
            Vec3 pp = mc.player.getPosition(pt);
            double dx = pp.x - tpos.x;
            double dz = pp.z - tpos.z;
            if (dx * dx + dz * dz < 1.0E-4) {
                Vec3 f = npcForward(target, pt);
                base = Math.atan2(f.z, f.x);
            } else {
                base = Math.atan2(dz, dx);
            }
        }

        double ang = base + Math.toRadians(cs.angle);
        double dist = cs.distance * Math.max(1.0, target.getBbHeight() / 1.8);
        Vec3 desired = eye.add(Math.cos(ang) * dist, cs.height, Math.sin(ang) * dist);

        // Луч «NPC -> камера» раз в тик; дальше плавно подтягиваем долю расстояния
        long gt = mc.level.getGameTime();
        if (gt != clipTick) {
            clipTick = gt;
            BlockHitResult hit = mc.level.clip(new ClipContext(eye, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, target));
            if (hit.getType() == HitResult.Type.MISS) {
                clipFraction = 1.0;
            } else {
                double total = eye.distanceTo(desired);
                clipFraction = total < 1.0E-4 ? 1.0 : Math.max(0.12, (eye.distanceTo(hit.getLocation()) - 0.3) / total);
            }
        }
        clipSmooth += (clipFraction - clipSmooth) * k;
        Vec3 camPos = eye.add(desired.subtract(eye).scale(clipSmooth));

        // Куда смотрит камера: левее NPC (с точки зрения камеры) => NPC справа в кадре
        Vec3 f = new Vec3(eye.x - camPos.x, 0, eye.z - camPos.z);
        f = f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 left = new Vec3(f.z, 0, -f.x);
        Vec3 aim = eye.add(left.scale(cs.sideShift)).add(0, cs.aimY, 0);

        double dx = aim.x - camPos.x;
        double dy = aim.y - camPos.y;
        double dz = aim.z - camPos.z;
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90f;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI));

        this.faceYaw = (float) (Mth.atan2(camPos.z - tpos.z, camPos.x - tpos.x) * (180.0 / Math.PI)) - 90f;
        this.goalFov = cs.fov;
        return new Pose(camPos, yaw, pitch);
    }

    private static Vec3 npcForward(Entity e, float pt) {
        float yaw = e instanceof LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : e.getViewYRot(pt);
        return Vec3.directionFromRotation(0, yaw);
    }

    private void faceNpc(float dt) {
        if (settings.anchorNpc || !settings.faceNpc || !(target instanceof LivingEntity le)) {
            return;
        }
        npcYaw = Mth.rotLerp(1f - (float) Math.exp(-dt * 7f), npcYaw, faceYaw);
        le.setYRot(npcYaw);
        le.yRotO = npcYaw;
        le.yBodyRot = npcYaw;
        le.yBodyRotO = npcYaw;
        le.setYHeadRot(npcYaw);
        le.yHeadRotO = npcYaw;
    }

    // ------------------------------------------------------------------ съёмка ракурса из текущего вида

    /**
     * Превращает текущий вид игрока (позиция + взгляд) в ракурс относительно NPC.
     * Привязка = NPC (кадр фиксирован относительно того, куда смотрит NPC). Значения fov/speed берутся из current.
     */
    public static CameraSettings captureFromView(Minecraft mc, Entity target, CameraSettings current) {
        float pt = 1f;
        Vec3 eye = target.getEyePosition(pt);
        Vec3 cam = mc.player.getEyePosition(pt);
        Vec3 look = mc.player.getViewVector(pt);

        CameraSettings cs = current.copy();
        cs.anchorNpc = true;
        cs.faceNpc = false;

        double scale = Math.max(1.0, target.getBbHeight() / 1.8);
        double dx = cam.x - eye.x;
        double dz = cam.z - eye.z;
        cs.distance = Math.sqrt(dx * dx + dz * dz) / scale;
        cs.height = cam.y - eye.y;

        Vec3 fwd = npcForward(target, pt);
        cs.angle = Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx) - Math.atan2(fwd.z, fwd.x)));

        // точка, в которую смотрит игрок, на «глубине» NPC
        Vec3 aim = cam.add(look.scale(cam.distanceTo(eye)));
        Vec3 f = new Vec3(eye.x - cam.x, 0, eye.z - cam.z);
        f = f.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : f.normalize();
        Vec3 left = new Vec3(f.z, 0, -f.x);
        Vec3 d = aim.subtract(eye);
        cs.sideShift = d.dot(left);
        cs.aimY = d.y;
        cs.clamp();
        return cs;
    }

    // ------------------------------------------------------------------ вспомогательное

    private static Pose playerPose(Minecraft mc) {
        return new Pose(mc.player.getEyePosition(1f), mc.player.getYRot(), mc.player.getXRot());
    }

    private static Pose lerp(Pose a, Pose b, float t) {
        return new Pose(a.pos.lerp(b.pos, t), Mth.rotLerp(t, a.yaw, b.yaw), Mth.lerp(t, a.pitch, b.pitch));
    }

    private void apply(Pose p) {
        double y = p.pos.y - CameraEntity.EYE_HEIGHT;
        cam.setPos(p.pos.x, y, p.pos.z);
        // old = new, чтобы vanilla-интерполяция Camera не вносила своё «подёргивание»
        cam.xo = cam.xOld = p.pos.x;
        cam.yo = cam.yOld = y;
        cam.zo = cam.zOld = p.pos.z;
        cam.setYRot(p.yaw);
        cam.setXRot(p.pitch);
        cam.yRotO = p.yaw;
        cam.xRotO = p.pitch;
    }

    private void finish() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        if (prevCameraType != null) {
            mc.options.setCameraType(prevCameraType);
        }
        reset();
    }

    private void reset() {
        phase = Phase.IDLE;
        cam = null;
        target = null;
        fovFactor = 1f;
    }

    // ------------------------------------------------------------------ события

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) {
            INSTANCE.frame(e.renderTickTime);
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (INSTANCE.isEngaged()) {
            e.setFOV(e.getFOV() * INSTANCE.fovFactor);
        }
    }

    @SubscribeEvent
    public static void onHand(RenderHandEvent e) {
        if (INSTANCE.isEngaged()) {
            e.setCanceled(true);
        }
    }

    /**
     * Баг «секунду ничего не видно»: пока камера выезжает из головы игрока (или возвращается в неё),
     * она находится ВНУТРИ модели собственного скина — видны только внутренние грани. Скрываем модель игрока,
     * пока камера «снята» с него (портрет в GUI рисуется отдельно и разрешён флагом portraitRender).
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre e) {
        if (INSTANCE.isEngaged() && !portraitRender && e.getEntity() == Minecraft.getInstance().player) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre e) {
        if (INSTANCE.hidesHud()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        INSTANCE.reset();
        ClientDialogues.clear();
    }
}
