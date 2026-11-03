package net.spacejoy.mod.compat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;

/**
 * 服务端这边只剩一件事：把老版本留下的发光效果清掉。
 *
 * <p>0.6.0 及之前是服务端给生物挂无限时长的 {@code MobEffects.GLOWING} 来控制描边，
 * 那份效果会跟着存档走。换到客户端判定之后，这些遗留效果必须清掉，否则它们会绕过新的
 * 遮挡判断、隔着方块一直亮。
 *
 * <p>除此之外服务端<b>什么都不做</b>：不扫实体、不判能力、不打射线、不跑周期任务。
 * 实际标记由客户端 {@code net.spacejoy.mod.client.ClientMobLabel} 画在头顶。
 *
 * <p>历史弯路，别走回去：客户端 {@code setGlowingTag} 只改本地标记，别的玩家看不到；
 * 服务端撒 {@code ParticleTypes.GLOW} 粒子太细、还费 CPU 和带宽。
 */
public final class MobGlow {
    private MobGlow() {
    }

    public static void init() {
        // 这里刻意不判 EA_LOADED：清理遗留效果跟 EA 没关系，
        // 玩家把 EA 卸了、存档里那批无限时长发光反而更需要清掉。
        // 实体进世界时看一眼有没有遗留的无限时长发光。这是一次 O(1) 的效果表查找，
        // 只在实体加载时发生，不构成周期开销。
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Mob mob) {
                clearLegacy(mob);
            }
        });
    }

    /**
     * 清掉老版本挂上去的无限时长发光。
     *
     * <p>只删「无限时长」那种 —— 玩家用光灵箭打出来的有限时长发光是人家的，不动。
     *
     * <p><b>不看功能开关。</b>这种效果只可能是本模组 0.6.0 及之前留下的，玩家把功能关了照样得清，
     * 否则那批生物会一直按原版发光（不经遮挡判定、隔着山也亮），比开着功能还糟。
     */
    private static void clearLegacy(Mob mob) {
        MobEffectInstance current = mob.getEffect(MobEffects.GLOWING);
        if (current != null && current.isInfiniteDuration()) {
            mob.removeEffect(MobEffects.GLOWING);
        }
    }
}
