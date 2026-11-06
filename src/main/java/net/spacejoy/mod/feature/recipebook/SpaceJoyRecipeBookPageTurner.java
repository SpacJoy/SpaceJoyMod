package net.spacejoy.mod.feature.recipebook;

/**
 * 由 {@code RecipeBookPage} 的混入实现，把原版那两个翻页按钮背后的动作拆出来直接调用。
 *
 * <p>原版 {@code currentPage} / {@code updateButtonsForPage()} 都是私有的，
 * 只有 {@code mouseClicked} 里点中箭头按钮才会走一遍。这里复用同一套状态，
 * 不重新实现按钮布局，翻页后箭头显隐也跟着原版逻辑走。
 *
 * <p><b>注意：这个接口不能放在 {@code net.spacejoy.mod.mixin} 包里</b>，原因见
 * {@link SpaceJoyRecipeBookScrollable} 的类注释。
 */
public interface SpaceJoyRecipeBookPageTurner {
    /**
     * @param dir -1 上一页，+1 下一页
     * @return 页码真的变了才返回 true
     */
    boolean spacejoy$turnRecipeBookPage(int dir);
}
