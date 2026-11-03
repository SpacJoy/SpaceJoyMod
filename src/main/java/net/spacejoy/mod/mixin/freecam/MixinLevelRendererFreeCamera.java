package net.spacejoy.mod.mixin.freecam;

import net.minecraft.client.renderer.LevelRenderer;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.feature.freecam.SpaceJoyFreeCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由相机：标出渲染的两个阶段，只在「主视角渲染」时把玩家伪装成旁观者。
 *
 * <p>整帧都伪装的话，阴影和别的实体也会跳过玩家，画面会缺东西。原版在
 * {@code repositionCamera} 之前准备，{@code compileSections} 之后开始区块构建，
 * 用这两个 profiling 字符串当锚点最稳。
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererFreeCamera {
    // Sodium / Iris 会重写 render 的流水线。注入点找不到时宁可不生效，也不能让游戏崩。
    @Inject(method = "render",
            require = 0,
            at = @At(value = "INVOKE_STRING",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V",
                    args = "ldc=repositionCamera"))
    private void spacejoy$beginSpectatorPhase(CallbackInfo ci) {
        if (SpaceJoyConfig.isFreeCamera()) {
            SpaceJoyFreeCamera.setSpectatorPhase(true);
        }
    }

    @Inject(method = "render",
            require = 0,
            at = @At(value = "INVOKE_STRING",
                    target = "Lnet/minecraft/util/profiling/ProfilerFiller;push(Ljava/lang/String;)V",
                    args = "ldc=compileSections"))
    private void spacejoy$endSpectatorPhase(CallbackInfo ci) {
        if (SpaceJoyConfig.isFreeCamera()) {
            SpaceJoyFreeCamera.setSpectatorPhase(false);
        }
    }
}
