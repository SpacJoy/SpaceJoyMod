package net.spacejoy.mod.feature.recipebook;

/**
 * 由 {@code RecipeBookComponent} 的混入实现，给容器界面提供一个「按屏幕坐标翻配方书页」的入口。
 *
 * <p>原版 {@code RecipeBookComponent#isMouseOver} 在 26.3 里恒返回 {@code false}，
 * 也不对外暴露画板的原点，所以判定悬停只能在混入内部用它自己的
 * {@code width / height / xOffset} 算 —— 接口只把结果传出去，不外泄字段。
 *
 * <p><b>注意：这个接口不能放在 {@code net.spacejoy.mod.mixin} 包里。</b>
 * 那个包整体是 {@code spacejoy_mod.mixins.json} 声明的混入包，Mixin 禁止直接引用
 * 混入包里的类（{@code IllegalClassLoadError: ... is in a defined mixin package}），
 * 一旦被普通代码碰到就崩启动。
 */
public interface SpaceJoyRecipeBookScrollable {
    /**
     * 鼠标落在配方书画板内时翻一页。
     *
     * @param mouseX 鼠标的界面坐标 X（已按 GUI 缩放换算过）
     * @param mouseY 鼠标的界面坐标 Y
     * @param dir    -1 上一页，+1 下一页
     * @return 真的翻动了才返回 true；配方书没开、鼠标不在画板上、已经到头都返回 false
     */
    boolean spacejoy$scrollRecipeBookPage(double mouseX, double mouseY, int dir);
}
