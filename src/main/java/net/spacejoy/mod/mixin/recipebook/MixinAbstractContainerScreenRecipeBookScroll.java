package net.spacejoy.mod.mixin.recipebook;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.feature.recipebook.SpaceJoyRecipeBookScrollable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在带配方书的容器界面里，把落在配方书上的滚轮转成翻页。
 *
 * <p>原版这条路上没有配方书的位置：{@code AbstractRecipeBookScreen} 只覆写了
 * {@code mouseClicked} 和 {@code mouseDragged}，滚轮仍走 {@code AbstractContainerScreen} 的那份实现 ——
 * 它只在鼠标悬停的格子支持 {@code ItemSlotMouseAction} 时才接滚动，配方书区域一律放过。
 *
 * <p>这里只在 HEAD 插一针，翻不动就原样放行，绝不返回 true 把滚动吞掉：
 * 万一某个模组给格子挂了自定义滚轮行为，不能被配方书抢走。
 */
@Mixin(AbstractContainerScreen.class)
public class MixinAbstractContainerScreenRecipeBookScroll {
    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void spacejoy$scrollRecipeBookPage(double mouseX, double mouseY, double scrollX, double scrollY,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (scrollY == 0.0D || !SpaceJoyConfig.isRecipeBookScroll()) {
            return;
        }
        // 混入类本身不是 AbstractContainerScreen 的子类，`this instanceof` 直接编译不过，
        // 得先转成 Object 再判 —— 运行时 this 就是被混入的那个屏幕。
        Object self = this;
        if (!(self instanceof AbstractRecipeBookScreen<?> screen)) {
            return;
        }
        RecipeBookComponent<?> book = ((RecipeBookScreenAccessor) screen).spacejoy$getRecipeBookComponent();
        if (book == null) {
            return;
        }
        // 滚轮向上（scrollY > 0）看更靠前的页，和原版列表、创意模式物品栏的方向一致。
        int dir = scrollY > 0.0D ? -1 : 1;
        if (((SpaceJoyRecipeBookScrollable) book).spacejoy$scrollRecipeBookPage(mouseX, mouseY, dir)) {
            cir.setReturnValue(true);
        }
    }
}
