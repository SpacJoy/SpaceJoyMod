package net.spacejoy.mod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.spacejoy.mod.config.SpaceJoyConfig;

/**
 * 「这件装备要不要画」的判定，给 {@link net.spacejoy.mod.mixin.HideProjectEArmorMixin} 用。
 *
 * <p>只按物品的注册命名空间认 {@code projecte}，不去 import ProjectEF 的类：
 * SpaceJoyMod 对 ProjectEF 是软依赖，写死类引用会让没装 ProjectEF 的实例在加载期就炸。
 * 命名空间判定也顺带覆盖了 PEE 以后可能新增的盔甲件，不用跟着上游改列表。
 *
 * <p><b>只认本地玩家。</b>开关是给自己看的，别人身上的 ProjectEF 盔甲照常显示；
 * 判定用引用相等而不是 {@code equals}，避免实体比较跑到 NBT 上。
 */
public final class ProjectEArmor {

    /** ProjectEF 的 mod id 和物品命名空间一致，26.3 线没有改过。 */
    private static final String NAMESPACE = "projecte";

    private ProjectEArmor() {
    }

    /**
     * 该实体该装备槽上的物品，是否属于「要隐藏的 ProjectEF 盔甲」。
     *
     * <p>只看四个盔甲槽：手持物品不走这条渲染通道，判定它们没有意义。
     */
    public static boolean shouldHide(LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
        if (!SpaceJoyConfig.isHideProjectEArmor()) {
            return false;
        }
        if (slot != EquipmentSlot.HEAD && slot != EquipmentSlot.CHEST
                && slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET) {
            return false;
        }
        if (stack.isEmpty()) {
            return false;
        }
        // 只处理自己：别人的盔甲维持原样。
        if (entity != Minecraft.getInstance().player) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return NAMESPACE.equals(id.getNamespace());
    }
}
