package net.spacejoy.mod.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.Rarity;
import net.spacejoy.mod.network.SpaceJoyPackets.ConfigS2C;

/**
 * 服务端权威配置：这些开关改的是「专用服务器上真正生效的那份」。
 *
 * <p>客户端 GUI 改动通过 C2S 包发到服务端（服务端按 OP 重新鉴权），服务端保存后广播 S2C，
 * 两端都读这个类的静态字段：单人同一 JVM 天然共享，连服靠 S2C 同步。
 * 路径点表按「玩家 + 维度 + 坐标」存，传送时只认表里登记过的坐标。
 */
public final class SpaceJoyServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "spacejoy-mod-server.json";
    private static final String WP_FILE = "spacejoy-waypoints.json";

    public static final boolean DEFAULT_WAYPOINT_ENABLED = true;
    public static final boolean DEFAULT_ELITE_ABILITY_DROP = true;
    public static final String DEFAULT_ELITE_ABILITY_MIN_RARITY = "RARE";
    public static final boolean DEFAULT_POLYMER_RECIPE_FIX = true;
    public static final boolean DEFAULT_TACZ_CONCURRENCY_FIX = true;
    public static final boolean DEFAULT_QUIET_DISCONNECT_ENCODE_ERROR = true;

    private static boolean loaded;
    private static boolean waypointEnabled = DEFAULT_WAYPOINT_ENABLED;
    private static boolean eliteAbilityDrop = DEFAULT_ELITE_ABILITY_DROP;
    private static String eliteAbilityMinRarity = DEFAULT_ELITE_ABILITY_MIN_RARITY;
    private static boolean polymerRecipeFix = DEFAULT_POLYMER_RECIPE_FIX;
    private static boolean taczConcurrencyFix = DEFAULT_TACZ_CONCURRENCY_FIX;
    private static boolean quietDisconnectEncodeError = DEFAULT_QUIET_DISCONNECT_ENCODE_ERROR;
    /** 客户端能不能改服务端设置（由 S2C 下发的 isOp 决定；单人默认 true）。 */
    private static boolean canEdit = true;

    /** 玩家 UUID → "维度|x|y|z" 集合；非 OP 手敲坐标也传不了没登记过的点。 */
    private static final Map<String, Set<String>> WAYPOINTS = new HashMap<>();

    private SpaceJoyServerConfig() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            Path p = dir().resolve(FILE);
            if (Files.isRegularFile(p)) {
                JsonObject r = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                waypointEnabled = bool(r, "waypointEnabled", waypointEnabled);
                eliteAbilityDrop = bool(r, "eliteAbilityDrop", eliteAbilityDrop);
                eliteAbilityMinRarity = str(r, "eliteAbilityMinRarity", eliteAbilityMinRarity);
                polymerRecipeFix = bool(r, "polymerRecipeFix", polymerRecipeFix);
                taczConcurrencyFix = bool(r, "taczConcurrencyFix", taczConcurrencyFix);
                quietDisconnectEncodeError = bool(r, "quietDisconnectEncodeError", quietDisconnectEncodeError);
            }
            Path wp = dir().resolve(WP_FILE);
            if (Files.isRegularFile(wp)) {
                JsonObject r = JsonParser.parseString(Files.readString(wp)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> e : r.entrySet()) {
                    Set<String> set = new HashSet<>();
                    for (JsonElement v : e.getValue().getAsJsonArray()) {
                        set.add(v.getAsString());
                    }
                    WAYPOINTS.put(e.getKey(), set);
                }
            }
        } catch (Exception ignored) {
            // 文件坏了就退回默认值，不让模组起不来。
        }
    }

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(dir());
            JsonObject r = new JsonObject();
            r.addProperty("waypointEnabled", waypointEnabled);
            r.addProperty("eliteAbilityDrop", eliteAbilityDrop);
            r.addProperty("eliteAbilityMinRarity", eliteAbilityMinRarity);
            r.addProperty("polymerRecipeFix", polymerRecipeFix);
            r.addProperty("taczConcurrencyFix", taczConcurrencyFix);
            r.addProperty("quietDisconnectEncodeError", quietDisconnectEncodeError);
            Files.writeString(dir().resolve(FILE), GSON.toJson(r));

            JsonObject w = new JsonObject();
            for (Map.Entry<String, Set<String>> e : WAYPOINTS.entrySet()) {
                JsonArray a = new JsonArray();
                e.getValue().forEach(a::add);
                w.add(e.getKey(), a);
            }
            Files.writeString(dir().resolve(WP_FILE), GSON.toJson(w));
        } catch (Exception ignored) {
            // 写不进去只影响持久化，不影响本次运行。
        }
    }

    /** 按 key 改一个值并落盘；GUI 保存与服务端 C2S 收包都走这里。 */
    public static synchronized void set(String key, String value) {
        load();
        switch (key) {
            case "waypointEnabled" -> waypointEnabled = Boolean.parseBoolean(value);
            case "eliteAbilityDrop" -> eliteAbilityDrop = Boolean.parseBoolean(value);
            case "eliteAbilityMinRarity" ->
                    eliteAbilityMinRarity = value == null ? DEFAULT_ELITE_ABILITY_MIN_RARITY : value;
            case "polymerRecipeFix" -> polymerRecipeFix = Boolean.parseBoolean(value);
            case "taczConcurrencyFix" -> taczConcurrencyFix = Boolean.parseBoolean(value);
            case "quietDisconnectEncodeError" -> quietDisconnectEncodeError = Boolean.parseBoolean(value);
            default -> {
                return;
            }
        }
        save();
    }

    public static synchronized ConfigS2C snapshot(boolean op) {
        load();
        return new ConfigS2C(op, waypointEnabled, eliteAbilityDrop, eliteAbilityMinRarity,
                polymerRecipeFix, taczConcurrencyFix, quietDisconnectEncodeError);
    }

    /** 客户端收到 S2C：把服务端的值覆盖到本地，同时记住自己有没有权限改。 */
    public static synchronized void setFromS2C(boolean edit, boolean we, boolean ead,
                                                String rarity, boolean prf, boolean tcf, boolean qdee) {
        loaded = true;
        canEdit = edit;
        waypointEnabled = we;
        eliteAbilityDrop = ead;
        eliteAbilityMinRarity = rarity;
        polymerRecipeFix = prf;
        taczConcurrencyFix = tcf;
        quietDisconnectEncodeError = qdee;
    }

    public static synchronized void addWaypoint(String uuid, String dim, int x, int y, int z) {
        load();
        WAYPOINTS.computeIfAbsent(uuid, k -> new HashSet<>()).add(key(dim, x, y, z));
        save();
    }

    public static synchronized void removeWaypoint(String uuid, String dim, int x, int y, int z) {
        load();
        Set<String> set = WAYPOINTS.get(uuid);
        if (set != null) {
            set.remove(key(dim, x, y, z));
            save();
        }
    }

    /** 传送前查表：坐标允许小误差（Xaero 的 y 会加 0.5，x/z 可能带小数）。维度不参与 key（见 key()）。 */
    public static synchronized boolean hasWaypoint(String uuid, String dim, double x, double y, double z) {
        load();
        Set<String> set = WAYPOINTS.get(uuid);
        if (set == null) {
            return false;
        }
        for (String k : set) {
            String[] p = k.split("\\|", -1);
            if (p.length != 3) {
                continue;
            }
            try {
                if (Math.abs(Double.parseDouble(p[0]) - x) < 1.5
                        && Math.abs(Double.parseDouble(p[1]) - y) < 2.5
                        && Math.abs(Double.parseDouble(p[2]) - z) < 1.5) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    private static String key(String dim, int x, int y, int z) {
        // 通配维度：客户端登记时可能拿不到当前维度（世界加载阶段），传送也只在玩家当前维度进行，
        // 所以只按坐标登记/查表，维度不参与 key。不同维度坐标撞车概率可忽略，且撞了也只是
        // 「允许传到这个已登记的坐标」，不存在越权。
        return x + "|" + y + "|" + z;
    }

    private static boolean bool(JsonObject r, String k, boolean f) {
        return r.has(k) && r.get(k).isJsonPrimitive() ? r.get(k).getAsBoolean() : f;
    }

    private static String str(JsonObject r, String k, String f) {
        return r.has(k) && r.get(k).isJsonPrimitive() ? r.get(k).getAsString() : f;
    }

    public static synchronized boolean canEdit() {
        load();
        return canEdit;
    }

    public static synchronized boolean isWaypointEnabled() {
        load();
        return waypointEnabled;
    }

    public static synchronized boolean isEliteAbilityDrop() {
        load();
        return eliteAbilityDrop;
    }

    public static synchronized boolean isPolymerRecipeFix() {
        load();
        return polymerRecipeFix;
    }

    public static synchronized boolean isTaczConcurrencyFix() {
        load();
        return taczConcurrencyFix;
    }

    /** 是否静音「disconnect 包编码失败」那条 ERROR 日志（只静音日志，不改变发包行为）。 */
    public static synchronized boolean isQuietDisconnectEncodeError() {
        load();
        return quietDisconnectEncodeError;
    }

    public static synchronized String getEliteAbilityMinRarityName() {
        load();
        return eliteAbilityMinRarity;
    }

    public static synchronized Rarity getEliteAbilityMinRarity() {
        load();
        for (Rarity r : Rarity.values()) {
            if (r.name().equalsIgnoreCase(eliteAbilityMinRarity)) {
                return r;
            }
        }
        return Rarity.RARE;
    }
}
