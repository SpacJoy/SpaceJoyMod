package net.spacejoy.mod.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 始终疾跑（移植自 tweakeroo 的 tweakPermanentSprint，另加「按键切换走路」）。
 *
 * <p>锚点选在 {@code aiStep} 里把 {@code wasFallFlying} 写回去的位置 —— 那之后原版
 * 已经算完了这一 tick 的疾跑状态，这里再补一次 {@code setSprinting(true)} 就不会被覆盖。
 *
 * <p>「按疾跑键切走路」是这样做的：按住前进自动疾跑，此时按一下疾跑键降成走路，
 * 再按一下切回疾跑；松开前进后复位，下次按住前进又是疾跑。
 * 疾跑键在这一分支里就是走/跑切换键，不再是「按住才跑」。
 *
 * <p>为了不出现「原版先 setSprinting(true) 再被我 setSprinting(false)」这种每 tick 抖两下的情况，
 * 走路期间额外让 {@code canStartSprinting()} 直接返回 false，原版这一 tick 就不会启动疾跑。
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerPermanentSprint {
    @Shadow
    protected int sprintTriggerTime;

    /** 这一次前进是否被玩家按键降成了走路。松开前进就清掉。 */
    @Unique
    private boolean spacejoy$walkMode;

    /** 上一 tick 疾跑键是不是按着。点击队列取不到时的兜底。 */
    @Unique
    private boolean spacejoy$sprintKeyDown;

    // 目标是 private 方法，@Shadow 不能直接桥接（Java 不允许 private abstract），
    // 用 @Invoker 生成一个包级可见的调用入口。
    @Invoker("isSprintingPossible")
    abstract boolean spacejoy$isSprintingPossible(boolean allowFlying);

    /**
     * 取走这一 tick 之前积攒的疾跑键点击。
     *
     * <p>不能用 {@code isDown()} 做边沿检测：{@code aiStep} 一 tick 才跑一次（50ms），
     * 手快的人点一下往往不到 50ms，按下和松开落在同一 tick 里，轮询时按键已经抬起来了，
     * 这一下就整个丢掉 —— 表现出来就是「要按两下才切一次」。
     * 原版对疾跑键只读 {@code isDown()}，从不消费点击队列，所以这里可以安全地整个取空。
     */
    @Unique
    private boolean spacejoy$consumeSprintKey() {
        KeyMapping sprintKey = Minecraft.getInstance().options.keySprint;
        boolean pressed = false;
        for (int i = 0; i < 8 && sprintKey.consumeClick(); i++) {
            pressed = true;
        }
        return pressed;
    }

    /**
     * 走路期间挡掉原版的「这一 tick 开始疾跑」。
     *
     * <p>原版 {@code aiStep} 里的顺序是：先 {@code canStartSprinting()} 决定要不要起步，
     * 再处理停止，最后才轮到下面的注入点。所以只在下游 {@code setSprinting(false)}
     * 会和原版每 tick 来回抢一次；在源头挡掉，状态就只翻一次。
     */
    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void spacejoy$blockSprintStart(CallbackInfoReturnable<Boolean> cir) {
        if (SpaceJoyConfig.isPermanentSprint() && this.spacejoy$walkMode) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "aiStep",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/player/LocalPlayer;wasFallFlying:Z",
                    opcode = Opcodes.PUTFIELD))
    private void spacejoy$forceSprint(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;

        // 先取点击队列：功能关着的时候也要取，否则点过的键会一直攒着，
        // 等下次打开时突然一次性生效。
        boolean clicked = this.spacejoy$consumeSprintKey();

        // 兜底：万一这一下点击被别处消费掉了（队列里没有），退回按 isDown() 抓边沿。
        // 两路都命中也只算一次，不会翻两下。
        KeyMapping sprintKey = Minecraft.getInstance().options.keySprint;
        boolean keyDown = sprintKey.isDown();
        boolean edge = keyDown && !this.spacejoy$sprintKeyDown;
        this.spacejoy$sprintKeyDown = keyDown;

        boolean pressed = clicked || edge;

        if (!SpaceJoyConfig.isPermanentSprint()) {
            this.spacejoy$walkMode = false;
            return;
        }

        if (!self.input.hasForwardImpulse()) {
            // 松手就把这一次前进的「走路」状态清掉，下一次按住前进还是疾跑。
            this.spacejoy$walkMode = false;
        } else if (pressed) {
            // 开着「始终疾跑」时，疾跑键变成一个走/跑切换键：
            // 按一下降成走路，再按一下切回疾跑。
            this.spacejoy$walkMode = !this.spacejoy$walkMode;
        }

        if (this.spacejoy$walkMode) {
            if (self.isSprinting()) {
                self.setSprinting(false);
            }
            return;
        }

        if (!this.spacejoy$canSprint(self) || self.isInWater() || this.spacejoy$shouldStopSprinting(self)) {
            return;
        }

        self.setSprinting(true);
        // 原版靠这个计数器在「刚松手」时给一段惯性，这里同步清掉，免得下一 tick 又被判成停。
        this.sprintTriggerTime = 0;
    }

    @Unique
    private boolean spacejoy$canSprint(LocalPlayer player) {
        if (player.isSprinting() || player.isUsingItem()) {
            return false;
        }
        if (!player.input.hasForwardImpulse()) {
            return false;
        }
        // 饿着肚子跑不动，除非能飞（创造/旁观）。
        if (player.getFoodData().getFoodLevel() <= 6.0F && !player.getAbilities().mayfly) {
            return false;
        }
        return !player.hasEffect(MobEffects.BLINDNESS);
    }

    @Unique
    private boolean spacejoy$shouldStopSprinting(LocalPlayer player) {
        if (!this.spacejoy$isSprintingPossible(player.getAbilities().flying)) {
            return true;
        }
        // 撞墙就停，否则贴墙跑会有很别扭的抖动。
        return player.horizontalCollision && !player.minorHorizontalCollision;
    }
}