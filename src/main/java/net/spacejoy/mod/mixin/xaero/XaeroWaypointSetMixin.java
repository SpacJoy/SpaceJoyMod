package net.spacejoy.mod.mixin.xaero;

import java.util.Collection;

import net.spacejoy.mod.network.SpaceJoyClientNetwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;

/**
 * Xaero 路径点登记：把玩家保存/删除的路径点坐标同步给服务端登记表。
 *
 * <p>为什么挂 {@code WaypointSet} 而不是 {@code GuiAddWaypoint} 的确认按钮：前者是所有
 * 增删改的必经之路（GUI 确认、死亡点、共享点、磁盘载入、批量删除都走它），签名稳定；
 * 后者字段几乎全是 private，版本一升级就对不上。
 *
 * <p><b>必须覆盖全部重载</b>：Xaero 删点走的是 {@code removeAll}/{@code clear}/{@code remove(int)}，
 * 编辑走 {@code set(int,...)}，磁盘载入走 {@code addAll} —— 只挂 {@code add}/{@code remove(Waypoint)}
 * 会漏掉「删除同步」和「已有路径点」。这些重载内部都直接操作底层 List，不会互相转调
 * （除了 {@code add(wp)}→{@code add(wp,boolean)}、{@code addAll(c)}→{@code addAll(c,boolean)}），
 * 所以只挂底层那个带 boolean 的即可，不会重复上报；登记表本身是 Set，重复也无害。
 *
 * <p><b>过滤临时路径点</b>：世界地图右键「创建临时路径点」= 地图上任意位置，登记了就会让
 * 非 OP 变相获得任意坐标传送能力，与需求相反，所以只登记玩家正式保存的路径点。
 *
 * <p>只在客户端生效，由 {@code SpaceJoyMixinPlugin} 按 {@code isModLoaded("xaerominimap")} 把关。
 */
@Mixin(targets = "xaero.hud.minimap.waypoint.set.WaypointSet", remap = false)
public abstract class XaeroWaypointSetMixin {

    @Inject(method = "add(Lxaero/common/minimap/waypoints/Waypoint;Z)V",
            at = @At("HEAD"), require = 0)
    private void spacejoy$onAdd(Waypoint wp, boolean sort, CallbackInfo ci) {
        report("add", wp);
    }

    @Inject(method = "addAll(Ljava/util/Collection;Z)V",
            at = @At("HEAD"), require = 0)
    private void spacejoy$onAddAll(Collection<Waypoint> c, boolean sort, CallbackInfo ci) {
        if (c != null) {
            for (Waypoint wp : c) {
                report("add", wp);
            }
        }
    }

    @Inject(method = "remove(Lxaero/common/minimap/waypoints/Waypoint;)V",
            at = @At("HEAD"), require = 0)
    private void spacejoy$onRemove(Waypoint wp, CallbackInfo ci) {
        report("del", wp);
    }

    @Inject(method = "remove(I)Lxaero/common/minimap/waypoints/Waypoint;",
            at = @At("RETURN"), require = 0)
    private void spacejoy$onRemoveAt(int index, CallbackInfoReturnable<Waypoint> cir) {
        report("del", cir.getReturnValue());
    }

    @Inject(method = "removeAll(Ljava/util/Collection;)V",
            at = @At("HEAD"), require = 0)
    private void spacejoy$onRemoveAll(Collection<Waypoint> c, CallbackInfo ci) {
        if (c != null) {
            for (Waypoint wp : c) {
                report("del", wp);
            }
        }
    }

    @Inject(method = "clear()V", at = @At("HEAD"), require = 0)
    private void spacejoy$onClear(CallbackInfo ci) {
        for (Waypoint wp : ((WaypointSet) (Object) this).getWaypoints()) {
            report("del", wp);
        }
    }

    /** 编辑路径点（改坐标/名字）：旧点撤销登记，新点重新登记。 */
    @Inject(method = "set(ILxaero/common/minimap/waypoints/Waypoint;)Lxaero/common/minimap/waypoints/Waypoint;",
            at = @At("RETURN"), require = 0)
    private void spacejoy$onSet(int index, Waypoint newValue, CallbackInfoReturnable<Waypoint> cir) {
        report("del", cir.getReturnValue());
        report("add", newValue);
    }

    private static void report(String op, Waypoint wp) {
        if (wp == null || wp.isTemporary()) {
            return; // 临时点 = 地图右键任意位置，绝不登记
        }
        // 维度不参与服务端登记表（见 SpaceJoyServerConfig.key），传 "*" 占位，
        // 避免在世界加载阶段去取 client level 造成空指针。
        SpaceJoyClientNetwork.sendWaypoint(op, wp.getName(),
                wp.getX(), wp.getY(), wp.getZ(), "*");
    }
}
