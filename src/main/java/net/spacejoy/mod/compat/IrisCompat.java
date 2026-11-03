package net.spacejoy.mod.compat;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.minecraft.client.renderer.RenderPipelines;
import net.spacejoy.mod.SpaceJoyMod;
import net.spacejoy.mod.config.SpaceJoyConfig;

/**
 * Iris 的桥接：只有这个类允许出现 Iris 的类型，混入类只转发调用。
 *
 * <p>背景：MC 26.3 给「发光实体描边」新增了两条渲染管线
 * （{@code minecraft:pipeline/outline_cull} 与 {@code minecraft:pipeline/outline_no_cull}），
 * 但 Iris 的 {@code IrisPipelines} 映射表里没有任何 outline 条目（上游从 1.16.5 到现在的分支
 * 用 {@code git log -S OUTLINE_CULL} 都查不到，不是 26.3 的回归）。
 * {@code MixinShaderManager_Overrides} 查表拿到 null 就走到
 * {@code Iris.logger.fatal("Missing program ... This is likely an Iris bug!!!")}，
 * 同时这条描边退回原版管线，开光影时发光生物边缘与画面不匹配。
 *
 * <p>修法用 Iris 自己的公开 API {@link IrisPipelines#assignPipeline(RenderPipeline, ShaderKey)}，
 * 不改 Iris 源码，也不碰它的私有映射表。
 */
public final class IrisCompat {
    /** 已经补过映射，避免反复进出世界时重复调用。 */
    private static boolean outlineRegistered;

    private IrisCompat() {
    }

    /**
     * 把 Iris 漏掉的 outline 管线补进映射表。幂等，重复调用无副作用。
     *
     * <p>顶点格式必须对得上：{@code OUTLINE_SNIPPET} 用的是
     * {@code DefaultVertexFormat.POSITION_TEX_COLOR}（shader {@code core/rendertype_outline}），
     * Iris 里同格式的键是 {@link ShaderKey#TEXTURED_COLOR}。
     * 不能照抄 {@code SECONDARY_BLOCK_OUTLINE → ShaderKey.LINES} 的先例：{@code LINES} 的顶点格式是
     * {@code POSITION_COLOR_NORMAL_LINE_WIDTH}，属性对不上会渲染错位。
     */
    public static void ensureOutlinePipelines() {
        if (outlineRegistered) {
            return;
        }
        // 和日志降级共用同一个开关：关掉后下次启动就退回原版管线（本次运行已经改过的映射撤不回来）。
        if (!SpaceJoyConfig.isEnabled() || !SpaceJoyConfig.isIrisOutlineFix()) {
            return;
        }
        outlineRegistered = true;
        // 只补主渲染表：实体描边不进阴影贴图，coreShaderMapShadow 不需要动。
        assign(RenderPipelines.OUTLINE_CULL);
        assign(RenderPipelines.OUTLINE_NO_CULL);
    }

    private static void assign(RenderPipeline pipeline) {
        try {
            IrisPipelines.assignPipeline(pipeline, ShaderKey.TEXTURED_COLOR);
            if (SpaceJoyConfig.isDebugLog()) {
                SpaceJoyMod.LOGGER.info("[iris] mapped {} to ShaderKey.TEXTURED_COLOR", pipeline.getLocation());
            }
        } catch (IllegalStateException e) {
            // 新版 Iris 自己补上了这条映射：让它去管，我们退让。
            if (SpaceJoyConfig.isDebugLog()) {
                SpaceJoyMod.LOGGER.info("[iris] {} already mapped by Iris, leaving it alone", pipeline.getLocation());
            }
        } catch (Throwable t) {
            // 光影内部状态异常不该影响启动，这里只记录。
            SpaceJoyMod.LOGGER.warn("[iris] failed to map {}", pipeline.getLocation(), t);
        }
    }

    /**
     * {@code minecraft:entity_outline/0..3} 是原版发光描边的后处理链
     * （sobel + 两次高斯模糊 + blit），四个全屏 pass。
     *
     * <p>它们本来就不该套光影包的世界 shader —— Iris 不给任何 post chain pass 做映射
     * （{@code blur.json} / {@code invert.json} 同样没有）。所以这里的「缺失」是正常行为，
     * 只是 Iris 对 {@code minecraft} 命名空间一律用 {@code fatal} 级别打印，才显得像故障。
     */
    public static boolean isEntityOutlinePostPass(String message) {
        return message != null && message.startsWith("Missing program minecraft:entity_outline/");
    }

    /**
     * 是否把这条 {@code fatal} 日志降级掉。
     *
     * <p>只在开关打开且确实是 entity_outline 后处理链时降级；其它 {@code Missing program}
     * 仍然原样输出，免得把真正的映射缺失一起藏起来。
     */
    public static boolean shouldSilenceFatal(String message) {
        return SpaceJoyConfig.isEnabled() && SpaceJoyConfig.isIrisOutlineFix() && isEntityOutlinePostPass(message);
    }
}
