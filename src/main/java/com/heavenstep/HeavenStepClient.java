package com.heavenstep;

import com.heavenstep.HeavenStep.StrikePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class HeavenStepClient implements ClientModInitializer {

    private static KeyMapping strikeKey;

    @Override
    public void onInitializeClient() {
        // 기본 키는 Q 입니다. 바닐라 "아이템 버리기"와 겹치므로,
        // 게임 내 옵션 -> 조작 설정 -> Heaven Step 카테고리에서 원하는 키로 바꿀 수 있습니다.
        strikeKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.heavenstep.strike",
                GLFW.GLFW_KEY_Q,
                "key.categories.heavenstep"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (strikeKey.consumeClick()) {
                if (client.player == null || client.level == null) {
                    continue;
                }

                HitResult hit = client.player.pick(HeavenStep.MAX_RANGE, 0.0F, false);
                Vec3 targetPos;

                if (hit.getType() == HitResult.Type.BLOCK) {
                    targetPos = hit.getLocation();
                } else {
                    // 조준 범위 안에 블록이 없으면, 시선 방향으로 20블록 지점에 낙뢰
                    targetPos = client.player.getEyePosition().add(client.player.getLookAngle().scale(20.0));
                }

                ClientPlayNetworking.send(new StrikePayload(targetPos.x, targetPos.y, targetPos.z));

                // 즉각적인 피드백을 위한 로컬 사운드
                client.player.playSound(SoundEvents.TRIDENT_THUNDER, 0.6F, 1.4F);
            }
        });
    }
}
