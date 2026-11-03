package net.spacejoy.mod.mixin.freecam;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由相机：隐藏第一人称的手和手持物。
 *
 * <p>自由相机飞出去以后，画面里还挂着一双站在原地的手会很怪，所以默认不画。
 *
 * <p>注入方法把目标方法的三个参数照抄了一遍 —— Mixin 靠参数列表定位目标方法，
 * 只写 {@code CallbackInfo} 会匹配失败，配 {@code require = 0} 就是静默不生效。
 *
 * <p>{@code priority = 900}：和下面那个渲染器 mixin 一样，抢在其它模组（Punchy、TACZ、
 * tweakeroo 等）的同点注入之前 cancel，否则排到后面就等于没生效。
 */
@Mixin(value = GameRenderer.class, priority = 900)
public abstract class MixinGameRendererFreeCamera {
    // 光影类模组常会改写第一人称渲染流程，注入失败时跳过即可。
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true, require = 0)
    private void spacejoy$hideFirstPersonHands(CameraRenderState cameraState, PlayerRenderState playerState,
                                               GpuTextureView textureView, CallbackInfo ci) {
        if (SpaceJoyConfig.isFreeCamera() && !SpaceJoyConfig.isFreeCameraShowHands()) {
            ci.cancel();
        }
    }
}
