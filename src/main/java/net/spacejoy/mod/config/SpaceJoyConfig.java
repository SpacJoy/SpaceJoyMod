package net.spacejoy.mod.config;

import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.spacejoy.mod.SpaceJoyMod;

/**
 * SpaceJoyMod 的运行时配置。
 *
 * <p>文件放在 {@code config/spacejoy-mod.json}，用 ModMenu 的配置界面改，改完立刻生效。
 *
 * <p>开关分组：
 * <ul>
 *   <li>通用：总开关、日志</li>
 *   <li>修复：polymer 配方编码修复</li>
 *   <li>功能：Jade 能力显示、带能力生物的头顶标签</li>
 *   <li>维度光影：Iris 按维度切换光影包（规则本身在 {@code config/spacejoy-mod/irisdim/config.json}）</li>
 * </ul>
 */
public final class SpaceJoyConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "spacejoy-mod.json";

    private static boolean loaded;

    // ---- 默认值 ----
    // 字段初始化和配置界面的「重置」按钮都读这里，只维护一份。
    // Cloth 的重置按钮是拿「当前值」和「默认值」比对来决定灰不灰的，两边对不上就会出现
    // 「本来默认关闭的项，反而在开启状态下按钮变灰」这种反向表现。
    public static final boolean DEFAULT_ENABLED = true;
    public static final boolean DEFAULT_DEBUG_LOG = true;
    public static final boolean DEFAULT_POLYMER_RECIPE_FIX = true;
    public static final boolean DEFAULT_TACZ_CONCURRENCY_FIX = true;
    public static final boolean DEFAULT_IRIS_OUTLINE_FIX = false;
    public static final boolean DEFAULT_JADE_ABILITY_DISPLAY = true;
    public static final boolean DEFAULT_MOB_LABEL = true;
    /**
     * 头顶标签的可见距离默认值：跟原版名字下方那一行（计分板 {@code BELOW_NAME}）对齐成 10 格。
     *
     * <p>不是 64：标签挂在名字下方那一格，原版那一格本身就是给「近处才看」的信息准备的
     * （{@code Attributes.BELOW_NAME_DISTANCE} 默认 10）。名字栏 64 格是另一回事，
     * 我们并不占它。
     */
    public static final int DEFAULT_MOB_LABEL_DISTANCE = 10;
    /**
     * 距离下限 4 格：再近就只剩贴脸才看得见，等于把标签关掉了。
     */
    public static final int MIN_MOB_LABEL_DISTANCE = 4;
    /**
     * 距离上限 64 格：原版名字栏（{@code Attributes.NAME_TAG_DISTANCE}）的默认值。
     * 再远受实体渲染距离限制，调了也不会更远，只会白给每帧多算几帧标签。
     */
    public static final int MAX_MOB_LABEL_DISTANCE = 64;
    public static final boolean DEFAULT_IRIS_DIMENSION_SHADERS = true;
    public static final boolean DEFAULT_ELITE_ABILITY_DROP = true;
    public static final boolean DEFAULT_OFFHAND_TORCH_GUARD = false;
    public static final boolean DEFAULT_FREE_CAMERA = false;
    public static final boolean DEFAULT_FREE_CAMERA_PLAYER_INPUTS = false;
    public static final boolean DEFAULT_FREE_CAMERA_SHOW_HANDS = false;
    public static final boolean DEFAULT_FREE_CAMERA_SHOW_HOTBAR = true;
    public static final boolean DEFAULT_FREE_CAMERA_SHOW_STATUS_BARS = true;
    public static final boolean DEFAULT_PERMANENT_SPRINT = false;
    public static final boolean DEFAULT_ANGEL_BLOCK = false;
    public static final boolean DEFAULT_AUTO_SWITCH_ELYTRA = false;
    public static final boolean DEFAULT_CUSTOM_INVENTORY_GUI_SCALE = false;
    public static final boolean DEFAULT_SPECTATOR_TELEPORT = false;
    public static final boolean DEFAULT_RECIPE_BOOK_SCROLL = true;
    public static final boolean DEFAULT_CHAT_HISTORY = true;
    /** 隐藏 ProjectEF 盔甲：纯客户端渲染开关，默认关。 */
    public static final boolean DEFAULT_HIDE_PROJECTE_ARMOR = false;
    /** 聊天记录上限的默认值：与原 More Chat History 的 16384 一致。 */
    public static final int DEFAULT_CHAT_HISTORY_LENGTH = 16384;
    /** 下限就是原版自己的 100，再小等于把记录砍到比原版还短，没有意义。 */
    public static final int MIN_CHAT_HISTORY_LENGTH = 100;
    public static final int MAX_CHAT_HISTORY_LENGTH = 16384;

    private static boolean enabled = DEFAULT_ENABLED;
    private static boolean debugLog = DEFAULT_DEBUG_LOG;
    private static boolean polymerRecipeFix = DEFAULT_POLYMER_RECIPE_FIX;
    private static boolean taczConcurrencyFix = DEFAULT_TACZ_CONCURRENCY_FIX;
    private static boolean irisOutlineFix = DEFAULT_IRIS_OUTLINE_FIX;
    private static boolean jadeAbilityDisplay = DEFAULT_JADE_ABILITY_DISPLAY;
    private static boolean mobLabel = DEFAULT_MOB_LABEL;
    /** 头顶标签的可见距离（格）。超出就不画，也不进缓存。 */
    private static int mobLabelDistance = DEFAULT_MOB_LABEL_DISTANCE;
    private static boolean irisDimensionShaders = DEFAULT_IRIS_DIMENSION_SHADERS;
    private static boolean eliteAbilityDrop = DEFAULT_ELITE_ABILITY_DROP;
    private static String eliteAbilityMinRarity = "RARE";

    /** 副手火把保留数哨兵：-1 = 整摞都不许从副手放置（永远留火把）；0 = 不拦。 */
    public static final int TORCH_KEEP_ALWAYS = -1;
    /** 副手火把保留数的默认值：-1（整摞都不许从副手放置）。功能默认关闭，打开时按这个最保守的值来。 */
    public static final int DEFAULT_TORCH_KEEP = TORCH_KEEP_ALWAYS;
    /** 保留数上限。再高就等于「副手永远不放火把」，不如直接关掉功能。 */
    public static final int MAX_TORCH_KEEP = 16;
    /** 保留数下限：只允许 -1（永远保留）这一种负值。 */
    public static final int MIN_TORCH_KEEP = TORCH_KEEP_ALWAYS;

    private static boolean offhandTorchGuard = DEFAULT_OFFHAND_TORCH_GUARD;
    private static int offhandTorchKeepCount = DEFAULT_TORCH_KEEP;

    // ---- 自由相机（移植自 tweakeroo 的 tweakFreeCamera 及其关联设置）----
    private static boolean freeCamera = DEFAULT_FREE_CAMERA;
    /** 自由相机开着时，本体还能不能操作（挖方块、右键、攻击）。默认不能。 */
    private static boolean freeCameraPlayerInputs = DEFAULT_FREE_CAMERA_PLAYER_INPUTS;
    /**
     * 自由相机开着时第一人称手默认不画：相机已经飞出去了，画面里还挂着一双站在原地的胳膊
     * 没有意义。快捷栏和状态栏仍要留着，那是玩家判断自身状态的信息。
     */
    private static boolean freeCameraShowHands = DEFAULT_FREE_CAMERA_SHOW_HANDS;
    private static boolean freeCameraShowHotbar = DEFAULT_FREE_CAMERA_SHOW_HOTBAR;
    private static boolean freeCameraShowStatusBars = DEFAULT_FREE_CAMERA_SHOW_STATUS_BARS;

    /** 浮空放置方块（Angel Block）的落点距离默认值，对应 tweakeroo 的 angelBlockPlacementDistance。 */
    public static final int DEFAULT_ANGEL_BLOCK_DISTANCE = 3;
    public static final int MIN_ANGEL_BLOCK_DISTANCE = 1;
    public static final int MAX_ANGEL_BLOCK_DISTANCE = 5;

    /** 背包界面缩放的默认值与取值范围，对应 tweakeroo 的 customInventoryGuiScale。 */
    public static final int DEFAULT_INVENTORY_SCALE = 2;
    public static final int MIN_INVENTORY_SCALE = 1;
    public static final int MAX_INVENTORY_SCALE = 10;

    // ---- 其它 tweak 移植 ----
    private static boolean permanentSprint = DEFAULT_PERMANENT_SPRINT;
    private static boolean angelBlock = DEFAULT_ANGEL_BLOCK;
    private static int angelBlockPlacementDistance = DEFAULT_ANGEL_BLOCK_DISTANCE;
    private static boolean autoSwitchElytra = DEFAULT_AUTO_SWITCH_ELYTRA;
    private static boolean customInventoryGuiScale = DEFAULT_CUSTOM_INVENTORY_GUI_SCALE;
    private static int customInventoryScale = DEFAULT_INVENTORY_SCALE;
    private static boolean spectatorTeleport = DEFAULT_SPECTATOR_TELEPORT;
    private static boolean recipeBookScroll = DEFAULT_RECIPE_BOOK_SCROLL;

    // ---- 聊天记录长度（移植自 More Chat History）----
    private static boolean chatHistory = DEFAULT_CHAT_HISTORY;
    private static int chatHistoryLength = DEFAULT_CHAT_HISTORY_LENGTH;

    // ---- 隐藏 ProjectEF 盔甲 ----
    private static boolean hideProjectEArmor = DEFAULT_HIDE_PROJECTE_ARMOR;

    private SpaceJoyConfig() {
    }

    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = path();
        if (!Files.isRegularFile(path)) {
            // 第一次运行就把默认值落盘，玩家能直接看到有哪些开关，不用先进游戏开界面。
            save();
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            enabled = readBool(root, "enabled", enabled);
            debugLog = readBool(root, "debugLog", debugLog);
            polymerRecipeFix = readBool(root, "polymerRecipeFix", polymerRecipeFix);
            taczConcurrencyFix = readBool(root, "taczConcurrencyFix", taczConcurrencyFix);
            irisOutlineFix = readBool(root, "irisOutlineFix", irisOutlineFix);
            jadeAbilityDisplay = readBool(root, "jadeAbilityDisplay", jadeAbilityDisplay);
            mobLabel = readBool(root, "mobLabel", mobLabel);
            mobLabelDistance = clampMobLabelDistance(readInt(root, "mobLabelDistance", mobLabelDistance));
            irisDimensionShaders = readBool(root, "irisDimensionShaders", irisDimensionShaders);
            eliteAbilityDrop = readBool(root, "eliteAbilityDrop", eliteAbilityDrop);
            eliteAbilityMinRarity = readString(root, "eliteAbilityMinRarity", eliteAbilityMinRarity);
            offhandTorchGuard = readBool(root, "offhandTorchGuard", offhandTorchGuard);
            offhandTorchKeepCount = clampKeep(readInt(root, "offhandTorchKeepCount", offhandTorchKeepCount));
            freeCamera = readBool(root, "freeCamera", freeCamera);
            freeCameraPlayerInputs = readBool(root, "freeCameraPlayerInputs", freeCameraPlayerInputs);
            freeCameraShowHands = readBool(root, "freeCameraShowHands", freeCameraShowHands);
            freeCameraShowHotbar = readBool(root, "freeCameraShowHotbar", freeCameraShowHotbar);
            freeCameraShowStatusBars = readBool(root, "freeCameraShowStatusBars", freeCameraShowStatusBars);
            permanentSprint = readBool(root, "permanentSprint", permanentSprint);
            angelBlock = readBool(root, "angelBlock", angelBlock);
            angelBlockPlacementDistance = clampDistance(readInt(root, "angelBlockPlacementDistance", angelBlockPlacementDistance));
            autoSwitchElytra = readBool(root, "autoSwitchElytra", autoSwitchElytra);
            customInventoryGuiScale = readBool(root, "customInventoryGuiScale", customInventoryGuiScale);
            customInventoryScale = clampScale(readInt(root, "customInventoryScale", customInventoryScale));
            spectatorTeleport = readBool(root, "spectatorTeleport", spectatorTeleport);
            recipeBookScroll = readBool(root, "recipeBookScroll", recipeBookScroll);
            chatHistory = readBool(root, "chatHistory", chatHistory);
            chatHistoryLength = clampChatHistory(readInt(root, "chatHistoryLength", chatHistoryLength));
            hideProjectEArmor = readBool(root, "hideProjectEArmor", hideProjectEArmor);
        } catch (Exception e) {
            // 配置文件坏了就退回默认值，不要让模组起不来。
            SpaceJoyMod.LOGGER.warn("spacejoy-mod config unreadable, using defaults", e);
        }
    }

    public static void save() {
        ensureLoaded();
        JsonObject root = new JsonObject();
        root.addProperty("enabled", enabled);
        root.addProperty("debugLog", debugLog);
        root.addProperty("polymerRecipeFix", polymerRecipeFix);
        root.addProperty("taczConcurrencyFix", taczConcurrencyFix);
        root.addProperty("irisOutlineFix", irisOutlineFix);
        root.addProperty("jadeAbilityDisplay", jadeAbilityDisplay);
        root.addProperty("mobLabel", mobLabel);
        root.addProperty("mobLabelDistance", mobLabelDistance);
        root.addProperty("irisDimensionShaders", irisDimensionShaders);
        root.addProperty("eliteAbilityDrop", eliteAbilityDrop);
        root.addProperty("eliteAbilityMinRarity", eliteAbilityMinRarity);
        root.addProperty("offhandTorchGuard", offhandTorchGuard);
        root.addProperty("offhandTorchKeepCount", offhandTorchKeepCount);
        root.addProperty("freeCamera", freeCamera);
        root.addProperty("freeCameraPlayerInputs", freeCameraPlayerInputs);
        root.addProperty("freeCameraShowHands", freeCameraShowHands);
        root.addProperty("freeCameraShowHotbar", freeCameraShowHotbar);
        root.addProperty("freeCameraShowStatusBars", freeCameraShowStatusBars);
        root.addProperty("permanentSprint", permanentSprint);
        root.addProperty("angelBlock", angelBlock);
        root.addProperty("angelBlockPlacementDistance", angelBlockPlacementDistance);
        root.addProperty("autoSwitchElytra", autoSwitchElytra);
        root.addProperty("customInventoryGuiScale", customInventoryGuiScale);
        root.addProperty("customInventoryScale", customInventoryScale);
        root.addProperty("spectatorTeleport", spectatorTeleport);
        root.addProperty("recipeBookScroll", recipeBookScroll);
        root.addProperty("chatHistory", chatHistory);
        root.addProperty("chatHistoryLength", chatHistoryLength);
        root.addProperty("hideProjectEArmor", hideProjectEArmor);
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(root));
        } catch (Exception e) {
            SpaceJoyMod.LOGGER.warn("failed to save spacejoy-mod config", e);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static boolean readBool(JsonObject root, String key, boolean fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsBoolean() : fallback;
    }

    private static String readString(JsonObject root, String key, String fallback) {
        return root.has(key) && root.get(key).isJsonPrimitive() ? root.get(key).getAsString() : fallback;
    }

    private static int readInt(JsonObject root, String key, int fallback) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return root.get(key).getAsInt();
        } catch (RuntimeException e) {
            // 手改成 "abc" 之类的就退回默认值，不让整个配置读失败。
            return fallback;
        }
    }

    /**
     * 保留数夹到合法区间。
     *
     * <p>-1 是「永远保留」的哨兵值，原样放过；其它负数一律当成 -1；
     * 正数夹到 0（等于不保留）到 {@link #MAX_TORCH_KEEP} 之间。
     */
    private static int clampKeep(int value) {
        if (value < 0) {
            return TORCH_KEEP_ALWAYS;
        }
        return Math.min(MAX_TORCH_KEEP, value);
    }

    /** 浮空放置距离夹到 1~5 格，和 tweakeroo 的取值范围一致。 */
    private static int clampDistance(int value) {
        return Math.max(MIN_ANGEL_BLOCK_DISTANCE, Math.min(MAX_ANGEL_BLOCK_DISTANCE, value));
    }

    /** 背包缩放夹到 1~10。 */
    private static int clampScale(int value) {
        return Math.max(MIN_INVENTORY_SCALE, Math.min(MAX_INVENTORY_SCALE, value));
    }

    /** 标签距离夹到 4~64 格，手改成 0 或 500 也不会把标签彻底关掉或撑到没有意义。 */
    private static int clampMobLabelDistance(int value) {
        return Math.max(MIN_MOB_LABEL_DISTANCE, Math.min(MAX_MOB_LABEL_DISTANCE, value));
    }

    /** 聊天记录上限夹到 100~16384，手改成 0 或负数也不会把记录清空。 */
    private static int clampChatHistory(int value) {
        return Math.max(MIN_CHAT_HISTORY_LENGTH, Math.min(MAX_CHAT_HISTORY_LENGTH, value));
    }

    private static void ensureLoaded() {
        load();
    }

    /** 总开关：关掉后所有修复和功能都不介入。 */
    public static boolean isEnabled() {
        ensureLoaded();
        return enabled;
    }

    public static void setEnabled(boolean value) {
        ensureLoaded();
        enabled = value;
    }

    /** 日志开关：关掉后模组自己不再打 INFO 日志。 */
    public static boolean isDebugLog() {
        ensureLoaded();
        return debugLog;
    }

    public static void setDebugLog(boolean value) {
        ensureLoaded();
        debugLog = value;
    }

    /** 修复：polymer ingredient 编解码在无包上下文时退回原版编码。 */
    public static boolean isPolymerRecipeFix() {
        ensureLoaded();
        return polymerRecipeFix;
    }

    public static void setPolymerRecipeFix(boolean value) {
        ensureLoaded();
        polymerRecipeFix = value;
    }

    /**
     * 修复：TaCZ 的 {@code DataHolder.dataMap} 由裸 {@code HashMap} 换成 {@code ConcurrentHashMap}，
     * 避免服务端 tick 线程与实体 NBT 读写线程撞车时抛 {@code ConcurrentModificationException} 崩游戏。
     *
     * <p>只跟 {@code isEnabled()} 取与 —— 不硬绑 TaCZ 是否安装：混入本身由
     * {@code SpaceJoyMixinPlugin} 按 {@code isModLoaded("tacz")} 把关，没装时这段代码根本不进 JVM。
     */
    public static boolean isTaczConcurrencyFix() {
        ensureLoaded();
        return taczConcurrencyFix;
    }

    public static void setTaczConcurrencyFix(boolean value) {
        ensureLoaded();
        taczConcurrencyFix = value;
    }

    /**
     * 修复：补齐 Iris 漏掉的 outline 渲染管线映射，并静音 entity_outline 后处理链的假报错。
     *
     * <p><b>默认关闭。</b>带能力生物的效果已经改成走原版命名牌渲染，不依赖描边，所以这项只在
     * 确实想用原版发光效果（光灵箭、{@code /glowing}、队伍颜色）时才需要打开。
     * 打开后 Iris 才会把两条 outline 管线映射进光影管线表，并静音 {@code entity_outline}
     * 后处理链那几条假报错；关着的话 Iris 会照旧打 {@code Missing program ... outline_cull}
     * 的 fatal 日志，开光影时原版发光描边也退回原版管线。
     */
    public static boolean isIrisOutlineFix() {
        ensureLoaded();
        return irisOutlineFix;
    }

    public static void setIrisOutlineFix(boolean value) {
        ensureLoaded();
        irisOutlineFix = value;
    }

    /** 功能：Jade 里显示生物可掉落的能力。 */
    public static boolean isJadeAbilityDisplay() {
        ensureLoaded();
        return jadeAbilityDisplay;
    }

    public static void setJadeAbilityDisplay(boolean value) {
        ensureLoaded();
        jadeAbilityDisplay = value;
    }

    /** 功能：给带能力掉落的生物加头顶标签（走原版命名牌通道，不用发光描边）。 */
    public static boolean isMobLabel() {
        ensureLoaded();
        return mobLabel;
    }

    public static void setMobLabel(boolean value) {
        ensureLoaded();
        mobLabel = value;
    }

    /** 头顶标签的可见距离（格），默认 10 —— 和原版名字下方那一行一致。 */
    public static int getMobLabelDistance() {
        ensureLoaded();
        return mobLabelDistance;
    }

    public static void setMobLabelDistance(int value) {
        ensureLoaded();
        mobLabelDistance = clampMobLabelDistance(value);
    }

    /** 功能：Iris 按维度切换光影包。 */
    public static boolean isIrisDimensionShaders() {
        ensureLoaded();
        return irisDimensionShaders;
    }

    public static void setIrisDimensionShaders(boolean value) {
        ensureLoaded();
        irisDimensionShaders = value;
    }

    /** 功能：BuffMobs 精英生物必掉一个高稀有度能力图腾。 */
    public static boolean isEliteAbilityDrop() {
        ensureLoaded();
        return eliteAbilityDrop;
    }

    public static void setEliteAbilityDrop(boolean value) {
        ensureLoaded();
        eliteAbilityDrop = value;
    }

    /** 副手火把保护：副手火把数量降到保留数就不再从副手放置。 */
    public static boolean isOffhandTorchGuard() {
        ensureLoaded();
        return offhandTorchGuard;
    }

    public static void setOffhandTorchGuard(boolean value) {
        ensureLoaded();
        offhandTorchGuard = value;
    }

    /** 副手至少要留下的火把数；0 表示不保留（等于关掉这条限制）。 */
    public static int getOffhandTorchKeepCount() {
        ensureLoaded();
        return offhandTorchKeepCount;
    }

    public static void setOffhandTorchKeepCount(int value) {
        ensureLoaded();
        offhandTorchKeepCount = clampKeep(value);
    }

    /** 精英掉落的最低稀有度，存名字（COMMON / UNCOMMON / RARE / EPIC），读的时候再解析。 */
    public static String getEliteAbilityMinRarityName() {
        ensureLoaded();
        return eliteAbilityMinRarity;
    }

    public static void setEliteAbilityMinRarityName(String value) {
        ensureLoaded();
        eliteAbilityMinRarity = value == null ? "RARE" : value;
    }

    /**
     * 精英掉落的最低稀有度。
     *
     * <p>配置文件是手改得动的，所以这里容错：认不出来的名字退回 RARE，不抛异常。
     */
    public static net.minecraft.world.item.Rarity getEliteAbilityMinRarity() {
        ensureLoaded();
        for (net.minecraft.world.item.Rarity rarity : net.minecraft.world.item.Rarity.values()) {
            if (rarity.name().equalsIgnoreCase(eliteAbilityMinRarity)) {
                return rarity;
            }
        }
        return net.minecraft.world.item.Rarity.RARE;
    }

    // ---- 自由相机 ----

    public static boolean isFreeCamera() {
        ensureLoaded();
        return enabled && freeCamera;
    }

    public static void setFreeCamera(boolean value) {
        ensureLoaded();
        freeCamera = value;
    }

    public static boolean isFreeCameraPlayerInputs() {
        ensureLoaded();
        return freeCameraPlayerInputs;
    }

    public static void setFreeCameraPlayerInputs(boolean value) {
        ensureLoaded();
        freeCameraPlayerInputs = value;
    }

    public static boolean isFreeCameraShowHands() {
        ensureLoaded();
        return freeCameraShowHands;
    }

    public static void setFreeCameraShowHands(boolean value) {
        ensureLoaded();
        freeCameraShowHands = value;
    }

    public static boolean isFreeCameraShowHotbar() {
        ensureLoaded();
        return freeCameraShowHotbar;
    }

    public static void setFreeCameraShowHotbar(boolean value) {
        ensureLoaded();
        freeCameraShowHotbar = value;
    }

    public static boolean isFreeCameraShowStatusBars() {
        ensureLoaded();
        return freeCameraShowStatusBars;
    }

    public static void setFreeCameraShowStatusBars(boolean value) {
        ensureLoaded();
        freeCameraShowStatusBars = value;
    }

    // ---- 其它 tweak ----

    public static boolean isPermanentSprint() {
        ensureLoaded();
        return enabled && permanentSprint;
    }

    public static void setPermanentSprint(boolean value) {
        ensureLoaded();
        permanentSprint = value;
    }

    public static boolean isAngelBlock() {
        ensureLoaded();
        return enabled && angelBlock;
    }

    public static void setAngelBlock(boolean value) {
        ensureLoaded();
        angelBlock = value;
    }

    public static int getAngelBlockPlacementDistance() {
        ensureLoaded();
        return angelBlockPlacementDistance;
    }

    public static void setAngelBlockPlacementDistance(int value) {
        ensureLoaded();
        angelBlockPlacementDistance = clampDistance(value);
    }

    public static boolean isAutoSwitchElytra() {
        ensureLoaded();
        return enabled && autoSwitchElytra;
    }

    public static void setAutoSwitchElytra(boolean value) {
        ensureLoaded();
        autoSwitchElytra = value;
    }

    public static boolean isCustomInventoryGuiScale() {
        ensureLoaded();
        return enabled && customInventoryGuiScale;
    }

    public static void setCustomInventoryGuiScale(boolean value) {
        ensureLoaded();
        customInventoryGuiScale = value;
    }

    public static int getCustomInventoryScale() {
        ensureLoaded();
        return customInventoryScale;
    }

    public static void setCustomInventoryScale(int value) {
        ensureLoaded();
        customInventoryScale = clampScale(value);
    }

    public static boolean isSpectatorTeleport() {
        ensureLoaded();
        return enabled && spectatorTeleport;
    }

    public static void setSpectatorTeleport(boolean value) {
        ensureLoaded();
        spectatorTeleport = value;
    }

    /** 功能：在配方书区域滚轮翻页。 */
    public static boolean isRecipeBookScroll() {
        ensureLoaded();
        return enabled && recipeBookScroll;
    }

    public static void setRecipeBookScroll(boolean value) {
        ensureLoaded();
        recipeBookScroll = value;
    }

    /** 功能：把聊天记录（聊天框里的历史消息 + 输入框的上键历史）上限从 100 条放宽。 */
    public static boolean isChatHistory() {
        ensureLoaded();
        return enabled && chatHistory;
    }

    public static void setChatHistory(boolean value) {
        ensureLoaded();
        chatHistory = value;
    }

    /** 聊天记录上限。混入只在 {@link #isChatHistory()} 为真时才用它。 */
    public static int getChatHistoryLength() {
        ensureLoaded();
        return clampChatHistory(chatHistoryLength);
    }

    public static void setChatHistoryLength(int value) {
        ensureLoaded();
        chatHistoryLength = clampChatHistory(value);
    }

    /**
     * 功能：不渲染 ProjectEF 的盔甲。
     *
     * <p>纯客户端，只影响自己看到的画面，不改装备槽里的东西，也不影响减伤计算。
     * 判定按物品命名空间是否为 {@code projecte}，其它模组的盔甲不受影响。
     */
    public static boolean isHideProjectEArmor() {
        ensureLoaded();
        return enabled && hideProjectEArmor;
    }

    public static void setHideProjectEArmor(boolean value) {
        ensureLoaded();
        hideProjectEArmor = value;
    }
}
