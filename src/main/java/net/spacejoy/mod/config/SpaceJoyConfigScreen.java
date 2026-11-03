package net.spacejoy.mod.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Rarity;
import net.spacejoy.mod.CompatFlags;
import net.spacejoy.mod.irisdim.IrisDimClient;
import net.spacejoy.mod.irisdim.client.DimensionSwitchController;
import net.spacejoy.mod.irisdim.client.gui.ConfigScreen;
import net.spacejoy.mod.irisdim.config.ModSettings;
import net.spacejoy.mod.irisdim.config.ModConfig;

/**
 * ModMenu 里的配置界面，用 Cloth Config 的现成模板。
 *
 * <p>按用途分成五个标签页：通用 / 修复 / 功能 / 联动 / 维度光影。
 *
 * <p><b>缺依赖就置灰</b>：某个功能需要的前置没装时，对应的开关会被设成不可编辑而不是直接隐藏，
 * 这样玩家能看到「有这么个功能、为什么用不了」，而不是对着一个空页面猜。页面本身也保留，
 * 只是里面所有开关都置灰；理由写在每项说明的最后一行。
 */
@Environment(EnvType.CLIENT)
public final class SpaceJoyConfigScreen {
    private static final String K = "spacejoy_mod.config.";
    private static final int WARN_COLOR = 0xFFAA00;

    private SpaceJoyConfigScreen() {
    }

    public static Screen create(Screen parent) {
        boolean ea = CompatFlags.EA_LOADED;
        boolean jade = CompatFlags.JADE_LOADED;
        boolean iris = CompatFlags.IRIS_LOADED;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable(K + "title"))
                .setSavingRunnable(() -> {
                    SpaceJoyConfig.save();
                    net.spacejoy.mod.client.ClientMobLabel.clearCache();
                    saveIrisSettings();
                });
        ConfigEntryBuilder eb = builder.entryBuilder();

        buildGeneral(builder.getOrCreateCategory(Component.translatable(K + "tab.general")), eb);
        buildFixes(builder.getOrCreateCategory(Component.translatable(K + "tab.fixes")), eb, iris);
        buildFeatures(builder.getOrCreateCategory(Component.translatable(K + "tab.features")), eb, ea, jade);
        buildIntegration(builder.getOrCreateCategory(Component.translatable(K + "tab.integration")), eb, ea);
        buildIris(builder.getOrCreateCategory(Component.translatable(K + "tab.iris")), eb, iris, parent);

        return builder.build();
    }

    // -------------------------------------------------------------------------------------
    // 通用
    // -------------------------------------------------------------------------------------

    private static void buildGeneral(ConfigCategory category, ConfigEntryBuilder eb) {
        category.addEntry(toggle(eb, K + "enabled", SpaceJoyConfig.isEnabled(),
                SpaceJoyConfig::setEnabled, K + "enabled.tip"));
        category.addEntry(toggle(eb, K + "debugLog", SpaceJoyConfig.isDebugLog(),
                SpaceJoyConfig::setDebugLog, K + "debugLog.tip"));
    }

    // -------------------------------------------------------------------------------------
    // 修复
    // -------------------------------------------------------------------------------------

    private static void buildFixes(ConfigCategory category, ConfigEntryBuilder eb, boolean iris) {
        boolean polymer = CompatFlags.POLYMER_LOADED;
        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "polymerRecipeFix"),
                        SpaceJoyConfig.isPolymerRecipeFix())
                .setDefaultValue(true)
                .setSaveConsumer(SpaceJoyConfig::setPolymerRecipeFix)
                .setTooltip(tooltip(K + "polymerRecipeFix.tip", polymer, "polymer-core"))
                .build(), polymer));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "irisOutlineFix"),
                        SpaceJoyConfig.isIrisOutlineFix())
                .setDefaultValue(false)
                .setSaveConsumer(SpaceJoyConfig::setIrisOutlineFix)
                .setTooltip(tooltip(K + "irisOutlineFix.tip", iris, "Iris"))
                .build(), iris));

        if (!polymer) {
            category.addEntry(note(eb, K + "missing.polymer"));
        }
        if (!iris) {
            category.addEntry(note(eb, K + "missing.iris"));
        }
    }

    // -------------------------------------------------------------------------------------
    // 功能
    // -------------------------------------------------------------------------------------

    private static void buildFeatures(ConfigCategory category, ConfigEntryBuilder eb,
                                      boolean ea, boolean jade) {
        boolean jadeOk = jade && ea;
        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "jadeAbilityDisplay"),
                        SpaceJoyConfig.isJadeAbilityDisplay())
                .setDefaultValue(true)
                .setSaveConsumer(SpaceJoyConfig::setJadeAbilityDisplay)
                .setTooltip(tooltip(K + "jadeAbilityDisplay.tip", jadeOk, "Jade + Everlasting Abilities"))
                .build(), jadeOk));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "mobLabel"),
                        SpaceJoyConfig.isMobLabel())
                .setDefaultValue(true)
                .setSaveConsumer(v -> {
                    SpaceJoyConfig.setMobLabel(v);
                    // 打开时顺手把 EA 自己那套会画错的粒子压掉；关掉时不用管，它本来就是关的。
                    if (v && ea) {
                        net.spacejoy.mod.compat.EACompat.suppressEntityParticles();
                    }
                })
                .setTooltip(tooltip(K + "mobLabel.tip", ea, "Everlasting Abilities"))
                .build(), ea));

        if (!ea) {
            category.addEntry(note(eb, K + "missing.everlastingabilities"));
        }
        if (!jade) {
            category.addEntry(note(eb, K + "missing.jade"));
        }
    }

    // -------------------------------------------------------------------------------------
    // 联动
    // -------------------------------------------------------------------------------------

    /**
     * BuffMobs 精英 × Everlasting Abilities：精英死时必掉一个高稀有度能力图腾。
     *
     * <p>两个前置都要：没有 BuffMobs 就没有「精英」这个身份可判断，没有 EA 就没有能力图腾可掉。
     * 缺任意一个就把这一页的开关全置灰，并在页面底部说明缺什么。
     */
    private static void buildIntegration(ConfigCategory category, ConfigEntryBuilder eb, boolean ea) {
        boolean buffMobs = CompatFlags.BUFFMOBS_LOADED;
        boolean ok = ea && buffMobs;

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "eliteAbilityDrop"),
                        SpaceJoyConfig.isEliteAbilityDrop())
                .setDefaultValue(true)
                .setSaveConsumer(SpaceJoyConfig::setEliteAbilityDrop)
                .setTooltip(tooltip(K + "eliteAbilityDrop.tip", ok, "BuffMobs + Everlasting Abilities"))
                .build(), ok));

        category.addEntry(gate(eb.startEnumSelector(
                        Component.translatable(K + "eliteAbilityMinRarity"),
                        Rarity.class, SpaceJoyConfig.getEliteAbilityMinRarity())
                .setDefaultValue(Rarity.RARE)
                .setEnumNameProvider(v -> Component.translatable(
                        K + "eliteAbilityMinRarity." + ((Enum<?>) v).name()))
                .setSaveConsumer(v -> SpaceJoyConfig.setEliteAbilityMinRarityName(v.name()))
                .setTooltip(tooltip(K + "eliteAbilityMinRarity.tip", ok, "BuffMobs + Everlasting Abilities"))
                .build(), ok));

        if (!buffMobs) {
            category.addEntry(note(eb, K + "missing.buffmobs"));
        }
        if (!ea) {
            category.addEntry(note(eb, K + "missing.everlastingabilities"));
        }
    }

    // -------------------------------------------------------------------------------------
    // 维度光影
    // -------------------------------------------------------------------------------------

    private static void buildIris(ConfigCategory category, ConfigEntryBuilder eb, boolean iris, Screen parent) {
        // 整个维度光影只有这一个总开关：它同时写模组自己的 spacejoy-mod.json 和规则文件里的
        // settings.enabled。早先这里是两个开关（「按维度切换光影」+「启用维度光影规则」），
        // 描述方向不同但做的是同一件事，所以合并了。
        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "irisDimensionShaders"),
                        SpaceJoyConfig.isIrisDimensionShaders())
                .setDefaultValue(true)
                .setSaveConsumer(v -> {
                    SpaceJoyConfig.setIrisDimensionShaders(v);
                    DimensionSwitchController controller = IrisDimClient.controller();
                    if (controller != null) {
                        controller.config().settings.enabled = v;
                    }
                })
                .setTooltip(tooltip(K + "irisDimensionShaders.tip", iris, "Iris"))
                .build(), iris));

        // 规则编辑器是一个自绘界面（左列表 + 右详情面板 + 行内勾选框），Cloth 的条目拼不出来，
        // 所以这里只留一个入口按钮，点进去就是原来的规则编辑页。Iris 缺失时按钮置灰。
        category.addEntry(gate(new ScreenLinkEntry(
                Component.translatable(K + "iris.editRules"),
                Component.translatable(K + "iris.editRules.button"),
                () -> openRuleEditor(parent)), iris));

        if (!iris) {
            category.addEntry(note(eb, K + "missing.iris"));
            return;
        }

        DimensionSwitchController controller = IrisDimClient.controller();
        if (controller == null) {
            category.addEntry(note(eb, K + "missing.iris"));
            return;
        }

        ModSettings settings = controller.config().settings;
        boolean on = iris;

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.applyOnWorldEnter"), settings.applyOnWorldEnter)
                .setDefaultValue(true)
                .setSaveConsumer(v -> settings.applyOnWorldEnter = v)
                .setTooltip(tooltip(K + "iris.applyOnWorldEnter.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.applyOnDimensionChange"), settings.applyOnDimensionChange)
                .setDefaultValue(true)
                .setSaveConsumer(v -> settings.applyOnDimensionChange = v)
                .setTooltip(tooltip(K + "iris.applyOnDimensionChange.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.applyDuringLevelLoading"), settings.applyDuringLevelLoading)
                .setDefaultValue(true)
                .setSaveConsumer(v -> settings.applyDuringLevelLoading = v)
                .setTooltip(tooltip(K + "iris.applyDuringLevelLoading.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.restoreOnWorldExit"), settings.restoreOnWorldExit)
                .setDefaultValue(false)
                .setSaveConsumer(v -> settings.restoreOnWorldExit = v)
                .setTooltip(tooltip(K + "iris.restoreOnWorldExit.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.reloadOnlyWhenChanged"), settings.reloadOnlyWhenChanged)
                .setDefaultValue(true)
                .setSaveConsumer(v -> settings.reloadOnlyWhenChanged = v)
                .setTooltip(tooltip(K + "iris.reloadOnlyWhenChanged.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.trackDiscoveredDimensions"),
                        settings.trackDiscoveredDimensions)
                .setDefaultValue(true)
                .setSaveConsumer(v -> settings.trackDiscoveredDimensions = v)
                .setTooltip(tooltip(K + "iris.trackDiscoveredDimensions.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.announceInChat"), settings.announceInChat)
                .setDefaultValue(true)
                .setSaveConsumer(v -> settings.announceInChat = v)
                .setTooltip(tooltip(K + "iris.announceInChat.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startIntSlider(
                        Component.translatable(K + "iris.announceDurationTicks"), settings.announceDurationTicks,
                        ModSettings.MIN_ANNOUNCE_TICKS, ModSettings.MAX_ANNOUNCE_TICKS)
                .setDefaultValue(ModSettings.DEFAULT_ANNOUNCE_TICKS)
                .setTextGetter(v -> Component.translatable(K + "iris.ticks", v))
                .setSaveConsumer(v -> settings.announceDurationTicks = v)
                .setTooltip(tooltip(K + "iris.announceDurationTicks.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startIntSlider(
                        Component.translatable(K + "iris.applyDelayTicks"), settings.applyDelayTicks,
                        ModSettings.MIN_DELAY_TICKS, ModSettings.MAX_DELAY_TICKS)
                .setDefaultValue(0)
                .setTextGetter(v -> Component.translatable(K + "iris.ticks", v))
                .setSaveConsumer(v -> settings.applyDelayTicks = v)
                .setTooltip(tooltip(K + "iris.applyDelayTicks.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startEnumSelector(
                        Component.translatable(K + "iris.missingPackPolicy"),
                        ModSettings.MissingPackPolicy.class, settings.policy())
                .setDefaultValue(ModSettings.MissingPackPolicy.SKIP_AND_WARN)
                .setEnumNameProvider(v -> Component.translatable(K + "iris.policy." + ((Enum<?>) v).name()))
                .setSaveConsumer(v -> settings.missingPackPolicy = v.name())
                .setTooltip(tooltip(K + "iris.missingPackPolicy.tip", on, "Iris"))
                .build(), on));
    }

    /** 打开规则编辑器；父界面取「当前正在显示的这个 Cloth 界面」，返回时回到原处。 */
    private static void openRuleEditor(Screen fallbackParent) {
        DimensionSwitchController controller = IrisDimClient.controller();
        if (controller == null || !CompatFlags.IRIS_LOADED) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        Screen current = client.gui.screen();
        client.setScreenAndShow(new ConfigScreen(controller, current == null ? fallbackParent : current));
    }

    // -------------------------------------------------------------------------------------
    // 保存
    // -------------------------------------------------------------------------------------

    /**
     * 把界面上改到的维度光影设置写回 {@code config/spacejoy-mod/irisdim/config.json}。
     *
     * <p>规则列表只在文件里编辑，界面不碰它，所以这里只落盘 settings，保留原 rules 不动。
     */
    private static void saveIrisSettings() {
        DimensionSwitchController controller = IrisDimClient.controller();
        if (controller == null || !CompatFlags.IRIS_LOADED) {
            return;
        }
        try {
            ModConfig current = controller.config();
            current.validate();
            controller.saveConfig(current);
        } catch (RuntimeException e) {
            net.spacejoy.mod.SpaceJoyMod.LOGGER.warn("failed to save dimension shader settings", e);
        }
    }

    // -------------------------------------------------------------------------------------
    // 小工具
    // -------------------------------------------------------------------------------------

    private static AbstractConfigListEntry toggle(ConfigEntryBuilder eb, String key, boolean value,
                                                  java.util.function.Consumer<Boolean> setter, String tipKey) {
        return eb.startBooleanToggle(Component.translatable(key), value)
                .setDefaultValue(true)
                .setSaveConsumer(setter)
                .setTooltip(Component.translatable(tipKey))
                .build();
    }

    /** 依赖缺失时把开关置灰，让它看起来「有但用不了」。 */
    private static <T extends AbstractConfigListEntry<?>> T gate(T entry, boolean available) {
        if (!available) {
            entry.setEditable(false);
        }
        return entry;
    }

    /** 正常说明 + （缺失时）最后补一行缺什么。 */
    private static Component[] tooltip(String tipKey, boolean available, String requirement) {
        if (available) {
            return new Component[]{Component.translatable(tipKey)};
        }
        return new Component[]{
                Component.translatable(tipKey),
                Component.translatable(K + "missing.need", requirement).withColor(WARN_COLOR)
        };
    }

    private static AbstractConfigListEntry note(ConfigEntryBuilder eb, String key) {
        return eb.startTextDescription(Component.translatable(key).withColor(WARN_COLOR)).build();
    }
}
