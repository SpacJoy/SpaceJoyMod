package net.spacejoy.mod.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.khotyz.buffmobs.data.BuffMobsWorldData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.LevelResource;
import net.spacejoy.mod.CompatFlags;
import net.spacejoy.mod.SpaceJoyMod;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.config.SpaceJoyServerConfig;
import net.spacejoy.mod.mixin.buffmobs.BuffMobsWorldDataAccessor;

/**
 * BuffMobs 状态表裁剪：把 {@code world_data.dat} 里只增不减的 mobStates 压到可解码的规模。
 *
 * <p><b>为什么需要这个补丁。</b>BuffMobs 给每只生物都存一份 {@code MobState}
 * （精英 / 词条 / 护甲槽位 / 召唤冷却 …），键是生物 UUID。它只在「生物死亡」时删条目，
 * 自然消失、{@code /kill} 以外的 discard、区块卸载后再也没回来的那些全都留在表里，
 * 条目数只涨不降。表本身没问题，问题在读取路径：{@code BuffMobsWorldData.CODEC} 用
 * {@code Codec.unboundedMap}，解码走 {@code BaseMapCodec} 的
 * {@code Object2ObjectArrayMap.putIfAbsent} —— 每插入一条都要线性扫一遍已有键，
 * 是 O(n²) 的。一万九千多条实测卡在 {@code SavedDataStorage.readSavedData} 里超过 60 秒，
 * 被服务端 Watchdog 判成「单 tick 卡死」直接强杀进程。
 *
 * <p><b>而且一旦解码过就晚了。</b>第一次 {@code BuffMobsWorldData.get()} 会把整张表读进内存，
 * 之后再裁也改变不了那次启动的命运。所以必须赶在世界打开之前动磁盘上的文件，见下。
 *
 * <p><b>分两段。</b>
 * <ol>
 *   <li>{@code SERVER_STARTING}（{@code runServer} 里 {@code initServer} 之前，世界还没打开）：
 *       直接读盘上的 NBT，按优先级保留 {@code limit} 条，其余删掉写回。这一步绕开
 *       BuffMobs 的 codec，自己遍历 compound，所以踩不到那个 O(n²)。</li>
 *   <li>{@code END_SERVER_TICK}：每 200 tick 看一次内存里的表，涨到「上限 × 触发倍数」
 *       （默认 4 倍）时一次性裁回上限。这一段保的是「跑起来以后又被填满」的那种增长。
 *       之所以不在「刚过上限」就裁，见 {@link #onEndTick}。</li>
 * </ol>
 *
 * <p><b>命名牌保护。</b>玩家用命名牌命名过的生物这条规则一律不动，判据是
 * {@code Mob#hasCustomName()}——它和 buffmobs 自己起的英文名走同一个字段，
 * 无法从名字本身区分，所以这里改用「登记」而不是「猜」：
 * {@code ENTITY_LOAD} 时凡是带自定义名字的生物就把 UUID 记进
 * {@code <world>/…/buffmobs/spacejoy_named_mobs.dat}，两个裁剪阶段都把这批 UUID
 * 整批排除。这样即使命名怪所在区块没加载、它也不在候选里。
 * 被裁的只会是「没名字 + 没有任何身份信息（纯占位）」的老条目。
 *
 * <p>裁掉的只是状态记录，不是生物，也不改任何名字：{@code EliteMobHandler.applyElite}
 * 只在 {@code getCustomName() == null} 时才写名字，所以万一某只没登记的怪被裁了，
 * 它自己的名字也原样在实体 NBT 里，只是会重新抽一次精英/词条。
 *
 * <p>调用方必须先判断 {@link CompatFlags#BUFFMOBS_LOADED}，否则本类加载时会因为
 * 找不到 BuffMobs 的类而抛 {@code NoClassDefFoundError}。
 */
public final class BuffMobsStateGuard {
    /** 磁盘裁剪只做一次：{@code SERVER_STARTING} 时世界还没打开，此刻改文件不会被内存里的旧表覆盖。 */
    private static boolean trimmed;
    /** 磁盘裁剪失败时停用运行期检查：那一段的 {@code get()} 会在大表上触发一次 60 秒解码。 */
    private static boolean disabled;

    /** 存档里这张表的位置：{@code <world>/dimensions/minecraft/overworld/data/buffmobs/world_data.dat}。 */
    private static final String[] DATA_PATH = {
            "dimensions", "minecraft", "overworld", "data", "buffmobs", "world_data.dat"
    };
    /** 命名怪登记表就放在世界数据旁边，跟着存档走（换存档不会串）。 */
    private static final String NAMED_FILE = "spacejoy_named_mobs.dat";
    /** 登记表的硬上限：只增不减的集合本身就是新的隐患，到顶就不再收新 UUID。 */
    private static final int NAMED_CAP = 8192;

    /** 被命名牌（或数据包 CustomName）命名过的生物 UUID；裁剪时整批跳过。 */
    private static final Set<String> NAMED = new HashSet<>();
    /**
     * 本局是否已经加载过生物。
     *
     * <p>运行期裁剪要拿 {@code BuffMobsWorldData.get()}，而它「读不到就现读盘解码」。
     * 服务器刚起来、一个怪都还没刷的时候去碰它，等于我们自己替 buffmobs 付那次解码。
     * 但只要场上有怪，buffmobs 的 {@code MobTickHandler} 在同一个 tick 末尾一定会调，
     * 那时表已经在内存里，我们只是搭个便车。所以这里等「见过生物」再动手。
     */
    private static boolean sawMob;
    /**
     * 上一次运行期裁剪之后记下的条目数，用来判断这一批是不是已经裁过。
     *
     * <p>光看「是否超过触发线」不够：如果表里剩下的全是精英、词条这类永不可删的条目，
     * 表会永远高于触发线，于是每次检查都白扫一遍整张表。记下裁剪后的规模，
     * 表在它之上又长了一整批才再动手。
     */
    private static int trimmedAt = -1;
    private static Path namedFile;
    private static boolean namedDirty;
    private static boolean namedLoaded;

    private BuffMobsStateGuard() {
    }

    public static void init() {
        if (!CompatFlags.BUFFMOBS_LOADED) {
            return;
        }
        ServerEntityEvents.ENTITY_LOAD.register(BuffMobsStateGuard::onEntityLoad);
        ServerLifecycleEvents.SERVER_STARTING.register(BuffMobsStateGuard::onServerStarting);
        ServerLifecycleEvents.BEFORE_SAVE.register((server, flush, require) -> saveNamed());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            saveNamed();
            resetForNewServer();
        });
        ServerTickEvents.END_SERVER_TICK.register(BuffMobsStateGuard::onEndTick);
    }

    /** 换存档时把只跟「上一个服务器实例」有关的状态清掉。 */
    private static void resetForNewServer() {
        NAMED.clear();
        namedFile = null;
        namedDirty = false;
        namedLoaded = false;
        trimmed = false;
        disabled = false;
        sawMob = false;
        trimmedAt = -1;
    }

    private static boolean active() {
        return SpaceJoyConfig.isEnabled() && SpaceJoyServerConfig.isBuffMobsStateGuard();
    }

    // -------------------------------------------------------------------------------------
    // 命名怪登记
    // -------------------------------------------------------------------------------------

    private static void onEntityLoad(Entity entity, ServerLevel level) {
        if (!(entity instanceof Mob mob)) {
            return;
        }
        sawMob = true;
        // 功能关着就不该往存档里写任何东西；真开了以后新加载的怪会补登记。
        if (!active() || !mob.hasCustomName()) {
            return;
        }
        ensureNamedLoaded(level.getServer());
        if (NAMED.size() >= NAMED_CAP) {
            return;
        }
        if (NAMED.add(mob.getUUID().toString())) {
            namedDirty = true;
        }
    }

    private static void ensureNamedLoaded(MinecraftServer server) {
        if (namedLoaded) {
            return;
        }
        namedLoaded = true;
        try {
            Path dir = dataDir(server);
            if (dir == null) {
                return;
            }
            namedFile = dir.resolve(NAMED_FILE);
            if (!Files.isRegularFile(namedFile)) {
                return;
            }
            CompoundTag tag = NbtIo.readCompressed(namedFile, NbtAccounter.unlimitedHeap());
            ListTag list = tag.getListOrEmpty("named");
            for (int i = 0; i < list.size(); i++) {
                list.getString(i).ifPresent(NAMED::add);
            }
        } catch (Throwable t) {
            // 登记表坏了就当空的：只是少了一层保护，不能让服务端起不来。
            SpaceJoyMod.LOGGER.warn("读取命名怪登记表失败，按空表处理", t);
        }
    }

    private static void saveNamed() {
        if (!namedDirty || namedFile == null) {
            return;
        }
        namedDirty = false;
        try {
            Files.createDirectories(namedFile.getParent());
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (String uuid : NAMED) {
                list.add(StringTag.valueOf(uuid));
            }
            tag.put("named", list);
            NbtIo.writeCompressed(tag, namedFile);
        } catch (IOException e) {
            namedDirty = true;
            SpaceJoyMod.LOGGER.warn("写命名怪登记表失败", e);
        }
    }

    // -------------------------------------------------------------------------------------
    // 第一段：世界打开之前裁磁盘
    // -------------------------------------------------------------------------------------

    private static void onServerStarting(MinecraftServer server) {
        // 同一个 JVM 里换存档（单人切世界）时，上一局的登记表和「已裁过」标记都不能带过来。
        resetForNewServer();
        ensureNamedLoaded(server);
        if (trimmed || disabled || !active()) {
            return;
        }
        trimmed = true;
        int limit = SpaceJoyServerConfig.getBuffMobsStateLimit();
        try {
            Path path = resolveDataPath(server);
            if (path == null || !Files.isRegularFile(path)) {
                return;
            }
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            CompoundTag data = root.getCompoundOrEmpty("data");
            CompoundTag mobStates = data.getCompoundOrEmpty("mobStates");
            int before = mobStates.size();
            if (before <= limit) {
                return;
            }
            // 优先级高的排前面（稳定排序，同优先级保持原顺序），下标 >= limit 的删掉。
            List<String> keys = new ArrayList<>(mobStates.keySet());
            keys.sort(Comparator.comparingInt((String key) -> -diskPriority(tagOf(mobStates, key), key)));
            int removed = 0;
            int skippedNamed = 0;
            for (int i = limit; i < keys.size(); i++) {
                String key = keys.get(i);
                if (NAMED.contains(key)) {
                    skippedNamed++;
                    continue;
                }
                if (mobStates.remove(key) != null) {
                    removed++;
                }
            }
            if (removed == 0) {
                return;
            }
            // 首次裁剪留一份原文件：这毕竟是别人模组的存档数据，出问题得能退回去。
            Path backup = path.resolveSibling(path.getFileName() + ".sjbak");
            if (!Files.isRegularFile(backup)) {
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            Path tmp = path.resolveSibling(path.getFileName() + ".sjtmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            SpaceJoyMod.LOGGER.info("BuffMobs 状态表裁剪：{} -> {} 条（上限 {}），删掉 {} 条占位记录，保护命名怪 {} 条",
                    before, mobStates.size(), limit, removed, skippedNamed);
        } catch (Throwable t) {
            disabled = true;
            SpaceJoyMod.LOGGER.warn("BuffMobs 状态表裁剪失败，本次启动不再处理（原文件没动）", t);
        }
    }

    private static Path dataDir(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        if (root == null) {
            return null;
        }
        return root.resolve(DATA_PATH[0]).resolve(DATA_PATH[1]).resolve(DATA_PATH[2])
                .resolve(DATA_PATH[3]).resolve(DATA_PATH[4]);
    }

    private static Path resolveDataPath(MinecraftServer server) {
        Path dir = dataDir(server);
        return dir == null ? null : dir.resolve(DATA_PATH[5]);
    }

    private static CompoundTag tagOf(CompoundTag states, String key) {
        return states.get(key) instanceof CompoundTag tag ? tag : null;
    }

    /**
     * 磁盘上的条目值不值得留：0 = 只有「已评估」标记，数字越大越该留。
     *
     * <p>判据全部来自 {@code MobState} 的字段默认值 —— 默认构造是
     * {@code elite=false, trait="", armorSlotsMask=0, lastTeleportTick=-100000,
     * packLastPullTick=Long.MIN_VALUE}，所以「还是默认值」就等于「没留下任何身份信息」。
     */
    private static int diskPriority(CompoundTag state, String key) {
        if (state == null) {
            return 0;
        }
        // eliteGeneratedName 为真说明 buffmobs 已经给它起过名（名字在实体 NBT 里），
        // 与玩家命名牌同名同姓，同样不该被裁。
        if (state.getBooleanOr("elite", false)
                || state.getBooleanOr("eliteGeneratedName", false)
                || NAMED.contains(key)) {
            return 4;
        }
        if (!state.getStringOr("trait", "").isEmpty()) {
            return 3;
        }
        if (state.getIntOr("armorSlotsMask", 0) != 0) {
            return 2;
        }
        if (state.getBooleanOr("summonedMinion", false)
                || !state.getListOrEmpty("summonedMinions").isEmpty()
                || state.getBooleanOr("bloodMoonGlowing", false)
                || state.getBooleanOr("passiveAggressionEnraged", false)
                || state.getBooleanOr("lastStandUsed", false)
                || state.getLongOr("nextSummonTick", 0L) != 0L
                || state.getLongOr("lastTeleportTick", -100000L) != -100000L
                || state.getLongOr("packLastPullTick", Long.MIN_VALUE) != Long.MIN_VALUE) {
            return 1;
        }
        return 0;
    }

    // -------------------------------------------------------------------------------------
    // 第二段：跑起来之后限上限
    // -------------------------------------------------------------------------------------

    private static void onEndTick(MinecraftServer server) {
        if (disabled || !sawMob || !active()) {
            return;
        }
        // 200 tick（10 秒）查一次。这个开销不值得每 tick 付。
        if (server.getTickCount() % 200 != 0) {
            return;
        }
        BuffMobsWorldData data;
        try {
            data = BuffMobsWorldData.get(server);
        } catch (Throwable t) {
            disabled = true;
            SpaceJoyMod.LOGGER.warn("读取 BuffMobs 状态表失败，已停用运行期裁剪", t);
            return;
        }
        if (data == null) {
            return;
        }
        // 混入没应用上（比如哪天换了混淆名）时这里是普通强转失败而不是静默返回，
        // 而它跑在服务端主线程上 —— 一次 ClassCastException 就是一次崩服。
        if (!(data instanceof BuffMobsWorldDataAccessor accessor)) {
            disabled = true;
            SpaceJoyMod.LOGGER.warn("BuffMobs 状态表访问器没应用上，已停用运行期裁剪（磁盘裁剪仍然有效）");
            return;
        }
        Map<String, BuffMobsWorldData.MobState> states = accessor.sj$getMobStates();
        if (states == null) {
            return;
        }
        int limit = SpaceJoyServerConfig.getBuffMobsStateLimit();
        int factor = SpaceJoyServerConfig.getBuffMobsStateTrimFactor();
        // 触发线在「上限」之上再乘一个自定义倍数。这一整轮扫描的代价是 O(条目数)，
        // 而条目每天都在按「刷出来但没死」的速度增长，贴着上限裁十来条的结果是：
        // 每 10 秒刷一行日志、表永远卡在临界点上。所以改成攒够一批再裁，一次裁回上限。
        int trigger = (int) Math.min((long) limit * Math.max(1, factor), Integer.MAX_VALUE);
        int size = states.size();
        if (size <= trigger) {
            return;
        }
        // 上一轮处理过这个规模就别再整表扫了，等它再涨出一整批再说：
        // 已经裁过一次的批次不会自己掉回来，连着扫只会白烧 CPU。
        int step = Math.max(256, limit / 20);
        if (trimmedAt >= 0 && size < trimmedAt + step) {
            return;
        }
        int over = size - limit;
        // 已加载实体整批保护；命名怪还有登记表兜底（它们可能在没加载的区块里）。
        Set<String> keep = new HashSet<>(NAMED);
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob mob) {
                    keep.add(mob.getUUID().toString());
                }
            }
        }
        int removed = 0;
        // 先删完全没身份的，还不够再删只留了冷却/发光痕迹的 —— 精英、词条、命名怪永不进候选。
        removed += removeUpTo(states, keep, 0, over - removed);
        removed += removeUpTo(states, keep, 1, over - removed);
        trimmedAt = states.size();
        if (removed > 0) {
            data.setDirty();
            SpaceJoyMod.LOGGER.info(
                    "BuffMobs 状态表运行期裁剪：{} 条超过触发线 {} 条（上限 {} × {} 倍），删掉 {} 条",
                    size, trigger, limit, factor, removed);
        } else if (SpaceJoyConfig.isDebugLog()) {
            // 一条都删不动说明表里剩下的全是不可删的身份记录，这本身就是信息：
            // 要么调高上限，要么知道这套判据在这份存档上已经到头了。只在调试日志里记一次。
            SpaceJoyMod.LOGGER.info(
                    "BuffMobs 状态表运行期裁剪：{} 条超过触发线 {} 条，但剩下的全是精英/词条/护甲槽位记录，无可删条目",
                    size, trigger);
        }
    }

    private static int removeUpTo(Map<String, BuffMobsWorldData.MobState> states, Set<String> keep,
                                  int maxPriority, int want) {
        if (want <= 0) {
            return 0;
        }
        List<String> victims = new ArrayList<>();
        for (Map.Entry<String, BuffMobsWorldData.MobState> entry : states.entrySet()) {
            if (victims.size() >= want) {
                break;
            }
            if (keep.contains(entry.getKey())) {
                continue;
            }
            if (statePriority(entry.getValue()) <= maxPriority) {
                victims.add(entry.getKey());
            }
        }
        for (String key : victims) {
            states.remove(key);
        }
        return victims.size();
    }

    /** 与 {@link #diskPriority} 同一套判据，只是换成读已经解码好的对象。 */
    private static int statePriority(BuffMobsWorldData.MobState state) {
        if (state == null) {
            return 0;
        }
        if (state.elite() || state.eliteGeneratedName()) {
            return 4;
        }
        String trait = state.trait();
        if (trait != null && !trait.isEmpty()) {
            return 3;
        }
        if (state.armorSlotsMask() != 0) {
            return 2;
        }
        List<String> minions = state.summonedMinions();
        if (state.summonedMinion()
                || (minions != null && !minions.isEmpty())
                || state.bloodMoonGlowing()
                || state.passiveAggressionEnraged()
                || state.lastStandUsed()
                || state.nextSummonTick() != 0L
                || state.lastTeleportTick() != -100000L
                || state.packLastPullTick() != Long.MIN_VALUE) {
            return 1;
        }
        return 0;
    }
}
