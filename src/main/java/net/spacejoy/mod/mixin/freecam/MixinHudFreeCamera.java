package net.spacejoy.mod.mixin.freecam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自由相机：HUD 显示哪些元素跟着相机走。
 *
 * <p>相机默认不显示手、快捷栏、状态栏，这些在飞行看景时都是干扰。三个开关各自独立，
 * 想留哪个都行。快捷栏藏了但手留着时，手持物名字还要显示，所以两个条件分开判。
 */
@Mixin(Hud.class)
public abstract class MixinHudFreeCamera {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getCameraPlayer", at = @At("HEAD"), cancellable = true)
    private void spacejoy$usePlayerAsCameraPlayer(CallbackInfoReturnable<Player> cir) {
        if (SpaceJoyConfig.isFreeCamera() && this.minecraft.player != null) {
            cir.setReturnValue(this.minecraft.player);
        }
    }

    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void spacejoy$hideHotbar(CallbackInfo ci) {
        if (SpaceJoyConfig.isFreeCamera() && !SpaceJoyConfig.isFreeCameraShowHotbar()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void spacejoy$hideSelectedItemName(CallbackInfo ci) {
        if (SpaceJoyConfig.isFreeCamera()
                && !SpaceJoyConfig.isFreeCameraShowHotbar()
                && !SpaceJoyConfig.isFreeCameraShowHands()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void spacejoy$hideHealth(CallbackInfo ci) {
        if (SpaceJoyConfig.isFreeCamera() && !SpaceJoyConfig.isFreeCameraShowStatusBars()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void spacejoy$hideVehicleHealth(CallbackInfo ci) {
        if (SpaceJoyConfig.isFreeCamera() && !SpaceJoyConfig.isFreeCameraShowStatusBars()) {
            ci.cancel();
        }
    }

    @Inject(method = "nextContextualInfoState", at = @At("HEAD"), cancellable = true)
    private void spacejoy$hideContextualInfo(CallbackInfoReturnable<Hud.ContextualInfo> cir) {
        if (SpaceJoyConfig.isFreeCamera() && !SpaceJoyConfig.isFreeCameraShowStatusBars()) {
            cir.setReturnValue(Hud.ContextualInfo.EMPTY);
        }
    }
}
