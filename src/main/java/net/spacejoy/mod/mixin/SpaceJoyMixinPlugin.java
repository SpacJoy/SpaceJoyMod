package net.spacejoy.mod.mixin;

import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import net.spacejoy.mod.SpaceJoyMod;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * 让「修别的模组」的混入只在对应模组存在时才加载。
 *
 * <p>Mixin 在目标类不存在时会直接抛 {@code ClassNotFoundException} 把游戏带崩，
 * 而这些都是可选依赖（{@code fabric.mod.json} 里只写在 {@code suggests}）。
 * 与其给每个混入都配一个 {@code required: false} 的独立 config，不如在这里统一判断 ——
 * 也顺便让「模组装了但用户在配置里关掉了这项修复」时不激活混入。
 */
public final class SpaceJoyMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> IRIS_MIXINS = Set.of(
            "net.spacejoy.mod.mixin.IrisPipelinesMixin",
            "net.spacejoy.mod.mixin.IrisLoggingMixin"
    );
    private static final String POLYMER_JEI_MIXIN = "net.spacejoy.mod.mixin.PolymerJeiCompatibilityMixin";
    /**
     * TaCZ 的 {@code DataHolder.dataMap} 是裸 {@code HashMap}，服务端 tick 线程和实体 NBT 读写线程
     * 会同时碰它，装了 c2me 之后必撞 {@code ConcurrentModificationException}。
     * 这是唯一一组挂在非 client 环境下的软依赖混入 —— 崩溃发生在 Integrated Server 线程，
     * 只修客户端等于没修。整包一起判，新增混入不用再改这里。
     */
    private static final String TACZ_MIXIN_PACKAGE = "net.spacejoy.mod.mixin.tacz.";
    /**
     * 自由相机在装了 Sodium 时需要额外把相机停留过的区块标脏 ——
     * Sodium 自己接管了可见区块的选取，原版那条 path 不走了。
     */
    private static final String SODIUM_FREE_CAMERA_MIXIN =
            "net.spacejoy.mod.mixin.freecam.MixinLevelRendererFreeCameraSodium";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (IRIS_MIXINS.contains(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("iris");
        }
        if (POLYMER_JEI_MIXIN.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("polymer-core")
                    && FabricLoader.getInstance().isModLoaded("jei");
        }
        if (SODIUM_FREE_CAMERA_MIXIN.equals(mixinClassName)) {
            return FabricLoader.getInstance().isModLoaded("sodium");
        }
        if (mixinClassName.startsWith(TACZ_MIXIN_PACKAGE)) {
            return FabricLoader.getInstance().isModLoaded("tacz");
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
