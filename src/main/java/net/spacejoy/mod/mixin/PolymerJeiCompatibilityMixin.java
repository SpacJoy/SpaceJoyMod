package net.spacejoy.mod.mixin;

import java.util.Collection;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.runtime.IIngredientManager;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 修掉 polymer 的 JEI 插件在「一个物品都没有」时抛
 * {@code IllegalArgumentException: ingredients must not be empty}。
 *
 * <p>polymer 的 {@code JeiCompatibility.update} 是这样写的：
 * <pre>{@code
 * var stacks = new ArrayList<ItemStack>();
 * CompatUtils.iterateItems(stacks::add);              // 只收集 polymer 自己的物品组
 * manager.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks);
 * }</pre>
 * 而 {@code CompatUtils.iterateItems} 只遍历 {@code CreativeModeTab.Type.CATEGORY} 的分类，
 * 并把 {@code polymer$getStacksGroup()} / {@code polymer$getStacksSearch()} 里的内容倒出来 ——
 * 也就是**只包含服务器用 polymer 下发过的自定义物品**，不含任何原版物品。
 *
 * <p>当服务器没装 polymer、或者装了但这次没下发自定义物品时，这个集合就是空的；
 * JEI 的 {@code IngredientManager.addIngredientsAtRuntime} 会先做
 * {@code ErrorUtil.checkNotEmpty}，于是抛异常。polymer 自己 catch 住并
 * {@code printStackTrace()}，所以游戏不崩，只是每次进世界都在日志里留一段 STDERR 堆栈，
 * 而且这次 JEI 的运行时物品注册被整个跳过。
 *
 * <p>这里只拦这一次调用：集合为空就跳过（本来也没有东西可加），非空照常交给 JEI。
 * 用 {@code @WrapOperation} 而不是 {@code @Redirect}，避免和 polymer 自己（或别的 mod）
 * 对同一调用点的重定向抢注入点；
 * </p>
 */
@Mixin(targets = "eu.pb4.polymer.core.impl.client.compat.JeiCompatibility", remap = false)
public abstract class PolymerJeiCompatibilityMixin {
    @WrapOperation(
            method = "update",
            at = @At(value = "INVOKE",
                    target = "Lmezz/jei/api/runtime/IIngredientManager;addIngredientsAtRuntime(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;)V"),
            require = 0
    )
    private static void spacejoy$skipEmptyIngredientSync(IIngredientManager manager, IIngredientType<?> type,
                                                         Collection<?> ingredients, Operation<Void> original) {
        if (!SpaceJoyConfig.isEnabled() || !SpaceJoyConfig.isPolymerRecipeFix()) {
            original.call(manager, type, ingredients);
            return;
        }
        if (ingredients == null || ingredients.isEmpty()) {
            return;
        }
        original.call(manager, type, ingredients);
    }
}
