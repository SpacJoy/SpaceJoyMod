package net.spacejoy.mod.irisdim.compat.iris;

/**
 * 让 Iris 跳过紧跟在「我们自己刚建好的管线」之后的那一次销毁 + 重建。
 *
 * <h2>为什么需要这个</h2>
 * 维度切换时 Iris 会在 {@code Minecraft#updateLevelInEngines} 的 HEAD 上做一次
 * {@code destroyPipeline()} + {@code preparePipeline(当前维度)}。它判断「维度变了」用的是
 * <b>引用比较</b>：
 *
 * <pre>{@code if (Iris.getCurrentDimension() != Iris.lastDimension)}</pre>
 *
 * 而 {@code Iris#getCurrentDimension()} 每次都 {@code new NamespacedId(...)}，新对象永远不等于
 * 旧对象，所以这个条件实际上<b>恒为真</b>——给 {@code Iris.lastDimension} 赋任何值都拦不住它。
 * 实测日志里 {@code Reloading pipeline on dimension change: the_nether => the_nether}（两边同名）
 * 就是这条恒真条件的直接证据。
 *
 * <p>于是本模组的早切换路径（在 Iris 之前 reload 换包）虽然让 Iris 用的是<b>新</b>包，但 Iris
 * 仍然会把我们刚建好的管线销毁再建一遍：一次换包两次完整管线构建、两次全量区块重建
 * （{@code preparePipeline} 内部会触发 {@code levelExtractor.allChanged()}）。
 *
 * <p>这里不试图去改 Iris 的判断，而是把那次多余的销毁吞掉：{@code destroyPipeline()} 被取消后，
 * {@code pipelinesPerDimension} 里还留着我们刚为新维度建好的管线，Iris 随后的
 * {@code preparePipeline} 命中缓存直接返回，既不重建也不再触发 allChanged。
 *
 * <h2>为什么有时限</h2>
 * 取消销毁是有前提的：只在「刚建好、还没被任何人用过」的那一瞬间才安全。用一个很短的时限
 * （{@link #FRESH_WINDOW_NANOS}）把这份许可限制在同一帧附近；万一 Iris 的注入因为别的模组
 * 干扰没有执行，过期的许可也不会污染之后任意一次正常的 {@code destroyPipeline()}。
 */
public final class PipelineRebuildGuard {
    /**
     * 许可的有效期。Iris 的注入和我们挂在同一个方法上，实际间隔是毫秒级；给到 250ms 足够宽容，
     * 又不至于跨到下一次手动换包（那至少是几十毫秒之后的另一次交互）。
     */
    private static final long FRESH_WINDOW_NANOS = 250_000_000L;

    /** 到这一刻之前，{@code destroyPipeline()} 的调用被视为冗余。 */
    private static long skipUntil;

    private PipelineRebuildGuard() {
    }

    /** 管线已经按新维度 + 新包建好，紧跟着的那次销毁可以省掉。 */
    public static void markFresh() {
        skipUntil = System.nanoTime() + FRESH_WINDOW_NANOS;
    }

    /** 本次销毁是否该跳过；命中即视为已消费，避免一次许可吞掉两次销毁。 */
    public static boolean consumeSkip() {
        if (skipUntil == 0L || System.nanoTime() >= skipUntil) {
            skipUntil = 0L;
            return false;
        }

        skipUntil = 0L;
        return true;
    }

    /** 放弃尚未消费的许可（早切换失败或根本没走到建管线那一步时用）。 */
    public static void clear() {
        skipUntil = 0L;
    }
}
