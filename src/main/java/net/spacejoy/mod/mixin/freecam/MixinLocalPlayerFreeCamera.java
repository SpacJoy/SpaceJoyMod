package net.spacejoy.mod.mixin.freecam;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.authlib.GameProfile;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.feature.freecam.EmptyClientInput;
import net.spacejoy.mod.feature.freecam.SpaceJoyFreeCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自由相机：冻结本体移动，且不把「输入被改写」同步给服务端。
 *
 * <p>做法是把 {@code input} 临时换成空输入跑完这一 tick 的逻辑，tick 结束时再换回来。
 * 空输入的 {@code tick()} 是空实现，所以按键不会被翻译成移动向量，本体会停在原地。
 * 不这么做而直接改按键状态的话，服务端收到的输入会和客户端不一致，容易触发反作弊。
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerFreeCamera extends AbstractClientPlayer {
    @Shadow
    public ClientInput input;

    @Unique
    private final ClientInput spacejoy$emptyInput = new EmptyClientInput(null);

    @Unique
    private ClientInput spacejoy$realInput;

    private MixinLocalPlayerFreeCamera(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void spacejoy$freezeInputStart(CallbackInfo ci) {
        if (!SpaceJoyFreeCamera.shouldPreventPlayerMovement()) {
            return;
        }

        spacejoy$realInput = this.input;
        if (spacejoy$realInput != null) {
            // 正常 tick 一次，让原版把按键刷新到 keyPresses 上。
            spacejoy$realInput.tick();
        }
        this.input = spacejoy$emptyInput;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void spacejoy$freezeInputEnd(CallbackInfo ci) {
        if (spacejoy$realInput == null) {
            return;
        }

        if (this.input == spacejoy$emptyInput) {
            this.input = spacejoy$realInput;
        }
        spacejoy$realInput = null;
    }

    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void spacejoy$isCameraController(CallbackInfoReturnable<Boolean> cir) {
        if (SpaceJoyConfig.isFreeCamera() && SpaceJoyFreeCamera.originalCameraWasPlayer()) {
            // 相机是从玩家切出去的，玩家仍然是「控制者」，行为包/动画依赖这个判断。
            cir.setReturnValue(true);
        }
    }
}
