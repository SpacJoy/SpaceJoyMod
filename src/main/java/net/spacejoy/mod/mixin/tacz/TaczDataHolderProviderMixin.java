package net.spacejoy.mod.mixin.tacz;

import com.tacz.guns.entity.sync.core.DataHolder;
import net.minecraft.nbt.CompoundTag;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.config.SpaceJoyServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link TaczDataHolderMixin} 的第二半：把实体存档读写这条路径也堵上。
 *
 * <p>TaCZ 的 {@code EntityMixin} 把这里的 {@code writeToNbt} / {@code readFromNbt} 注进了
 * {@code Entity.saveWithoutId} 和 {@code Entity.load}，而这两个方法内部直接遍历
 * {@code holder.dataMap}（写）或先 {@code clear()} 再逐条 {@code put}（读）。
 * 装了 c2me 之后实体存档读写会离开主线程，于是这两条就和 tick 线程的
 * {@code DataHolder.get} 撞在一起 —— 也就是崩溃报告里的那一帧。
 *
 * <p>为什么不能只靠 {@link TaczDataHolderMixin}：那张表在玩家克隆
 * （{@code SyncedEntityDataEvent.onPlayerClone}）时会被整个换成一张新的普通 {@code HashMap}。
 * 换完之后如果第一次摸它的是存档线程，就轮不到 tick 线程去换表。
 * 所以这里在两条存档路径的入口再兜一次。
 */
@Mixin(targets = "com.tacz.guns.entity.sync.core.DataHolderCapabilityProvider", remap = false)
public abstract class TaczDataHolderProviderMixin {
    @Shadow
    private DataHolder holder;

    @Inject(method = "writeToNbt", at = @At("HEAD"), require = 0)
    private void spacejoy$beforeWriteToNbt(CompoundTag tag, CallbackInfo ci) {
        spacejoy$ensureConcurrent(this.holder);
    }

    @Inject(method = "readFromNbt", at = @At("HEAD"), require = 0)
    private void spacejoy$beforeReadFromNbt(CompoundTag tag, CallbackInfo ci) {
        spacejoy$ensureConcurrent(this.holder);
    }

    /**
     * 和 {@link TaczDataHolderMixin#spacejoy$ensureConcurrentMap} 同一套逻辑。
     *
     * <p>因为跨类调不到那边的 {@code @Unique} 方法，这里重写一遍。换表要加锁，
     * 否则两个线程同时看到「还不是并发表」会各建一张，后赋值的把另一半数据盖掉。
     */
    private static void spacejoy$ensureConcurrent(DataHolder holder) {
        if (holder == null || !SpaceJoyConfig.isEnabled() || !SpaceJoyServerConfig.isTaczConcurrencyFix()) {
            return;
        }
        if (holder.dataMap instanceof java.util.concurrent.ConcurrentHashMap) {
            return;
        }
        synchronized (holder) {
            if (holder.dataMap instanceof java.util.concurrent.ConcurrentHashMap) {
                return;
            }
            holder.dataMap = new java.util.concurrent.ConcurrentHashMap<>(holder.dataMap);
        }
    }
}
