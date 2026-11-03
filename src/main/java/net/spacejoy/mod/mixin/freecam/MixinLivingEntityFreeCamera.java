package net.spacejoy.mod.mixin.freecam;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.InteractionHand;
import net.spacejoy.mod.feature.freecam.SpaceJoyFreeCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自由相机：本体被冻结时不再播挥手动画。
 *
 * <p>本体不能操作的情况下还挥手，看起来像卡了。所以只要输入被拦了就直接吞掉这次 swing。
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityFreeCamera extends Entity {
    private MixinLivingEntityFreeCamera(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void spacejoy$skipSwingWhenFrozen(InteractionHand hand, SwingAnimation animation,
                                              boolean sendToSwingingEntity,
                                              CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LocalPlayer && SpaceJoyFreeCamera.shouldPreventPlayerInputs()) {
            // 返回 false 表示「没有真的挥」，动画和声音都不会播。
            cir.setReturnValue(false);
        }
    }
}
