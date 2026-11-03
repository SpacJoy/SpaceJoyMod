package net.spacejoy.mod.mixin;

import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.spacejoy.mod.compat.IrisCompat;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给 Iris 补上 MC 26.3 新增的实体描边管线映射。
 *
 * <p>Iris 的 {@code IrisPipelines} 静态块只注册了它认识的那些 {@code RenderPipelines}，
 * {@code OUTLINE_CULL} / {@code OUTLINE_NO_CULL} 一个都没进表。{@code MixinShaderManager_Overrides}
 * 查表拿到 null 就打 {@code fatal("Missing program ... This is likely an Iris bug!!!")}，
 * 并且这条描边退回原版管线，开光影时与画面不协调。
 *
 * <p>注入点选 {@code getPipeline} 的 HEAD 而不是静态块 TAIL：静态块执行期间补表会牵扯到
 * {@code IrisCompat} 自身的类初始化顺序，而 {@code getPipeline} 只可能在静态块跑完之后
 * 才被调用，HEAD 补表刚好赶在同一次查表之前。补表本身是幂等的。
 */
@Mixin(value = IrisPipelines.class, remap = false)
public abstract class IrisPipelinesMixin {
    @Inject(method = "getPipeline", at = @At("HEAD"), require = 0)
    private static void spacejoy$ensureOutlinePipelines(IrisRenderingPipeline pipeline, RenderPipeline shader,
                                                        CallbackInfoReturnable<ShaderKey> cir) {
        IrisCompat.ensureOutlinePipelines();
    }
}
