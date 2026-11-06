package net.spacejoy.mod.mixin.recipebook;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 把 {@code AbstractRecipeBookScreen} 里那个 private 的配方书实例取出来。
 *
 * <p>同一个实例在容器界面上只存在一份，界面上画的是它、接点击的也是它，
 * 所以要判断「滚轮是不是落在配方书上」必须拿到它本体，不能另造一个。
 */
@Mixin(AbstractRecipeBookScreen.class)
public interface RecipeBookScreenAccessor {
    @Accessor("recipeBookComponent")
    RecipeBookComponent<?> spacejoy$getRecipeBookComponent();
}
