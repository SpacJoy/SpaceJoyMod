package net.spacejoy.mod.mixin.buffmobs;

import java.util.Map;

import com.khotyz.buffmobs.data.BuffMobsWorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 唯一目的：把 {@code BuffMobsWorldData} 的私有 {@code mobStates} 表拿出来，好按上限裁剪。
 *
 * <p>BuffMobs 没有暴露任何能枚举这张表的公开方法（{@code getMobState(UUID)} 只能按已知
 * UUID 查，而且<b>查不到会顺手新建一条</b>），所以要么反射要么 accessor。accessor 更稳：
 * 字段改名在编译期就报错，不会拖到运行期才炸。
 *
 * <p>只读引用、不改字段本身的指向；删除条目走 {@code Map#remove} 后再由调用方
 * {@code setDirty()}，和 BuffMobs 自己的 {@code removeMobState} 效果一致。
 *
 * <p>这个混入由 {@code SpaceJoyMixinPlugin} 把关，只在 buffmobs 装了才应用。
 */
@Mixin(BuffMobsWorldData.class)
public interface BuffMobsWorldDataAccessor {
    @Accessor("mobStates")
    Map<String, BuffMobsWorldData.MobState> sj$getMobStates();
}
