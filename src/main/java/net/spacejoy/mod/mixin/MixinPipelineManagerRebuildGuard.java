package net.spacejoy.mod.mixin;

import net.irisshaders.iris.pipeline.PipelineManager;
import net.spacejoy.mod.SpaceJoyMod;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.irisdim.compat.iris.PipelineRebuildGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 吞掉「我们刚建好管线、Iris 紧接着又要拆掉重建」的那一次销毁。
 *
 * <h2>为什么不能靠对齐 {@code Iris.lastDimension} 解决</h2>
 * Iris 判断维度是否变化用的是引用比较：{@code if (Iris.getCurrentDimension() != Iris.lastDimension)}，
 * 而 {@code Iris#getCurrentDimension()} 每次调用都 {@code new NamespacedId(...)}。新对象永远不等于
 * 旧对象，所以这个条件<b>恒为真</b>，给 {@code lastDimension} 赋任何值都没用。
 * 实测日志里 {@code Reloading pipeline on dimension change: the_nether => the_nether}（两边同名）
 * 就是这条恒真条件的证据——注意这行日志仍会照常打印，因为它在销毁之前。
 *
 * <p>能省的是它后面那次销毁 + 重建：{@link PipelineRebuildGuard#markFresh()} 已在早切换路径
 * 成功之后打过标记，这里把 {@code destroyPipeline()} 取消，Iris 随后的
 * {@code preparePipeline} 命中缓存直接返回，不再重建、也不再触发全量区块重建。
 *
 * <p>验证时看的是 {@code Destroying pipeline ...} 和 {@code Creating pipeline for dimension ...}
 * 这两行是否消失，而不是 {@code Reloading pipeline on dimension change} 那行。
 */
@Mixin(value = PipelineManager.class, remap = false, priority = 900)
public abstract class MixinPipelineManagerRebuildGuard {
    @Inject(method = "destroyPipeline", at = @At("HEAD"), cancellable = true, require = 0)
    private void spacejoy$skipRedundantDestroy(CallbackInfo ci) {
        boolean skip;

        try {
            skip = PipelineRebuildGuard.consumeSkip();
        } catch (Throwable t) {
            // 早切只是优化：这里出问题就退回 Iris 原本行为，不要因为一个开关把换包弄坏。
            SpaceJoyMod.LOGGER.warn("Pipeline rebuild guard failed; falling back to Iris's own rebuild", t);
            return;
        }

        if (skip) {
            if (SpaceJoyConfig.isDebugLog()) {
                SpaceJoyMod.LOGGER.info("skipped Iris's redundant pipeline destroy: the new pipeline is already in place");
            }

            ci.cancel();
        }
    }
}
