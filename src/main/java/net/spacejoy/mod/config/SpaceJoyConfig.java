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

    private static boolean enabled = true;
    private static boolean debugLog = true;
    private static boolean polymerRecipeFix = true;
    private static boolean irisOutlineFix = false;
    private static boolean jadeAbilityDisplay = true;
    private static boolean mobLabel = true;
    private static boolean irisDimensionShaders = true;
    private static boolean eliteAbilityDrop = true;
    private static String eliteAbilityMinRarity = "RARE";

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
            irisOutlineFix = readBool(root, "irisOutlineFix", irisOutlineFix);
            jadeAbilityDisplay = readBool(root, "jadeAbilityDisplay", jadeAbilityDisplay);
            mobLabel = readBool(root, "mobLabel", mobLabel);
            irisDimensionShaders = readBool(root, "irisDimensionShaders", irisDimensionShaders);
            eliteAbilityDrop = readBool(root, "eliteAbilityDrop", eliteAbilityDrop);
            eliteAbilityMinRarity = readString(root, "eliteAbilityMinRarity", eliteAbilityMinRarity);
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
        root.addProperty("irisOutlineFix", irisOutlineFix);
        root.addProperty("jadeAbilityDisplay", jadeAbilityDisplay);
        root.addProperty("mobLabel", mobLabel);
        root.addProperty("irisDimensionShaders", irisDimensionShaders);
        root.addProperty("eliteAbilityDrop", eliteAbilityDrop);
        root.addProperty("eliteAbilityMinRarity", eliteAbilityMinRarity);
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
}
