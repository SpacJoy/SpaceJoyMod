package net.spacejoy.mod.compat;

import com.khotyz.buffmobs.event.EliteMobHandler;
import net.minecraft.world.entity.Mob;

/**
 * BuffMobs 的唯一接触面：判断一只生物是不是 BuffMobs 生成的精英。
 *
 * <p>调用方必须先判断 {@link net.spacejoy.mod.CompatFlags#BUFFMOBS_LOADED}，
 * 否则本类加载时会因为找不到 BuffMobs 的类而抛 NoClassDefFoundError。
 *
 * <p>这里只读 BuffMobs 公开的静态接口，不碰它的配置对象、不改它的世界存档数据
 * （{@code BuffMobsWorldData}）。精英状态本来就存在存档里，我们只查询，不动它。
 */
public final class EliteMobCompat {
    private EliteMobCompat() {
    }

    /**
     * 这只生物是不是 BuffMobs 判定的精英。
     *
     * <p>同时要求 BuffMobs 的精英功能是开着的：玩家在它配置里关掉精英后，
     * 存档里那些旧的 {@code elite=true} 记录不该再触发掉落。
     */
    public static boolean isElite(Mob mob) {
        return EliteMobHandler.isEnabled() && EliteMobHandler.isElite(mob);
    }
}
