package net.spacejoy.mod.mixin.recipebook;

import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.spacejoy.mod.feature.recipebook.SpaceJoyRecipeBookPageTurner;
import net.spacejoy.mod.feature.recipebook.SpaceJoyRecipeBookScrollable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 判断鼠标是不是落在配方书画板里，在的话把滚轮转成分页。
 *
 * <p>原版 {@code RecipeBookComponent#isMouseOver} 在 26.3 里是硬编码 {@code false}
 * （这个方法只为接口凑数），画板原点也走 private 的 {@code getXOrigin/getYOrigin}，
 * 所以这里按它自己的算法复算一遍矩形：
 * {@code x = (width - 147) / 2 - xOffset}、{@code y = (height - 166) / 2}，
 * 尺寸就是贴图画出来的 147×166 —— 和 {@code extractRenderState} 里那次 blit 用的完全一致，
 * 改版式时两边会一起漂，不会只错一处。
 */
@Mixin(RecipeBookComponent.class)
public abstract class MixinRecipeBookComponentScroll implements SpaceJoyRecipeBookScrollable {
    /** 配方书打开时 {@code initVisuals} 往左推的距离，画板原点的计算要用。 */
    @Shadow
    private int xOffset;

    /** 当前界面宽度（GUI 缩放后的逻辑像素），和 Screen 的 width 同源。 */
    @Shadow
    private int width;

    /** 当前界面高度。 */
    @Shadow
    private int height;

    /** 配方书是不是开着。关着的时候画板不画，滚轮也不该被它吃掉。 */
    @Shadow
    private boolean visible;

    @Shadow
    @Final
    private RecipeBookPage recipeBookPage;

    @Override
    public boolean spacejoy$scrollRecipeBookPage(double mouseX, double mouseY, int dir) {
        if (!visible || recipeBookPage == null) {
            return false;
        }
        int originX = (width - RecipeBookComponent.IMAGE_WIDTH) / 2 - xOffset;
        int originY = (height - RecipeBookComponent.IMAGE_HEIGHT) / 2;
        if (mouseX < originX || mouseX >= originX + RecipeBookComponent.IMAGE_WIDTH
                || mouseY < originY || mouseY >= originY + RecipeBookComponent.IMAGE_HEIGHT) {
            return false;
        }
        return ((SpaceJoyRecipeBookPageTurner) recipeBookPage).spacejoy$turnRecipeBookPage(dir);
    }
}
