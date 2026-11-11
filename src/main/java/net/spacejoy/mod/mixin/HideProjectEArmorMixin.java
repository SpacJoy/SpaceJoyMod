package net.spacejoy.mod.mixin;

import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.spacejoy.mod.client.ProjectEArmor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 ProjectEF 的盔甲不参与渲染。
 *
 * <p><b>为什么改这里。</b>26.3 的装备渲染收拢到了渲染状态：
 * {@code HumanoidMobRenderer#extractHumanoidRenderState} 逐槽问
 * {@code getEquipmentIfRenderable(entity, slot)}，把返回值写进
 * {@code HumanoidRenderState} 的 {@code head/chest/legs/feetEquipment}，之后
 * {@code HumanoidArmorLayer#submit} 只照着这四个字段画。
 *
 * <p>{@code getEquipmentIfRenderable} 是 private static，但它是「装备进不进渲染」的
 * 唯一收敛点 —— 在里面把它换成 {@code ItemStack.EMPTY}，盔甲层自然什么都不画，
 * 而装备槽、属性、减伤、附魔全部原样不动，因为这里从头到尾只碰渲染状态。
 *
 * <p><b>玩家也走这条路。</b>{@code AvatarRenderer#extractRenderState} 调的是同一个
 * {@code extractHumanoidRenderState}，所以玩家和生物的盔甲是同一处拦下来的，
 * 不需要再给玩家单独挂一份；要区分「是谁」在 {@link net.spacejoy.mod.client.ProjectEArmor}
 * 里做，这里只负责把不该画的那一件换成 {@code ItemStack.EMPTY}。
 */
@Mixin(HumanoidMobRenderer.class)
public abstract class HideProjectEArmorMixin {

    @Inject(method = "getEquipmentIfRenderable", at = @At("RETURN"), cancellable = true)
    private static void spacejoy$hideProjectEArmor(LivingEntity entity, EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack renderable = cir.getReturnValue();
        if (renderable == null || renderable.isEmpty()) {
            return;
        }
        if (ProjectEArmor.shouldHide(entity, renderable, slot)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
