package net.spacejoy.mod.mixin.freecam;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.feature.freecam.SpaceJoyFreeCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自由相机：让玩家本体在渲染阶段表现得像旁观者。
 *
 * <p>渲染器看到 {@code isSpectator()} 为真时不会画这个人本身，否则第一人称下会看到
 * 一个自己站在镜头位置。只在相机的「旁观者阶段」内生效，不影响实际游戏逻辑。
 */
@Mixin(Player.class)
public abstract class MixinPlayerFreeCamera extends LivingEntity {
    private MixinPlayerFreeCamera(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "isSpectator", at = @At("HEAD"), cancellable = true)
    private void spacejoy$hidePlayerWhenFreeCamera(CallbackInfoReturnable<Boolean> cir) {
        if (SpaceJoyConfig.isFreeCamera()
                && SpaceJoyFreeCamera.isSpectatorPhase()
                && (Player) (Object) this instanceof LocalPlayer) {
            cir.setReturnValue(true);
        }
    }
}
