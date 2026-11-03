package net.spacejoy.mod.mixin.freecam;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由相机：隐藏第一人称手和手上的物品。
 *
 * <p>不去拦渲染器，而是把渲染状态里「该画哪只手」这个字段清空。
 * 26.x 的 {@code FirstPersonHandsAndItemsRenderer.submitHandsWithItems} 开头就是
 * {@code if (handRenderSelection == null) return;}，清空之后它直接返回，一只手都不提交。
 *
 * <p>为什么不用拦 {@code submitHandsWithItems} / {@code renderItemInHand}：
 * 这两个方法在装了 Iris、Sodium Extra、tweakeroo、tweakermore 的环境里会被别的模组
 * 反复重写，注入点容易匹配不上，而匹配不上又只能靠 {@code require = 0} 静默放过 ——
 * 表现出来就是「开关关了手还在」。清状态字段跟谁重写了渲染管线无关，永远生效。
 *
 * <p>{@code extractRenderState} 每帧都会重新算一次 {@code handRenderSelection}，
 * 所以这里清空不需要还原，下一帧自然重算。
 */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class MixinFirstPersonHandsAndItemsFreeCamera {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void spacejoy$hideHandsWhenFreeCamera(LocalPlayer player, float partialTicks,
                                                  FirstPersonHandsAndItemsRenderState state,
                                                  CallbackInfo ci) {
        if (SpaceJoyConfig.isFreeCamera() && !SpaceJoyConfig.isFreeCameraShowHands()) {
            state.handRenderSelection = null;
        }
    }
}
