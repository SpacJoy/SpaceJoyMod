package net.spacejoy.mod.config;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Rarity;
import net.spacejoy.mod.CompatFlags;
import net.spacejoy.mod.network.SpaceJoyClientNetwork;
import net.spacejoy.mod.config.SpaceJoyServerConfig;
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

    /** 当前由本类打开的那个界面（用来在权限变化时原地重建）。 */
    private static Screen openScreen;
    /** 它的父界面：重建时复用，否则关掉会掉进一个空界面。 */
    private static Screen openParent;
    /** 有「权限变了」待处理：重建要等界面重新成为当前界面才做，见 tickRefresh()。 */
    private static boolean refreshPending;

    public static Screen create(Screen parent) {
        boolean ea = CompatFlags.EA_LOADED;
        boolean jade = CompatFlags.JADE_LOADED;
        boolean iris = CompatFlags.IRIS_LOADED;

        // 上一次的「权限变了」待办到这里作废：界面已经重建，旧的待办只会造成一次多余重建。
        // 新界面开头本来就主动要一份最新快照（下面这行），状态一定是最新的。
        refreshPending = false;

        // 服务端只在进服时推一次 OP 状态，被 /op、/deop 改过之后客户端不知道，
        // 所以每次打开界面都主动要一份最新快照（单人不用问，本地就是权威）。
        SpaceJoyClientNetwork.requestStatus();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable(K + "title"))
                // 记住这次建出来的界面实例，权限变化时好原地重建（见 refreshIfOpen）。
                .setAfterInitConsumer(screen -> {
                    openScreen = screen;
                    openParent = parent;
                })
                .setSavingRunnable(() -> {
                    SpaceJoyConfig.save();
                    net.spacejoy.mod.client.ClientMobLabel.clearCache();
                    saveIrisSettings();
                });
        ConfigEntryBuilder eb = builder.entryBuilder();

        buildGeneral(builder.getOrCreateCategory(Component.translatable(K + "tab.general")), eb);
        buildFixes(builder.getOrCreateCategory(Component.translatable(K + "tab.fixes")), eb, iris);
        buildTweaks(builder.getOrCreateCategory(Component.translatable(K + "tab.tweaks")), eb);
        buildKeys(builder.getOrCreateCategory(Component.translatable(K + "tab.keys")), eb);
        buildFeatures(builder.getOrCreateCategory(Component.translatable(K + "tab.features")), eb, ea, jade);
        buildIntegration(builder.getOrCreateCategory(Component.translatable(K + "tab.integration")), eb, ea);
        buildServer(builder.getOrCreateCategory(Component.translatable(K + "tab.server")), eb);
        buildIris(builder.getOrCreateCategory(Component.translatable(K + "tab.iris")), eb, iris, parent);

        return builder.build();
    }

    /**
     * 权限状态变了（<code>/op</code>、<code>/deop</code>）之后重建配置界面。
     *
     * <p>Cloth Config 的条目在 build 时就把能不能编辑定死了，运行时没法改回来，
     * 所以整页重建最省事。重建延到「这个界面重新成为当前界面」的那一刻才做：
     * 玩家如果正点进子页面（比如维度光影规则编辑器），就先记一笔，别把他弹回来。
     */
    public static void tickRefresh() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || openScreen == null) {
            return;
        }
        if (client.gui.screen() == null) {
            // 界面已经关掉了（退回游戏里）。清掉引用，连「权限变了」的待办一起清：
            // 界面都没了，重建就没有意义，留着只会在下次打开时造成一次多余重建。
            openScreen = null;
            openParent = null;
            refreshPending = false;
            return;
        }
        if (!refreshPending) {
            return;
        }
        if (client.gui.screen() != openScreen) {
            // 还在子页面里，等回来再重建。
            return;
        }
        rebuild();
    }

    /** 收到「权限变了」时调用，把重建排到下一次 tick。 */
    public static void markRefreshPending() {
        refreshPending = true;
    }

    private static void rebuild() {
        refreshPending = false;
        Screen parent = openParent;
        // 先清空再建，避免 setAfterInitConsumer 递归指着新实例。
        openScreen = null;
        openParent = null;
        Minecraft.getInstance().setScreenAndShow(create(parent));
    }

    // -------------------------------------------------------------------------------------
    // 通用
    // -------------------------------------------------------------------------------------

    private static void buildGeneral(ConfigCategory category, ConfigEntryBuilder eb) {
        category.addEntry(toggle(eb, K + "enabled", SpaceJoyConfig.isEnabled(),
                SpaceJoyConfig.DEFAULT_ENABLED,
                SpaceJoyConfig::setEnabled, K + "enabled.tip"));
        category.addEntry(toggle(eb, K + "debugLog", SpaceJoyConfig.isDebugLog(),
                SpaceJoyConfig.DEFAULT_DEBUG_LOG,
                SpaceJoyConfig::setDebugLog, K + "debugLog.tip"));
    }

    // -------------------------------------------------------------------------------------
    // 修复
    // -------------------------------------------------------------------------------------

    private static void buildFixes(ConfigCategory category, ConfigEntryBuilder eb, boolean iris) {
        boolean polymer = CompatFlags.POLYMER_LOADED;
        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "polymerRecipeFix"),
                        SpaceJoyServerConfig.isPolymerRecipeFix())
                .setDefaultValue(SpaceJoyServerConfig.DEFAULT_POLYMER_RECIPE_FIX)
                .setSaveConsumer(v -> SpaceJoyClientNetwork.sendConfig("polymerRecipeFix", String.valueOf(v)))
                .setTooltip(tooltip(K + "polymerRecipeFix.tip", polymer, "polymer-core"))
                .build(), polymer && SpaceJoyServerConfig.canEdit()));

        boolean tacz = CompatFlags.TACZ_LOADED;
        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "taczConcurrencyFix"),
                        SpaceJoyServerConfig.isTaczConcurrencyFix())
                .setDefaultValue(SpaceJoyServerConfig.DEFAULT_TACZ_CONCURRENCY_FIX)
                .setSaveConsumer(v -> SpaceJoyClientNetwork.sendConfig("taczConcurrencyFix", String.valueOf(v)))
                .setTooltip(tooltip(K + "taczConcurrencyFix.tip", tacz, "TaCZ"))
                .build(), tacz && SpaceJoyServerConfig.canEdit()));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "irisOutlineFix"),
                        SpaceJoyConfig.isIrisOutlineFix())
                .setDefaultValue(SpaceJoyConfig.DEFAULT_IRIS_OUTLINE_FIX)
                .setSaveConsumer(SpaceJoyConfig::setIrisOutlineFix)
                .setTooltip(tooltip(K + "irisOutlineFix.tip", iris, "Iris"))
                .build(), iris));

        if (!polymer) {
            category.addEntry(note(eb, K + "missing.polymer"));
        }
        if (!tacz) {
            category.addEntry(note(eb, K + "missing.tacz"));
        }
        if (!iris) {
            category.addEntry(note(eb, K + "missing.iris"));
        }
    }

    // -------------------------------------------------------------------------------------
    // 操作增强（移植自 tweakeroo）
    // -------------------------------------------------------------------------------------

    /**
     * 六项 tweakeroo 功能，按用途分区。
     *
     * <p>只有自由相机折起来 —— 它自己带五个开关，摊开会把整页顶下去。
     * 其余每个功能区只有一两项，用一行小标题分隔就够了，不套折叠：
     * 为了看一眼设置还得先点开一次，多一层没有意义的操作。
     *
     * <p>全部是模组自己实现的，不依赖 MaLiLib，所以这里一项都不置灰 —— 没装 MaLiLib 也照常能用。
     */
    private static void buildTweaks(ConfigCategory category, ConfigEntryBuilder eb) {
        // ---- 自由相机：唯一折叠的一组 ----
        SubCategoryBuilder camera = eb.startSubCategory(Component.translatable(K + "section.freeCamera"))
                .setExpanded(false);
        // 走 setState 而不是直接写配置：这样在游戏里拨这个开关会立刻建/拆相机，也会弹提示，
        // 和快捷键那条路径完全一致。
        camera.add(toggle(eb, K + "freeCamera", SpaceJoyConfig.isFreeCamera(),
                SpaceJoyConfig.DEFAULT_FREE_CAMERA,
                net.spacejoy.mod.feature.freecam.SpaceJoyFreeCamera::setState, K + "freeCamera.tip"));
        camera.add(toggle(eb, K + "freeCameraPlayerInputs", SpaceJoyConfig.isFreeCameraPlayerInputs(),
                SpaceJoyConfig.DEFAULT_FREE_CAMERA_PLAYER_INPUTS,
                SpaceJoyConfig::setFreeCameraPlayerInputs, K + "freeCameraPlayerInputs.tip"));
        camera.add(toggle(eb, K + "freeCameraShowHands", SpaceJoyConfig.isFreeCameraShowHands(),
                SpaceJoyConfig.DEFAULT_FREE_CAMERA_SHOW_HANDS,
                SpaceJoyConfig::setFreeCameraShowHands, K + "freeCameraShowHands.tip"));
        camera.add(toggle(eb, K + "freeCameraShowHotbar", SpaceJoyConfig.isFreeCameraShowHotbar(),
                SpaceJoyConfig.DEFAULT_FREE_CAMERA_SHOW_HOTBAR,
                SpaceJoyConfig::setFreeCameraShowHotbar, K + "freeCameraShowHotbar.tip"));
        camera.add(toggle(eb, K + "freeCameraShowStatusBars", SpaceJoyConfig.isFreeCameraShowStatusBars(),
                SpaceJoyConfig.DEFAULT_FREE_CAMERA_SHOW_STATUS_BARS,
                SpaceJoyConfig::setFreeCameraShowStatusBars, K + "freeCameraShowStatusBars.tip"));
        category.addEntry(camera.build());

        // ---- 移动 ----
        category.addEntry(section(eb, K + "section.movement"));
        category.addEntry(toggle(eb, K + "permanentSprint", SpaceJoyConfig.isPermanentSprint(),
                SpaceJoyConfig.DEFAULT_PERMANENT_SPRINT,
                SpaceJoyConfig::setPermanentSprint, K + "permanentSprint.tip"));

        // ---- 建造 ----
        category.addEntry(section(eb, K + "section.building"));
        category.addEntry(toggle(eb, K + "angelBlock", SpaceJoyConfig.isAngelBlock(),
                SpaceJoyConfig.DEFAULT_ANGEL_BLOCK,
                SpaceJoyConfig::setAngelBlock, K + "angelBlock.tip"));
        category.addEntry(eb.startIntSlider(
                        Component.translatable(K + "angelBlockPlacementDistance"),
                        SpaceJoyConfig.getAngelBlockPlacementDistance(),
                        SpaceJoyConfig.MIN_ANGEL_BLOCK_DISTANCE, SpaceJoyConfig.MAX_ANGEL_BLOCK_DISTANCE)
                .setDefaultValue(SpaceJoyConfig.DEFAULT_ANGEL_BLOCK_DISTANCE)
                .setTextGetter(v -> Component.translatable(K + "angelBlockPlacementDistance.value", v))
                .setSaveConsumer(SpaceJoyConfig::setAngelBlockPlacementDistance)
                .setTooltip(Component.translatable(K + "angelBlockPlacementDistance.tip"))
                .build());

        // ---- 装备 ----
        category.addEntry(section(eb, K + "section.equipment"));
        category.addEntry(toggle(eb, K + "autoSwitchElytra", SpaceJoyConfig.isAutoSwitchElytra(),
                SpaceJoyConfig.DEFAULT_AUTO_SWITCH_ELYTRA,
                SpaceJoyConfig::setAutoSwitchElytra, K + "autoSwitchElytra.tip"));

        // ---- 界面 ----
        category.addEntry(section(eb, K + "section.interface"));
        category.addEntry(toggle(eb, K + "customInventoryGuiScale", SpaceJoyConfig.isCustomInventoryGuiScale(),
                SpaceJoyConfig.DEFAULT_CUSTOM_INVENTORY_GUI_SCALE,
                SpaceJoyConfig::setCustomInventoryGuiScale, K + "customInventoryGuiScale.tip"));
        category.addEntry(eb.startIntSlider(
                        Component.translatable(K + "customInventoryScale"),
                        SpaceJoyConfig.getCustomInventoryScale(),
                        SpaceJoyConfig.MIN_INVENTORY_SCALE, SpaceJoyConfig.MAX_INVENTORY_SCALE)
                .setDefaultValue(SpaceJoyConfig.DEFAULT_INVENTORY_SCALE)
                .setTextGetter(v -> Component.translatable(K + "customInventoryScale.value", v))
                .setSaveConsumer(SpaceJoyConfig::setCustomInventoryScale)
                .setTooltip(Component.translatable(K + "customInventoryScale.tip"))
                .build());

        // ---- 旁观 ----
        category.addEntry(section(eb, K + "section.spectator"));
        category.addEntry(toggle(eb, K + "spectatorTeleport", SpaceJoyConfig.isSpectatorTeleport(),
                SpaceJoyConfig.DEFAULT_SPECTATOR_TELEPORT,
                SpaceJoyConfig::setSpectatorTeleport, K + "spectatorTeleport.tip"));
    }

    /** 分区小标题：只做视觉分隔，不加折叠。 */
    private static AbstractConfigListEntry section(ConfigEntryBuilder eb, String key) {
        return eb.startTextDescription(Component.translatable(key).withStyle(ChatFormatting.BOLD)).build();
    }

    // -------------------------------------------------------------------------------------
    // 快捷键
    // -------------------------------------------------------------------------------------

    /**
     * 本模组按键的改绑页。
     *
     * <p>改动写回的是原版 {@link net.minecraft.client.KeyMapping}，所以和「选项 → 控制 → 按键绑定」
     * 里那一份是同一套数据，两边随便改哪边都同步；保存时一并写进 {@code options.txt}。
     */
    private static void buildKeys(ConfigCategory category, ConfigEntryBuilder eb) {
        category.addEntry(note(eb, K + "keys.hint"));

        for (net.minecraft.client.KeyMapping mapping : net.spacejoy.mod.client.SpaceJoyKeys.allMappings()) {
            if (mapping == null) {
                continue;
            }
            category.addEntry(eb.startKeyCodeField(
                            Component.translatable(mapping.getName()), mapping.key)
                    // 原版 KeyMapping 不支持组合键，开了只会存下一个用不上的值。
                    .setAllowModifiers(false)
                    .setKeySaveConsumer(key -> net.spacejoy.mod.client.SpaceJoyKeys.applyKey(mapping, key))
                    .setDefaultValue(mapping.getDefaultKey())
                    .build());
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
                .setDefaultValue(SpaceJoyConfig.DEFAULT_JADE_ABILITY_DISPLAY)
                .setSaveConsumer(SpaceJoyConfig::setJadeAbilityDisplay)
                .setTooltip(tooltip(K + "jadeAbilityDisplay.tip", jadeOk, "Jade + Everlasting Abilities"))
                .build(), jadeOk));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "mobLabel"),
                        SpaceJoyConfig.isMobLabel())
                .setDefaultValue(SpaceJoyConfig.DEFAULT_MOB_LABEL)
                .setSaveConsumer(v -> {
                    SpaceJoyConfig.setMobLabel(v);
                    // 打开时顺手把 EA 自己那套会画错的粒子压掉；关掉时不用管，它本来就是关的。
                    if (v && ea) {
                        net.spacejoy.mod.compat.EACompat.suppressEntityParticles();
                    }
                })
                .setTooltip(tooltip(K + "mobLabel.tip", ea, "Everlasting Abilities"))
                .build(), ea));

        // 副手火把保留：不依赖任何前置模组，原版火把就能用，所以不置灰。
        category.addEntry(eb.startBooleanToggle(
                        Component.translatable(K + "offhandTorchGuard"),
                        SpaceJoyConfig.isOffhandTorchGuard())
                .setDefaultValue(SpaceJoyConfig.DEFAULT_OFFHAND_TORCH_GUARD)
                .setSaveConsumer(SpaceJoyConfig::setOffhandTorchGuard)
                .setTooltip(Component.translatable(K + "offhandTorchGuard.tip"))
                .build());

        // 用输入框而不是滑块：-1（永远保留）到 16 之间是 18 个整数，滑块拖动很难停在 -1 上。
        category.addEntry(eb.startIntField(
                        Component.translatable(K + "offhandTorchKeepCount"),
                        SpaceJoyConfig.getOffhandTorchKeepCount())
                .setDefaultValue(SpaceJoyConfig.DEFAULT_TORCH_KEEP)
                .setMin(SpaceJoyConfig.MIN_TORCH_KEEP)
                .setMax(SpaceJoyConfig.MAX_TORCH_KEEP)
                .setSaveConsumer(SpaceJoyConfig::setOffhandTorchKeepCount)
                .setTooltip(Component.translatable(K + "offhandTorchKeepCount.tip"))
                .build());

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
                        SpaceJoyServerConfig.isEliteAbilityDrop())
                .setDefaultValue(SpaceJoyServerConfig.DEFAULT_ELITE_ABILITY_DROP)
                .setSaveConsumer(v -> SpaceJoyClientNetwork.sendConfig("eliteAbilityDrop", String.valueOf(v)))
                .setTooltip(tooltip(K + "eliteAbilityDrop.tip", ok, "BuffMobs + Everlasting Abilities"))
                .build(), ok && SpaceJoyServerConfig.canEdit()));

        category.addEntry(gate(eb.startEnumSelector(
                        Component.translatable(K + "eliteAbilityMinRarity"),
                        Rarity.class, SpaceJoyServerConfig.getEliteAbilityMinRarity())
                .setDefaultValue(Rarity.RARE)
                .setEnumNameProvider(v -> Component.translatable(
                        K + "eliteAbilityMinRarity." + ((Enum<?>) v).name()))
                .setSaveConsumer(v -> SpaceJoyClientNetwork.sendConfig("eliteAbilityMinRarity", ((Enum<?>) v).name()))
                .setTooltip(tooltip(K + "eliteAbilityMinRarity.tip", ok, "BuffMobs + Everlasting Abilities"))
                .build(), ok && SpaceJoyServerConfig.canEdit()));

        if (!buffMobs) {
            category.addEntry(note(eb, K + "missing.buffmobs"));
        }
        if (!ea) {
            category.addEntry(note(eb, K + "missing.everlastingabilities"));
        }
    }

    // -------------------------------------------------------------------------------------
    // 服务端（新增第 8 页）
    // -------------------------------------------------------------------------------------

    /**
     * 服务端权威设置：这些开关改的是「专用服务器上真正生效的那份」。
     * 非 OP 玩家进来时界面整页置灰（只是显示），服务端收 C2S 时还会用 PlayerList.isOp 重新鉴权。
     */
    private static void buildServer(ConfigCategory category, ConfigEntryBuilder eb) {
        boolean canEdit = SpaceJoyServerConfig.canEdit();
        category.addEntry(note(eb, K + "server.hint"));
        if (!canEdit) {
            category.addEntry(note(eb, K + "server.notOp"));
        }
        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "waypointTeleport"),
                        SpaceJoyServerConfig.isWaypointEnabled())
                .setDefaultValue(SpaceJoyServerConfig.DEFAULT_WAYPOINT_ENABLED)
                .setSaveConsumer(v -> SpaceJoyClientNetwork.sendConfig("waypointEnabled", String.valueOf(v)))
                .setTooltip(Component.translatable(K + "waypointTeleport.tip"))
                .build(), canEdit));
        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "quietDisconnectEncodeError"),
                        SpaceJoyServerConfig.isQuietDisconnectEncodeError())
                .setDefaultValue(SpaceJoyServerConfig.DEFAULT_QUIET_DISCONNECT_ENCODE_ERROR)
                .setSaveConsumer(v -> SpaceJoyClientNetwork.sendConfig(
                        "quietDisconnectEncodeError", String.valueOf(v)))
                .setTooltip(Component.translatable(K + "quietDisconnectEncodeError.tip"))
                .build(), canEdit));
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
                .setDefaultValue(SpaceJoyConfig.DEFAULT_IRIS_DIMENSION_SHADERS)
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
        // 默认值直接取一个刚构造出来的实例：ModSettings 的默认值就写在字段初始化里，
        // 这样界面的「重置」按钮永远不会和真正的默认值对不上，也不用再维护一份常量。
        ModSettings defaults = new ModSettings();
        boolean on = iris;

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.applyOnWorldEnter"), settings.applyOnWorldEnter)
                .setDefaultValue(defaults.applyOnWorldEnter)
                .setSaveConsumer(v -> settings.applyOnWorldEnter = v)
                .setTooltip(tooltip(K + "iris.applyOnWorldEnter.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.applyOnDimensionChange"), settings.applyOnDimensionChange)
                .setDefaultValue(defaults.applyOnDimensionChange)
                .setSaveConsumer(v -> settings.applyOnDimensionChange = v)
                .setTooltip(tooltip(K + "iris.applyOnDimensionChange.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.applyDuringLevelLoading"), settings.applyDuringLevelLoading)
                .setDefaultValue(defaults.applyDuringLevelLoading)
                .setSaveConsumer(v -> settings.applyDuringLevelLoading = v)
                .setTooltip(tooltip(K + "iris.applyDuringLevelLoading.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.restoreOnWorldExit"), settings.restoreOnWorldExit)
                .setDefaultValue(defaults.restoreOnWorldExit)
                .setSaveConsumer(v -> settings.restoreOnWorldExit = v)
                .setTooltip(tooltip(K + "iris.restoreOnWorldExit.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.reloadOnlyWhenChanged"), settings.reloadOnlyWhenChanged)
                .setDefaultValue(defaults.reloadOnlyWhenChanged)
                .setSaveConsumer(v -> settings.reloadOnlyWhenChanged = v)
                .setTooltip(tooltip(K + "iris.reloadOnlyWhenChanged.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.trackDiscoveredDimensions"),
                        settings.trackDiscoveredDimensions)
                .setDefaultValue(defaults.trackDiscoveredDimensions)
                .setSaveConsumer(v -> settings.trackDiscoveredDimensions = v)
                .setTooltip(tooltip(K + "iris.trackDiscoveredDimensions.tip", on, "Iris"))
                .build(), on));

        category.addEntry(gate(eb.startBooleanToggle(
                        Component.translatable(K + "iris.announceInChat"), settings.announceInChat)
                .setDefaultValue(defaults.announceInChat)
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
                                                  boolean defaultValue,
                                                  java.util.function.Consumer<Boolean> setter, String tipKey) {
        return eb.startBooleanToggle(Component.translatable(key), value)
                // 这里必须是该项真正的默认值：Cloth 的重置按钮是拿「当前值」和它比对来决定灰不灰的，
                // 写死 true 的话，默认关闭的项会反过来在「是」的时候变灰。
                .setDefaultValue(defaultValue)
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
