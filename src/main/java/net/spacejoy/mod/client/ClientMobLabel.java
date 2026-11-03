package net.spacejoy.mod.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.spacejoy.mod.CompatFlags;
import net.spacejoy.mod.compat.EACompat;
import net.spacejoy.mod.config.SpaceJoyConfig;

/**
 * 给带能力掉落的生物做「头顶标签」，走的是原版命名牌那条渲染通道，不是发光描边。
 *
 * <p><b>为什么放弃描边。</b>原版描边（{@code EntityRenderState.outlineColor}）有两个改不掉的毛病：
 * 一是不参与深度测试，隔着山也能看见；二是它只画轮廓，被别的实体挡住的那部分照样亮，
 * 想补上「被实体遮挡也要剔除」得自己重写整套描边渲染，代价和收益完全不匹配。
 * 头顶标签由原版 {@code SubmitNodeCollector.submitNameTag} 画，深度、遮挡、光影映射全部由
 * 原版自己处理，我们只管往 {@code EntityRenderState.nameTag} 里塞文本。
 *
 * <p><b>标签文本</b>就是 EA 同步到客户端的能力列表（客户端本地就有，不用问服务端）。
 * 结果按实体 id 缓存，{@link #REFRESH_TICKS} 刷新一次 —— 能力几乎不变，没必要每帧重新拼。
 *
 * <p><b>被命名时让位</b>：生物本身有自定义名（{@code hasCustomName()}）时，原版会占掉
 * {@code nameTag} 这个位置；这时我们把标签塞进 {@code scoreText}，原版会把名字画在上、
 * 标签画在下，两者不会叠在一起。
 */
@Environment(EnvType.CLIENT)
public final class ClientMobLabel {
    /** 标签文本的刷新间隔（客户端 tick）。 */
    private static final int REFRESH_TICKS = 20;
    /** 超过这个距离不画标签，和原版命名牌的默认可见距离一致。 */
    private static final double MAX_DISTANCE = 64.0;
    /** 缓存条目的最长空闲时间（客户端 tick），超过就认为那只生物已经不在了。 */
    private static final int CACHE_TTL_TICKS = 1200;

    private record Entry(UUID uuid, Component label, int tick) {
    }

    private static final Map<Integer, Entry> CACHE = new ConcurrentHashMap<>();

    private static int ticks;
    private static ClientLevel lastLevel;

    private ClientMobLabel() {
    }

    public static void init() {
        if (!CompatFlags.EA_LOADED) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            // 换世界 / 退出时清空，免得实体 id 撞上别的生物。
            if (client.level != lastLevel) {
                lastLevel = client.level;
                CACHE.clear();
                return;
            }
            if (ticks % CACHE_TTL_TICKS == 0) {
                CACHE.entrySet().removeIf(e -> ticks - e.getValue().tick() > CACHE_TTL_TICKS);
            }
        });
    }

    /**
     * 这只生物头顶该显示什么标签；返回 {@code null} 表示不显示。
     *
     * @param distanceSqr 到摄像机的距离平方，直接用 {@code EntityRenderState.distanceToCameraSq}
     */
    public static Component labelFor(Entity entity, double distanceSqr) {
        // 必须先判软依赖：EA 不在时 EACompat 这个类压根不存在，碰它就是 NoClassDefFoundError，
        // 而且是在渲染线程上每帧每只生物都调一次 —— 直接崩游戏。
        if (!CompatFlags.EA_LOADED) {
            return null;
        }
        if (!SpaceJoyConfig.isEnabled() || !SpaceJoyConfig.isMobLabel()) {
            return null;
        }
        if (!(entity instanceof Mob mob)) {
            return null;
        }
        // 相机还没设好时 extractNameTags 会提前返回，distanceToCameraSq 留着 0；
        // 这时候不画，免得刚进世界那几帧把全世界的标签都点亮。
        if (distanceSqr <= 0.0 || distanceSqr > MAX_DISTANCE * MAX_DISTANCE) {
            return null;
        }

        Entry entry = CACHE.get(mob.getId());
        if (entry != null && entry.uuid().equals(mob.getUUID()) && ticks - entry.tick() < REFRESH_TICKS) {
            return entry.label();
        }
        Component label = buildLabel(mob);
        CACHE.put(mob.getId(), new Entry(mob.getUUID(), label, ticks));
        return label;
    }

    /** 把能力列表拼成一行；没有能力就返回 {@code null}（缓存里存 null 表示「查过了，没有」）。 */
    private static Component buildLabel(Mob mob) {
        var lines = EACompat.getAbilityLines(mob);
        if (lines.isEmpty()) {
            return null;
        }
        MutableComponent text = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                text.append(Component.literal(" "));
            }
            text.append(lines.get(i));
        }
        return text;
    }

    /** 配置改完开关后清掉缓存，下一帧立刻按新设置来。 */
    public static void clearCache() {
        CACHE.clear();
    }
}
