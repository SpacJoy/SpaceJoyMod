package net.spacejoy.mod.compat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.spacejoy.mod.CompatFlags;
import net.spacejoy.mod.SpaceJoyMod;
import net.spacejoy.mod.config.SpaceJoyConfig;

/**
 * BuffMobs 精英生物 + Everlasting Abilities 的联动：精英死时必掉一个高稀有度能力图腾。
 *
 * <p>为什么是「额外掉落」而不是「改 EA 的掉落表」：EA 自己的 {@code onEntityDeath}
 * 只把生物<b>身上已有的</b>能力做成图腾掉出来，稀有度取决于那只生物随机到的能力，
 * 精英身份对它毫无影响。这里在死亡事件里再补一个按稀有度下限抽的图腾，
 * 不动 EA 的任何配置、也不改它的掉落逻辑，两边互不干扰。
 *
 * <p><b>必须排在 BuffMobs 前面。</b>BuffMobs 自己也监听 {@code AFTER_DEATH}，
 * 在里面调 {@code BuffMobsWorldData.removeMobState(uuid)} 把这只生物的精英记录删掉。
 * 如果我们排在它后面，{@code isElite()} 一定返回 false，功能看起来「装了但从不触发」。
 * 所以这里把处理器挂到 DEFAULT_PHASE 之前的一个具名 phase 上。
 *
 * <p>只用 {@code AFTER_DEATH}（死亡已经确定、战利品已经结算完），不用
 * {@code ALLOW_DEATH}：后者返回 false 会取消死亡，我们只是加掉落，没有取消权。
 */
public final class EliteAbilityDrop {
    /** 我们自己插在 DEFAULT_PHASE 之前的 phase。 */
    private static final Identifier EARLY_PHASE =
            Identifier.fromNamespaceAndPath(SpaceJoyMod.MOD_ID, "elite_drop_early");

    private EliteAbilityDrop() {
    }

    public static void init() {
        // 两个前置缺一个都没有意义：没有 BuffMobs 就没有精英，没有 EA 就没有能力图腾。
        // 这里提前返回，下面的 lambda 永远不会被注册，EliteMobCompat / EACompat 也就不会被解析。
        if (!CompatFlags.BUFFMOBS_LOADED || !CompatFlags.EA_LOADED) {
            return;
        }
        ServerLivingEntityEvents.AFTER_DEATH.addPhaseOrdering(EARLY_PHASE, Event.DEFAULT_PHASE);
        ServerLivingEntityEvents.AFTER_DEATH.register(EARLY_PHASE, EliteAbilityDrop::onDeath);
    }

    private static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (!SpaceJoyConfig.isEnabled() || !SpaceJoyConfig.isEliteAbilityDrop()) {
            return;
        }
        // 尊重 doMobLoot：玩家关掉生物掉落时，精英也不该凭空吐东西。
        if (!level.getGameRules().get(GameRules.MOB_DROPS)) {
            return;
        }
        if (!EliteMobCompat.isElite(mob)) {
            return;
        }

        ItemStack totem = EACompat.createEliteTotem(level,
                SpaceJoyConfig.getEliteAbilityMinRarity(), level.getRandom());
        if (totem.isEmpty()) {
            return;
        }
        // 用实体自己的掉落口子，位置、散布、拾取延迟都跟原版战利品一致。
        mob.spawnAtLocation(level, totem);
    }
}
