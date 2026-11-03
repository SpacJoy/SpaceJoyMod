package net.spacejoy.mod.mixin.freecam;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 自由相机：实体可见性剔除按玩家位置算。
 *
 * <p>相机跑远以后，玩家身边的实体会被当成「视野外」而不提取。这里把那处
 * {@code Camera.entity()} 的调用换成真玩家，保证本体附近的一切照常渲染。
 */
@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractorFreeCamera {
    @WrapOperation(
            method = "extractVisibleEntities",
            require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;entity()Lnet/minecraft/world/entity/Entity;",
                    ordinal = 3))
    private Entity spacejoy$usePlayerForVisibility(Camera camera, Operation<Entity> original) {
        if (SpaceJoyConfig.isFreeCamera()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                return mc.player;
            }
        }

        return original.call(camera);
    }
}
