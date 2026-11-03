package net.spacejoy.mod.mixin.freecam;

import net.minecraft.client.player.LocalPlayer;
import net.spacejoy.mod.feature.freecam.SpaceJoyFreeCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由相机：鼠标转向转给相机。
 *
 * <p>原版 {@code Entity.turn} 会同时改实体朝向。自由相机时玩家不该跟着转头，
 * 所以把增量交给相机后整段跳过，本体的朝向保持不动。
 */
@Mixin(net.minecraft.world.entity.Entity.class)
public abstract class MixinEntityFreeCamera {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void spacejoy$redirectTurnToCamera(double yaw, double pitch, CallbackInfo ci) {
        // turn 是所有实体共用的，只有本地玩家在自由相机下才拦。
        if (!((Object) this instanceof LocalPlayer)) {
            return;
        }

        if (!SpaceJoyFreeCamera.shouldPreventPlayerMovement()) {
            return;
        }

        SpaceJoyFreeCamera.updateCameraRotations((float) yaw, (float) pitch);
        ci.cancel();
    }
}
