package com.heavenstep;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class HeavenStep implements ModInitializer {

    public static final String MOD_ID = "heavenstep";

    // ===== 밸런스 설정 (원하는 값으로 자유롭게 수정하세요) =====
    /** 맞았을 때 깎이는 하트 수 (1 하트 = 체력 2) */
    public static final float DAMAGE_HEARTS = 5.0f;
    /** 실제 Minecraft 데미지 수치 (하트를 데미지로 환산) */
    public static final float DAMAGE_AMOUNT = DAMAGE_HEARTS * 2.0f;
    /** 번개가 떨어진 지점 기준, 피해를 입힐 반경(블록) */
    public static final double STRIKE_RADIUS = 3.5;
    /** Q로 지정할 수 있는 최대 사거리(블록) */
    public static final double MAX_RANGE = 64.0;

    /** 클라이언트 -> 서버로 "이 좌표에 번개를 내려줘"를 전달하는 패킷 */
    public record StrikePayload(double x, double y, double z) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StrikePayload> TYPE =
                new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "strike"));

        public static final StreamCodec<RegistryFriendlyByteBuf, StrikePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, StrikePayload::x,
                ByteBufCodecs.DOUBLE, StrikePayload::y,
                ByteBufCodecs.DOUBLE, StrikePayload::z,
                StrikePayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(StrikePayload.TYPE, StrikePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(StrikePayload.TYPE, (payload, context) -> {
            ServerPlayer caster = context.player();
            context.server().execute(() -> strikeLightning(caster, new Vec3(payload.x(), payload.y(), payload.z())));
        });
    }

    private void strikeLightning(ServerPlayer caster, Vec3 pos) {
        ServerLevel level = caster.serverLevel();

        // 시전자 위치에서 너무 멀리 떨어진 좌표는 무시 (핵/치트 패킷 방지용 안전장치)
        if (caster.position().distanceToSqr(pos) > (MAX_RANGE + 8.0) * (MAX_RANGE + 8.0)) {
            return;
        }

        // 1) 바닐라 번개 엔티티 소환 -> 번개 시각효과 + 벼락 소리 + 발화 효과
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(pos.x, pos.y, pos.z);
            bolt.setVisualOnly(false);
            bolt.setCause(caster);
            level.addFreshEntity(bolt);
        }

        // 2) 낙뢰 지점 주변에 화려한 파티클 연출 추가
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 1.0, pos.z, 60, 1.2, 1.2, 1.2, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y + 0.3, pos.z, 30, 0.6, 0.8, 0.6, 0.05);
        level.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.1, pos.z, 20, 0.8, 0.2, 0.8, 0.02);

        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 1.5F, 1.0F);

        // 3) 반경 내 모든 생명체에게 피해 적용 (바닐라 번개는 직격한 엔티티에게만 피해를 주므로 별도 처리)
        AABB area = new AABB(
                pos.x - STRIKE_RADIUS, pos.y - STRIKE_RADIUS, pos.z - STRIKE_RADIUS,
                pos.x + STRIKE_RADIUS, pos.y + STRIKE_RADIUS, pos.z + STRIKE_RADIUS
        );
        List<LivingEntity> hitEntities = level.getEntitiesOfClass(LivingEntity.class, area);
        DamageSource damageSource = caster.damageSources().lightningBolt();

        for (LivingEntity target : hitEntities) {
            target.hurt(damageSource, DAMAGE_AMOUNT);
        }
    }
}
