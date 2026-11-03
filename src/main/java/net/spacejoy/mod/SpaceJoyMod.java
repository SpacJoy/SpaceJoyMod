package net.spacejoy.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.spacejoy.mod.compat.EliteAbilityDrop;
import net.spacejoy.mod.compat.MobGlow;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SpaceJoyMod：一个整合包里的兼容性修复 + 便利功能模组。
 *
 * <p>当前内容：
 * <ul>
 *   <li>修复：polymer-core 的 ingredient 编解码在「无包上下文」时崩溃
 *       （连带 TACZ 配方同步失败、JEI 合成页空掉）</li>
 *   <li>功能：Jade 显示生物可掉落的能力（需要 Jade + Everlasting Abilities）</li>
 *   <li>功能：给带能力的生物加头顶标签（走原版命名牌通道，被命名时自动上移让位）</li>
 *   <li>功能：BuffMobs 精英生物必掉高稀有度能力图腾（需要 BuffMobs + Everlasting Abilities）</li>
 *   <li>功能：按维度切换 Iris 光影包（需要 Iris）</li>
 * </ul>
 *
 * <p>每一项都能在 ModMenu 的配置界面里单独开关，配置存 {@code config/spacejoy-mod.json}；
 * 维度光影的规则单独存在 {@code config/spacejoy-mod/irisdim/config.json}。
 */
public class SpaceJoyMod implements ModInitializer {
    public static final String MOD_ID = "spacejoy_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger("SpaceJoyMod");

    @Override
    public void onInitialize() {
        SpaceJoyConfig.load();
        if (SpaceJoyConfig.isDebugLog()) {
            String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString())
                    .orElse("?");
            LOGGER.info("SpaceJoyMod {} loaded (enabled={}, polymer={}, jade={}, everlastingabilities={}, buffmobs={}, iris={})",
                    version,
                    SpaceJoyConfig.isEnabled(),
                    CompatFlags.POLYMER_LOADED,
                    CompatFlags.JADE_LOADED,
                    CompatFlags.EA_LOADED,
                    CompatFlags.BUFFMOBS_LOADED,
                    CompatFlags.IRIS_LOADED);
        }
        MobGlow.init();
        EliteAbilityDrop.init();
    }
}
