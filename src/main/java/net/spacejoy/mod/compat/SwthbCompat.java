package net.spacejoy.mod.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import zy.swthb.command.BackHandler;
import zy.swthb.command.TeleportHandler;
import zy.swthb.config.ModConfig;

/**
 * simple-warp-tpa-home-back 的接触面收在这里，让 {@code /sjwp} 和它的 /home 走同一套流程。
 *
 * <p>为什么需要这个类：{@code /sjwp} 是我们自己注册的命令，swthb 不认识它，
 * 所以既不会在传送前调用 {@code BackHandler#saveBackPoint}（导致 /back 永远「没有可返回的位置」），
 * 也不会走 {@code TeleportHandler}（传送延时这些全局设置对路径点失效）。
 * 这里照着 {@code HomeCommand#teleportToHome} 的顺序补上：
 * 先在当前位置存返回点，再用 {@code TeleportHandler.startTeleport} 传送。
 *
 * <p>调用方必须先判断 {@link net.spacejoy.mod.CompatFlags#SWTHB_LOADED}，
 * 否则本类加载时会因为找不到 swthb 的类而抛 NoClassDefFoundError。
 */
public final class SwthbCompat {
    private SwthbCompat() {
    }

    /** 把玩家「传送前」的位置记成返回点，跟 /home、/warp 的行为一致。 */
    public static void saveBackPoint(ServerPlayer player) {
        // /back 被关掉时就没必要占这个位置；开着才记（和 swthb 自己的判断对齐）。
        if (!ModConfig.getInstance().isBackEnabled()) {
            return;
        }
        BackHandler.saveBackPoint(player, BackHandler.LocationType.TELEPORT);
    }

    /**
     * 走 swthb 的传送通道，这样它的传送延时等设置对路径点同样生效。
     *
     * @param onArrived 真正落地后才执行（传送延时 &gt; 0 时不会被提前触发），可为 null
     */
    public static void teleport(ServerPlayer player, TeleportTransition transition, Runnable onArrived) {
        // 取消回调传 null：swthb 内部会判空，我们不需要额外提示。
        TeleportHandler.startTeleport(player, transition, onArrived, null);
    }
}
