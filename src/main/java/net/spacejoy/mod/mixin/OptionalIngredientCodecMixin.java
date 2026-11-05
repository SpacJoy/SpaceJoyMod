package net.spacejoy.mod.mixin;

import java.util.Optional;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.config.SpaceJoyServerConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 与 {@link BaseIngredientCodecMixin} 同源的问题，对应 polymer-core 的
 * {@code IngredientExtension.OptionalStreamCodec.encode}（IngredientExtension.java:201），
 * 用于 smithing 等「可选 ingredient」配方的序列化。修法一致：无 PacketContext 时退回原版 codec。
 */
@Mixin(targets = "eu.pb4.polymer.core.impl.interfaces.IngredientExtension$OptionalStreamCodec")
public abstract class OptionalIngredientCodecMixin {
    @Shadow
    @Final
    private StreamCodec<RegistryFriendlyByteBuf, Optional<Ingredient>> original;

    // 同 BaseIngredientCodecMixin：写完整描述符，避开编译器生成的 encode(Object,Object) 桥方法。
    @Inject(
        method = "encode(Lnet/minecraft/network/RegistryFriendlyByteBuf;Ljava/util/Optional;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void spacejoy$fallbackWhenNoPacketContext(RegistryFriendlyByteBuf buf, Optional<Ingredient> value, CallbackInfo ci) {
        if (!SpaceJoyConfig.isEnabled() || !SpaceJoyServerConfig.isPolymerRecipeFix()) {
            return;
        }
        if (PacketContext.get() == null) {
            this.original.encode(buf, value);
            ci.cancel();
        }
    }
}