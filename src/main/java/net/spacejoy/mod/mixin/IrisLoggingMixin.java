package net.spacejoy.mod.mixin;

import net.irisshaders.iris.IrisLogging;
import net.spacejoy.mod.compat.IrisCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把 {@code entity_outline/0..3} 后处理链的 {@code fatal} 日志降级掉。
 *
 * <p>Iris 的 {@code MixinShaderManager_Overrides} 对 {@code minecraft} 命名空间缺失的管线
 * 一律打 {@code fatal}，但后处理链的全屏 pass 本来就没有、也不需要光影映射
 * （{@code blur.json}、{@code invert.json} 同样是「缺失」且 Iris 从不报错，因为它们不是
 * {@code minecraft} 命名空间下的静态 {@code RenderPipelines} 字段）。
 * 这一次启动里这 4 条会白刷一屏吓人的 FATAL + 堆栈，这里只吃掉这一类。
 *
 * <p>只拦 {@code fatal(String, Throwable)}；其它重载（包括普通 {@code error}）保持原样，
 * 真正的映射缺失仍然会打出来。
 */
@Mixin(value = IrisLogging.class, remap = false)
public abstract class IrisLoggingMixin {
    @Inject(method = "fatal(Ljava/lang/String;Ljava/lang/Throwable;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void spacejoy$silenceEntityOutlineFatal(String message, Throwable throwable, CallbackInfo ci) {
        if (IrisCompat.shouldSilenceFatal(message)) {
            ci.cancel();
        }
    }
}
