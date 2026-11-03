package net.spacejoy.mod.compat;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.cyclops.everlastingabilities.EverlastingAbilitiesInstance;
import org.cyclops.everlastingabilities.GeneralConfig;
import org.cyclops.everlastingabilities.api.Ability;
import org.cyclops.everlastingabilities.api.IAbilityType;

/**
 * Everlasting Abilities 的全部接触面都收在这一个类里。
 *
 * <p>调用方必须先判断 {@link net.spacejoy.mod.CompatFlags#EA_LOADED}，
 * 否则本类加载时会因为找不到 EA 的类而抛 NoClassDefFoundError。
 */
public final class EACompat {
    private EACompat() {
    }

    /**
     * 生物当前携带的能力文本（已过滤掉等级 &lt;= 0 的空能力）。
     */
    public static List<Component> getAbilityLines(Entity entity) {
        return EverlastingAbilitiesInstance.MOD.getAbilityHelpers()
                .getEntityAbilityStore(entity)
                .map(store -> store.getAbilities().stream()
                        .filter(ability -> !ability.isEmpty())
                        .map(Ability::getTextComponent)
                        .toList())
                .orElse(List.of());
    }

    public static boolean hasAbilities(Entity entity) {
        return EverlastingAbilitiesInstance.MOD.getAbilityHelpers()
                .getEntityAbilityStore(entity)
                .map(store -> store.getAbilities().stream().anyMatch(ability -> !ability.isEmpty()))
                .orElse(false);
    }

    /**
     * 关掉 EA 自带的「生物身上冒能力粒子」。
     *
     * <p>那套粒子走 CyclopsCore 的渲染挂钩，在 Iris + 光影下会画出错误的渲染结果；
     * 我们的标记方案已经取代它，所以开着自己的功能时把它压掉。
     *
     * <p>只改运行期静态值，不动 EA 的配置文件：EA 下次启动会按 toml 重新加载，
     * 我们每次进游戏都会再压一次，行为一致但不污染别人的配置。
     */
    public static void suppressEntityParticles() {
        if (GeneralConfig.showEntityParticles) {
            GeneralConfig.showEntityParticles = false;
        }
    }

    /**
     * 按稀有度下限抽一个能力，做成图腾物品。
     *
     * <p>抽取范围是 EA 的「可战利品获得」池（{@code isObtainableOnLoot} 且没被禁用），
     * 和它自己在地牢箱子里塞图腾用的是同一批能力，不会抽出只在合成/出生时才给的能力。
     *
     * <p>稀有度取「不低于 {@code minRarity} 的最低那一档」而不是直接给 EPIC：
     * 直接给 EPIC 会让每一只精英都掉最高稀有度，多打几只就没感觉了。
     * 具体落到哪一档由 {@link RarityFilter} 里的权重决定。
     *
     * @param minRarity 最低稀有度；{@code COMMON} 表示不做限制
     * @return 做好能力的图腾；没有可用能力时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack createEliteTotem(Level level, Rarity minRarity, RandomSource random) {
        var helpers = EverlastingAbilitiesInstance.MOD.getAbilityHelpers();
        List<Holder<IAbilityType>> pool = helpers.getAbilityTypesLoot(helpers.getRegistry(level.registryAccess()));
        if (pool.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Rarity picked = RarityFilter.pick(pool, minRarity, random);
        return helpers.getRandomAbility(pool, random, picked)
                .map(abilityType -> helpers.getTotem(new Ability(abilityType, 1)))
                .orElse(ItemStack.EMPTY);
    }
}
