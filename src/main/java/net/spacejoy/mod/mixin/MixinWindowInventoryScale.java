package net.spacejoy.mod.mixin;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 自定义背包界面缩放（移植自 tweakeroo 的 tweakCustomInventoryScreenScale）。
 *
 * <p>原版 GUI 缩放是全局的，改一次影响所有界面。这里只在「当前界面是容器界面」时
 * 谎报一次缩放值，退出容器界面就恢复，别的界面不受影响。
 *
 * <p>宽高必须一起改：只改缩放不改尺寸的话，界面元素会按错误的坐标系摆放。
 */
@Mixin(Window.class)
public abstract class MixinWindowInventoryScale {
    @Shadow
    public abstract int getScreenWidth();

    @Shadow
    public abstract int getScreenHeight();

    @Inject(method = "getGuiScale", at = @At("HEAD"), cancellable = true)
    private void spacejoy$customScale(CallbackInfoReturnable<Integer> cir) {
        int scale = spacejoy$scaleForCurrentScreen();
        if (scale > 0) {
            cir.setReturnValue(scale);
        }
    }

    @Inject(method = "getGuiScaledWidth", at = @At("HEAD"), cancellable = true)
    private void spacejoy$customScaledWidth(CallbackInfoReturnable<Integer> cir) {
        int scale = spacejoy$scaleForCurrentScreen();
        if (scale > 0) {
            // 用 ceil 和原版保持一致，否则宽高会比实际小 1 像素，界面右侧会露边。
            cir.setReturnValue((int) Math.ceil((double) this.getScreenWidth() / scale));
        }
    }

    @Inject(method = "getGuiScaledHeight", at = @At("HEAD"), cancellable = true)
    private void spacejoy$customScaledHeight(CallbackInfoReturnable<Integer> cir) {
        int scale = spacejoy$scaleForCurrentScreen();
        if (scale > 0) {
            cir.setReturnValue((int) Math.ceil((double) this.getScreenHeight() / scale));
        }
    }

    /** 当前该用哪个缩放；返回 0 表示「不接管，走原版」。 */
    private static int spacejoy$scaleForCurrentScreen() {
        // 这个 mixin 会在一局游戏里被调用上千次，任何一次抛异常都会直接崩掉渲染，
        // 所以每个可能为空的引用都要先判。
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            return 0;
        }
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?>)) {
            return 0;
        }
        if (!SpaceJoyConfig.isCustomInventoryGuiScale()) {
            return 0;
        }

        return SpaceJoyConfig.getCustomInventoryScale();
    }
}
