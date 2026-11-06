package net.spacejoy.mod.mixin.recipebook;

import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.spacejoy.mod.feature.recipebook.SpaceJoyRecipeBookPageTurner;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 让配方书的分页能被滚轮驱动。
 *
 * <p>原版翻页只有两条路：点 {@code forwardButton} / {@code backButton}，两条都在
 * {@code RecipeBookPage#mouseClicked} 里直接改 {@code currentPage} 再调
 * {@code updateButtonsForPage()}。这两个成员都是 private，外面拿不到，
 * 所以这里用 {@code @Invoker} 把刷新方法暴露出来，状态仍用原版那两个字段 ——
 * 页码、按钮显隐、提示文案全都照旧，不另建一套分页状态。
 */
@Mixin(RecipeBookPage.class)
public abstract class MixinRecipeBookPageScroll implements SpaceJoyRecipeBookPageTurner {
    /** 当前显示的页码，从 0 开始。 */
    @Shadow
    private int currentPage;

    /** 总页数，由 {@code updateCollections} 按配方数量算出来。 */
    @Shadow
    private int totalPages;

    /** 配方书里点开「可替代配方」时盖在上面的浮层，它开着时不翻页。 */
    @Shadow
    @Final
    private OverlayRecipeComponent overlay;

    /**
     * 原版刷这批按钮的方法。
     *
     * <p>private 方法不能声明成 abstract 接口方法，只能靠 {@code @Invoker} 生成访问器 ——
     * 这也是为什么本类必须声明为 abstract。
     */
    @Invoker("updateButtonsForPage")
    abstract void spacejoy$updateButtonsForPage();

    @Override
    public boolean spacejoy$turnRecipeBookPage(int dir) {
        if (dir == 0 || totalPages <= 1) {
            return false;
        }
        // 浮层（一组可替代配方）开着时，滚轮留给玩家自己看，不翻页。
        if (overlay != null && overlay.isVisible()) {
            return false;
        }
        int target = Math.max(0, Math.min(totalPages - 1, currentPage + dir));
        if (target == currentPage) {
            // 已经在第一页/最后一页，交给调用方放行，别把滚动吞掉。
            return false;
        }
        currentPage = target;
        spacejoy$updateButtonsForPage();
        return true;
    }
}
