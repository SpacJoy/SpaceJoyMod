package net.spacejoy.mod.mixin.freecam;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.feature.freecam.SpaceJoyFreeCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自由相机：屏蔽落到相机上的交互，顺便藏掉经验条。
 *
 * <p>相机开着而「仍可操作本体」关掉时，鼠标左右键不该产生任何网络动作。这里在发包之前
 * 就把攻击、实体交互、物品使用和挖掘全部挡掉：
 * <ul>
 *   <li>不挡 {@code attack}，左键会把 {@code ServerboundAttackPacket} 发给相机实体，
 *       服务端不认识这个实体，日志里就是「试图攻击无效实体」，严重的直接踢人。</li>
 *   <li>不挡 {@code useItem} / {@code interact}，右键会对着相机位置用物品、跟实体交互。</li>
 *   <li>不挡挖掘，左键会试图破坏相机所在位置的方块。</li>
 * </ul>
 *
 * <p>状态栏整体隐藏时，经验条也得跟着藏，否则底部会孤零零留一条绿色。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameModeFreeCamera {

    /**
     * 左键攻击：必须在 HEAD 拦，方法体第一句就是把攻击包发出去。
     *
     * <p>相机实体的 {@code getId()} 复用原玩家的 id，所以「准星落在相机上」和
     * 「攻击自己」在服务端看来都是同一个 id。原版 {@code ServerGamePacketListenerImpl.handleAttack}
     * 查不到这个 id 对应的实体，就按 {@code invalid_entity_attacked} 直接断线。
     *
     * <p>因此这里除了「本体不可操作」的常规拦截，还要单独挡掉「目标是玩家自己 / 相机实体 /
     * 同一个网络 id」的情况 —— 即使 {@code freeCameraPlayerInputs} 放开了本体操作也不放行。
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void spacejoy$blockAttack(Player player, Entity target, CallbackInfo ci) {
        if (SpaceJoyFreeCamera.shouldPreventPlayerInputs()
                || SpaceJoyFreeCamera.isSelfOrCameraTarget(target)) {
            ci.cancel();
        }
    }

    /** 右键与实体交互。 */
    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void spacejoy$blockInteract(Player player, Entity target, EntityHitResult hit,
                                        InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (SpaceJoyFreeCamera.shouldPreventPlayerInputs()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /** 右键用物品（含空手）。 */
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void spacejoy$blockUseItem(Player player, InteractionHand hand,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        if (SpaceJoyFreeCamera.shouldPreventPlayerInputs()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /** 右键对方块使用物品（放方块、开门、踩按钮……）。 */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void spacejoy$blockUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                         CallbackInfoReturnable<InteractionResult> cir) {
        if (SpaceJoyFreeCamera.shouldPreventPlayerInputs()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    /** 左键开始挖。返回值 true 表示「已经处理过」，原版就不会再走下一条路。 */
    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void spacejoy$blockStartDestroy(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (SpaceJoyFreeCamera.shouldPreventPlayerInputs()) {
            cir.setReturnValue(true);
        }
    }

    /** 左键持续挖。 */
    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void spacejoy$blockContinueDestroy(net.minecraft.core.BlockPos pos, net.minecraft.core.Direction direction,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (SpaceJoyFreeCamera.shouldPreventPlayerInputs()) {
            cir.setReturnValue(true);
        }
    }

    /** 隐藏经验条。 */
    @Inject(method = "hasExperience", at = @At("RETURN"), cancellable = true)
    private void spacejoy$hideExperienceBar(CallbackInfoReturnable<Boolean> cir) {
        if (SpaceJoyConfig.isFreeCamera() && !SpaceJoyConfig.isFreeCameraShowStatusBars()) {
            cir.setReturnValue(false);
        }
    }
}
