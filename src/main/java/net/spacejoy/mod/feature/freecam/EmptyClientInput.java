package net.spacejoy.mod.feature.freecam;

import net.minecraft.client.Options;
import net.minecraft.client.player.KeyboardInput;

/**
 * 空输入：所有 {@code tick()} 都不做，因此不会把按键翻成移动向量。
 *
 * <p>自由相机冻结本体时用它顶替玩家真正的输入对象（移植自 tweakeroo 的 DummyMovementInput）。
 * 构造参数可以是 null —— 只有 {@code tick()} 会读它，而这里把 {@code tick()} 整个覆盖了。
 *
 * <p>这个类必须待在 {@code net.spacejoy.mod.feature} 下，不能放进 {@code net.spacejoy.mod.mixin}：
 * 那个包被 mixin 配置整个声明为己有，里面的非 mixin 类会被 Mixin 拒绝加载
 * （IllegalClassLoadError: ... is in a defined mixin package ... cannot be referenced directly）。
 */
public class EmptyClientInput extends KeyboardInput {
    public EmptyClientInput(Options options) {
        super(options);
    }

    @Override
    public void tick() {
        // 故意留空：冻结移动时不需要任何按键转化。
    }
}
