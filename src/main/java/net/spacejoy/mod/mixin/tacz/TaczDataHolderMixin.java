package net.spacejoy.mod.mixin.tacz;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.tacz.guns.entity.sync.core.SyncedDataKey;
import net.minecraft.world.entity.Entity;
import net.spacejoy.mod.SpaceJoyMod;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.config.SpaceJoyServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修掉 TaCZ 的 {@code DataHolder.dataMap} 在多线程下抛
 * {@code ConcurrentModificationException: null} 把游戏带崩的问题。
 *
 * <p><b>崩溃现场</b>（26.3 R1-hotfix3 实测）：
 * <pre>
 * java.util.ConcurrentModificationException
 *   at java.util.HashMap.computeIfAbsent(HashMap.java:1230)
 *   at com.tacz.guns.entity.sync.core.DataHolder.get(DataHolder.java:30)
 *   at com.tacz.guns.entity.sync.core.SyncedEntityData.get(SyncedEntityData.java:124)
 *   at com.tacz.guns.entity.sync.core.SyncedDataKey.getValue(SyncedDataKey.java:28)
 *   at net.minecraft.world.entity.LivingEntity.getSynReloadState(LivingEntity.java:29383)
 *   at com.tacz.guns.entity.shooter.LivingEntityAim.tickSprint(LivingEntityAim.java:104)
 * </pre>
 *
 * <p><b>为什么会撞</b>：{@code DataHolder} 里那行是
 * {@code public Map<..., ...> dataMap = new HashMap<>();} —— 一张裸 {@code HashMap}，
 * 全类没有任何同步。而它会同时被两边摸到：
 * <ul>
 *   <li>服务端 tick 线程：{@code LivingEntityAim.tickSprint} 每 tick 拿一次
 *       {@code reload_state}，走 {@code DataHolder.get} → {@code computeIfAbsent}；
 *       每条生物都走，没拿枪的生物也一样。</li>
 *   <li>实体 NBT 线程：TaCZ 自己的 {@code EntityMixin} 把
 *       {@code DataHolderCapabilityProvider.readFromNbt/writeToNbt} 注入到
 *       {@code Entity.load} / {@code Entity.saveWithoutId}，而 {@code deserializeNBT}
 *       会先 {@code dataMap.clear()} 再逐条 {@code put}。装了 c2me 之后实体数据读写
 *       会挪到区块线程池，于是这两个动作就和 tick 线程撞上了。</li>
 * </ul>
 * {@code HashMap.computeIfAbsent} 在 mapping function 执行完会核对一次 {@code modCount}
 * （JDK 25 的 HashMap.java:1230），一旦期间有人改过结构就直接抛 CME。所以这不是重入 bug，
 * 是实打实的跨线程并发写。
 *
 * <p><b>怎么修</b>：把 {@code dataMap} 换成 {@code ConcurrentHashMap}。{@code get/set} 的
 * {@code computeIfAbsent} 本来就是原子语义，换掉之后并发读写的语义只会更强；{@code clear()}
 * 加逐条 {@code put} 的重建期间别的线程会看到一张还在重建的表（可能瞬态读到默认值，下一条
 * {@code put} 就覆盖回来），但不会再抛异常 —— 而且重建发生在实体加载期，那时实体还没开始 tick。
 *
 * <p>唯一会把 {@code dataMap} 换回普通 {@code HashMap} 的地方是 TaCZ 的
 * {@code SyncedEntityDataEvent.onPlayerClone}（{@code new HashMap<>(oldHolder.dataMap)} 之后
 * 直接赋给新 holder）。那个 holder 是刚克隆出来的新玩家、还没被别的线程看见，所以这里不拦它，
 * 只要之后第一次 {@code get}/{@code set}/{@code clean}/{@code gather*} 时顺手换回来即可 ——
 * 也就是下面那几个 HEAD 注入干的事。
 *
 * <p>目标类属于 TaCZ，是软依赖，所以混入由 {@code SpaceJoyMixinPlugin} 按
 * {@code isModLoaded("tacz")} 开关，配置为 {@code required: false}。
 */
@Mixin(targets = "com.tacz.guns.entity.sync.core.DataHolder", remap = false)
public abstract class TaczDataHolderMixin {
    /** 目标类里的那个字段，原名原描述符（{@code Ljava/util/Map;}），只借用来换实现。 */
    @Shadow
    public Map<Object, Object> dataMap;

    /** 只在第一次换成并发表时打一行，避免刷屏。 */
    @Unique
    private boolean spacejoy$mapSwapped;

    /**
     * 把 {@code dataMap} 换成 {@code ConcurrentHashMap}。
     *
     * <p>已经是并发表就直接返回 —— 这是绝大多数调用的路径，开销只有一个 {@code instanceof}。
     * 开关走配置，关掉本项就完全退回 TaCZ 原样（包括那个崩溃）。
     */
    @Unique
    private void spacejoy$ensureConcurrentMap() {
        if (!SpaceJoyConfig.isEnabled() || !SpaceJoyServerConfig.isTaczConcurrencyFix()) {
            return;
        }
        // 热路径：已经换过就只付一个 instanceof。
        if (this.dataMap instanceof ConcurrentHashMap) {
            return;
        }
        // 换表本身也要防并发：两个线程同时走到这里会各建一张新表，后赋值的把那半份数据盖掉。
        // 锁的是 holder 自己，粒度等同单只实体，不会串到别的实体上。
        synchronized (this) {
            if (this.dataMap instanceof ConcurrentHashMap) {
                return;
            }
            this.dataMap = new ConcurrentHashMap<>(this.dataMap);
            if (!this.spacejoy$mapSwapped) {
                this.spacejoy$mapSwapped = true;
                SpaceJoyMod.LOGGER.debug("TaCZ DataHolder.dataMap switched to ConcurrentHashMap");
            }
        }
    }

    /** 构造完立刻换掉，之后新建的 holder 天生就是并发表。 */
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void spacejoy$onInit(CallbackInfo ci) {
        this.spacejoy$ensureConcurrentMap();
    }

    /** 崩溃就发生在这里：先保证表是并发的，再让原方法去 computeIfAbsent。 */
    @Inject(method = "get", at = @At("HEAD"), require = 1)
    private void spacejoy$beforeGet(SyncedDataKey<?, ?> key, CallbackInfoReturnable<Object> cir) {
        this.spacejoy$ensureConcurrentMap();
    }

    @Inject(method = "set", at = @At("HEAD"), require = 0)
    private void spacejoy$beforeSet(Entity entity, SyncedDataKey<?, ?> key, Object value,
                                    CallbackInfoReturnable<Boolean> cir) {
        this.spacejoy$ensureConcurrentMap();
    }

    /** 下面三个是遍历型入口；换表只是兜底，真失败也不该让注入报错，所以 require = 0。 */
    @Inject(method = "clean", at = @At("HEAD"), require = 0)
    private void spacejoy$beforeClean(CallbackInfo ci) {
        this.spacejoy$ensureConcurrentMap();
    }

    @Inject(method = "gatherDirty", at = @At("HEAD"), require = 0)
    private void spacejoy$beforeGatherDirty(CallbackInfoReturnable<List<?>> cir) {
        this.spacejoy$ensureConcurrentMap();
    }

    @Inject(method = "gatherAll", at = @At("HEAD"), require = 0)
    private void spacejoy$beforeGatherAll(CallbackInfoReturnable<List<?>> cir) {
        this.spacejoy$ensureConcurrentMap();
    }
}
