package net.spacejoy.mod.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.feature.EquipmentSwapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自动切换鞘翅（移植自 tweakeroo 的 tweakAutoSwitchElytra）。
 *
 * <p>两段逻辑：
 * <ol>
 *     <li>跳下去的一瞬间 —— 原版要开始算「能不能滑翔」的时候，如果胸甲不是鞘翅，
 *         就把它记下来，换上背包里最好的鞘翅；</li>
 *     <li>落地后 —— 自己记下的「上一 tick 还在滑翔、这一 tick 不滑了」成立时，把原来的胸甲换回去。</li>
 * </ol>
 *
 * <p><b>为什么不用 {@code LocalPlayer.wasFallFlying}</b>：那个字段由 {@code aiStep} 在每 tick 末尾写入，
 * 落地那一 tick 它已经被刷成 false，读它永远等不到「真变假」。所以这里在 aiStep 里自己留一份快照。
 *
 * <p>注意这里只处理胸甲，不涉及烟花火箭的自动切换 —— 那是 tweakeroo 里另一项功能。
 */
@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayerAutoSwitchElytra extends AbstractClientPlayer {
    /** 起飞前身上那件胸甲，落地后要还回去。 */
    @Unique
    private ItemStack spacejoy$cachedChestplate = ItemStack.EMPTY;

    /** 上一 tick 是否在滑翔，落地判定靠它。 */
    @Unique
    private boolean spacejoy$wasFallFlying;

    private MixinLocalPlayerAutoSwitchElytra(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Inject(method = "aiStep",
            at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
                    target = "Lnet/minecraft/client/player/LocalPlayer;tryToStartFallFlying()Z"))
    private void spacejoy$swapInElytra(CallbackInfo ci) {
        if (!SpaceJoyConfig.isAutoSwitchElytra()) {
            return;
        }

        LocalPlayer self = (LocalPlayer) (Object) this;

        // 只在「真的要从空中开始滑翔」的那一帧动手。
        if (self.onGround() || self.isPassenger() || this.fallFlyTicks != 0
                || self.isInLiquid() || self.onClimbable()
                || self.hasEffect(MobEffects.LEVITATION)) {
            return;
        }

        ItemStack chest = self.getItemBySlot(EquipmentSlot.CHEST);
        boolean needsSwap = !chest.is(Items.ELYTRA)
                || chest.getDamageValue() > chest.getMaxDamage() - 10;

        if (!needsSwap) {
            this.spacejoy$cachedChestplate = ItemStack.EMPTY;
            return;
        }

        this.spacejoy$cachedChestplate = chest.copy();
        EquipmentSwapper.equipBestElytra(self);
    }

    /**
     * 每 tick 末尾刷新滑翔快照，并在「上一 tick 在滑、这一 tick 不滑」时把胸甲换回来。
     *
     * <p>挂在这里而不是 {@code onSyncedDataUpdated}：共享标志位是服务端同步过来的，
     * 客户端本地停滑（落地、贴墙、进液体）不一定每次都伴随一次同步包，挂在同步回调上会漏。
     */
    @Inject(method = "aiStep", at = @At("RETURN"))
    private void spacejoy$swapBackChestplate(CallbackInfo ci) {
        LocalPlayer self = self();
        boolean flying = self.isFallFlying();

        if (this.spacejoy$wasFallFlying && !flying) {
            this.spacejoy$onFallFlyingStopped(self);
        }

        this.spacejoy$wasFallFlying = flying;
    }

    @Unique
    private void spacejoy$onFallFlyingStopped(LocalPlayer self) {
        if (!SpaceJoyConfig.isAutoSwitchElytra()) {
            return;
        }

        if (!self.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            return;
        }

        ItemStack cached = this.spacejoy$cachedChestplate;
        if (!cached.isEmpty() && !cached.is(Items.ELYTRA) && self.inventoryMenu.getCarried().isEmpty()) {
            EquipmentSwapper.swapElytraFromChest(self, cached);
            this.spacejoy$cachedChestplate = ItemStack.EMPTY;
        } else {
            EquipmentSwapper.swapElytraAndChestPlate(self);
        }
    }

    @Unique
    private LocalPlayer self() {
        return (LocalPlayer) (Object) this;
    }
}
