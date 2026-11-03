package net.spacejoy.mod.mixin.freecam;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由相机：拦掉第一人称手部模型的提交。
 *
 * <p>26.x 把手的渲染拆到这个类里了，{@code GameRenderer} 那个入口只负责准备状态，
 * 真正的模型在这里提交，所以两处都要拦。
 *
 * <p><b>注入方法必须把目标方法的参数照抄一遍</b>：Mixin 是按参数列表匹配目标方法的，
 * 只写一个 {@code CallbackInfo} 会匹配不到，而 {@code require = 0} 又让失败变成静默 ——
 * 结果就是开关关着、手照样画出来。
 *
 * <p><b>priority = 900 是必须的</b>：Punchy 之类的模组也在这个方法的 HEAD 上注入了自己的
 * 手臂渲染，而且完全不看 {@code handRenderSelection}。同一个注入点上，Mixin 按 priority
 * 从小到大依次排布回调，priority 小的先执行；小的那边 cancel 之后，后面那些回调会被
 * 跳过。默认的 1000 和它们并列，谁先谁后没有保证 —— 排到后面就等于我们不生效。
 */
@Mixin(value = FirstPersonHandsAndItemsRenderer.class, priority = 900)
public abstract class MixinFirstPersonHandsAndItemsRendererFreeCamera {
    // 这个类是 26.x 新拆出来的，其它渲染类模组也在动它；找不到就让原版继续画。
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true, require = 0)
    private void spacejoy$hideHands(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector,
                                    PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                    CallbackInfo ci) {
        if (SpaceJoyConfig.isFreeCamera() && !SpaceJoyConfig.isFreeCameraShowHands()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void spacejoy$hideArm(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState,
                                  float xRot, float attack, InteractionHand hand, float swingProgress,
                                  ItemStack stack, float inverseArmHeight, PoseStack poseStack,
                                  SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
        if (SpaceJoyConfig.isFreeCamera() && !SpaceJoyConfig.isFreeCameraShowHands()) {
            ci.cancel();
        }
    }
}
