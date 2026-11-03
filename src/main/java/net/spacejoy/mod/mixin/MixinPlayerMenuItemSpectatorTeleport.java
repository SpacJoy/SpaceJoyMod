package net.spacejoy.mod.mixin;

import net.minecraft.client.gui.spectator.PlayerMenuItem;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 旁观者传送：允许点击玩家列表里的条目（移植自 tweakeroo 的 tweakSpectatorTeleport）。
 *
 * <p>原版在旁观者菜单里只允许点「非旁观者」的玩家，同一局里全是旁观者时就点不动。
 * 这个补丁解除该限制。
 */
@Mixin(value = PlayerMenuItem.class, priority = 1001)
public abstract class MixinPlayerMenuItemSpectatorTeleport {
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true)
    private void spacejoy$alwaysEnabled(CallbackInfoReturnable<Boolean> cir) {
        if (SpaceJoyConfig.isSpectatorTeleport()) {
            cir.setReturnValue(true);
        }
    }
}
