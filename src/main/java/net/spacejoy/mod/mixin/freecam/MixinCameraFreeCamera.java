package net.spacejoy.mod.mixin.freecam;

import net.minecraft.client.Camera;
import net.minecraft.world.level.material.FogType;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自由相机：水下/岩浆雾不糊在镜头上。
 *
 * <p>相机实体本身没有液体状态判定，但原版是拿「相机实体所在的液体」决定雾的。
 * 自由相机飞行时不该突然满屏糊雾，所以这里直接报「没有液体」。
 */
@Mixin(Camera.class)
public abstract class MixinCameraFreeCamera {
    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void spacejoy$noFluidFog(CallbackInfoReturnable<FogType> cir) {
        if (SpaceJoyConfig.isFreeCamera()) {
            cir.setReturnValue(FogType.NONE);
        }
    }
}
