package net.spacejoy.mod.mixin.freecam;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 自由相机：射线检测用玩家自己的视角。
 *
 * <p>{@code Minecraft.pick} 默认拿相机实体去算准星指向。自由相机时相机是假的，
 * 一旦开了「本体可操作」，准星就必须跟着真玩家走，否则挖不到眼前的方块。
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftFreeCamera {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @WrapOperation(
            method = "pick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;getCameraEntity()Lnet/minecraft/world/entity/Entity;"))
    private Entity spacejoy$usePlayerForPicking(Minecraft instance, Operation<Entity> original) {
        if (SpaceJoyConfig.isFreeCamera()
                && SpaceJoyConfig.isFreeCameraPlayerInputs()
                && this.player != null) {
            return this.player;
        }

        return original.call(instance);
    }
}
