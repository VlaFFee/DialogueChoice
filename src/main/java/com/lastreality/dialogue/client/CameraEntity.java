package com.lastreality.dialogue.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.network.NetworkHooks;

/**
 * Невидимая клиентская «камера-сущность». В мир не добавляется, существует только на клиенте.
 * Minecraft.setCameraEntity(...) — тот же механизм, что у режима наблюдателя.
 * Высота глаз = 1.62, как у стоящего игрока, чтобы Camera не «проседала» при смене сущности.
 */
public class CameraEntity extends Entity {
    public static final float EYE_HEIGHT = 1.62F;

    public CameraEntity(ClientLevel level) {
        super(EntityType.MARKER, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected float getEyeHeight(Pose pose, EntityDimensions dimensions) {
        return EYE_HEIGHT;
    }
}
