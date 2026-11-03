package net.spacejoy.mod.mixin;

import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 polymer-core 的 {@code IngredientExtension.BaseStreamCodec.encode} 在「无 PacketContext」场景下的 NPE。
 *
 * <p>polymer 0.18.6 原代码（polymer-core 的 IngredientExtension.java:102）：
 * <pre>
 * var context = PacketContext.get().get(PacketContext.CONNECTION);
 * if (context == null || ...) { original.encode(buf, value); return; }
 * </pre>
 * null 检查写在解引用之后：当 {@code PacketContext.get()} 返回 null（例如 TACZ 的
 * {@code CommonAssetsManager.sanitizeSyncedRecipes} 在服务端 reload 阶段对配方做「试编码」），
 * 就直接抛 {@code Cannot invoke "PacketContext.get(PacketContext$ReadKey)" because the return value of
 * "PacketContext.get()" is null}，TACZ 捕获后把该配方从 recipe_sync 中剔除，导致 JEI 合成页整片空掉。
 *
 * <p>这里在方法入口先判空，命中无上下文时直接走原版 codec 并取消原方法——
 * 与 polymer 作者本意的兜底分支（{@code original.encode(buf, value)}）完全一致，
 * 真实发包时（上下文存在）仍走 polymer 扩展路径，功能零损失。
 */
@Mixin(targets = "eu.pb4.polymer.core.impl.interfaces.IngredientExtension$BaseStreamCodec")
public abstract class BaseIngredientCodecMixin {
    @Shadow
    @Final
    private StreamCodec<RegistryFriendlyByteBuf, Ingredient> original;

    // 必须写完整描述符：record 实现 StreamCodec 时会由编译器生成桥方法 encode(Object,Object)，
    // 只写方法名 "encode" 会让 mixin 选择器命中两个候选（真实方法 + 桥方法）而产生歧义。
    @Inject(
        method = "encode(Lnet/minecraft/network/RegistryFriendlyByteBuf;Lnet/minecraft/world/item/crafting/Ingredient;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void spacejoy$fallbackWhenNoPacketContext(RegistryFriendlyByteBuf buf, Ingredient value, CallbackInfo ci) {
        if (!SpaceJoyConfig.isEnabled() || !SpaceJoyConfig.isPolymerRecipeFix()) {
            return;
        }
        if (PacketContext.get() == null) {
            this.original.encode(buf, value);
            ci.cancel();
        }
    }
}