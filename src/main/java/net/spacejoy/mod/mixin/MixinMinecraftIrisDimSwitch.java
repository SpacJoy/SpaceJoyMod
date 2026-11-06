package net.spacejoy.mod.mixin;

import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.spacejoy.mod.SpaceJoyMod;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.irisdim.IrisDimClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 抢在 Iris 之前换包，省掉一次注定被丢掉的管线构建。
 *
 * <p>Iris 自己在 {@code Minecraft#updateLevelInEngines} 的 HEAD 上挂了一个注入（见
 * {@code net.irisshaders.iris.mixin.MixinMinecraft_PipelineManagement}）：发现维度变了就
 * destroy 旧管线、按<b>当前已载入的包</b>为新维度再建一条。跨维度时当前载入的还是旧包，
 * 而本功能紧接着就会 reload 换成新包，于是这条管线建完立刻被扔——一次换包白编译一整套
 * 程序（composite / final / shadow / setup 在管线构造时就是预编译的）。
 *
 * <p>这里先自己把包换掉：reload 末尾 Iris 会为新维度准备好管线，成功之后再把
 * {@code Iris.lastDimension} 对齐，Iris 的注入随后就认为无事可做。换包失败或本轮不需要换
 * （无规则命中、已在目标状态、只差阴影距离）时直接返回，完全不干预 Iris 原本的行为。
 *
 * <h2>为什么优先级要比 Iris 低（900 而不是 1500）</h2>
 * 这条不能靠推理，有实测：0.14.0 用的是 1500，实机日志里 Iris 的
 * {@code Reloading pipeline on dimension change} 每次穿门都照常出现，而我们的
 * {@code dimension switch hook reached} 总是晚一步（00:21:24 vs 00:21:25，同一毫秒级的
 * 两次调用之间隔着 Iris 建整条管线的耗时）。结论是优先级数字<b>越大越晚执行</b>，
 * 想抢在 Iris（默认 1000）之前动手就必须写更小的数。改成 900 之后，顺序应当反过来：
 * 我们先 reload，reload 末尾 Iris 会为新维度备好新包的管线，最后对齐 {@code lastDimension}
 * 让 Iris 的注入判定无事可做，从而省掉那次「用旧包白建」的完整管线构建。
 */
@Mixin(value = Minecraft.class, priority = 900)
public abstract class MixinMinecraftIrisDimSwitch {
    /** 只在第一次进这个方法时打一行日志，用来确认注入点真的挂上了（require = 0 的代价）。 */
    private static boolean spacejoy$hookReached;

    @Inject(method = "updateLevelInEngines", at = @At("HEAD"), require = 0)
    private void spacejoy$switchPackBeforeIrisRebuilds(ClientLevel level, CallbackInfo ci) {
        if (level == null) {
            return;
        }

        if (!spacejoy$hookReached) {
            spacejoy$hookReached = true;
            if (SpaceJoyConfig.isDebugLog()) {
                SpaceJoyMod.LOGGER.info("dimension switch hook reached: shaderpacks now swap before Iris rebuilds its pipeline");
            }
        }

        try {
            if (IrisDimClient.applyEarlyOnLevelChange()) {
                Iris.lastDimension = Iris.getCurrentDimension();
            }
        } catch (Throwable t) {
            // 早切只是优化：这里出问题时少省一次构建而已，退回 tick 路径照样能换包。
            SpaceJoyMod.LOGGER.warn("Early shaderpack switch before Iris rebuilt its pipeline failed", t);
        }
    }
}