package net.spacejoy.mod.compat;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Rarity;
import org.cyclops.everlastingabilities.api.IAbilityType;

/**
 * 按「最低稀有度 + 递减权重」从能力池里挑一档稀有度。
 *
 * <p>规则只有两条：
 * <ol>
 *   <li>结果不低于 {@code minRarity}（配置的保底）</li>
 *   <li>越高越少见：从保底档往上，权重依次 8 / 2 / 1 / 1</li>
 * </ol>
 *
 * <p>保底档写 RARE 时，实际分布约 80% RARE + 20% EPIC —— 每一只精英都至少是「高稀有度」，
 * 但偶尔摸到 EPIC 仍有惊喜。如果直接锁 EPIC，连打十几只精英就完全可预测了。
 *
 * <p>池子里没有保底档（例如被数据包禁用光了）时，退回池子里实际存在的最高档，
 * 而不是返回空：掉个次一级的图腾，比精英白死强。
 */
final class RarityFilter {
    /** 从保底档往上每一档的权重，下标是相对保底档的偏移。 */
    private static final int[] WEIGHTS = {8, 2, 1, 1};

    private RarityFilter() {
    }

    static Rarity pick(List<Holder<IAbilityType>> pool, Rarity minRarity, RandomSource random) {
        Rarity[] all = Rarity.values();
        List<Rarity> candidates = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (Rarity rarity : all) {
            if (rarity.ordinal() < minRarity.ordinal()) {
                continue;
            }
            if (!hasRarity(pool, rarity)) {
                continue;
            }
            candidates.add(rarity);
            weights.add(WEIGHTS[Math.min(rarity.ordinal() - minRarity.ordinal(), WEIGHTS.length - 1)]);
        }
        if (!candidates.isEmpty()) {
            return weightedPick(candidates, weights, random);
        }
        // 保底档整个不在池子里：退回池子里最高的那一档。
        for (int i = all.length - 1; i >= 0; i--) {
            if (hasRarity(pool, all[i])) {
                return all[i];
            }
        }
        // 调用方已经保证池子非空，走不到这里。
        return all[0];
    }

    private static boolean hasRarity(List<Holder<IAbilityType>> pool, Rarity rarity) {
        return pool.stream().anyMatch(holder -> holder.value().getRarity() == rarity);
    }

    private static Rarity weightedPick(List<Rarity> candidates, List<Integer> weights, RandomSource random) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < candidates.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                return candidates.get(i);
            }
        }
        return candidates.get(candidates.size() - 1);
    }
}
