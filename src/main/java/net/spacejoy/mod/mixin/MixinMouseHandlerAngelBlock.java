package net.spacejoy.mod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 浮空放置方块 / Angel Block（移植自 tweakeroo 的 tweakAngelBlock）。
 *
 * <p>原版对着空气右键没有任何反应。这里在「准星没打中任何方块」时，沿着视线方向
 * 往前取一个点做一次方块裁剪，得到一张落在空气位置上的 {@link BlockHitResult}，
 * 再走正常的 {@code useItemOn} —— 服务端收到的是标准放置包，不需要服务端装模组。
 *
 * <p>只在创造模式生效，和 tweakeroo 一致：生存模式凭空放方块太破坏平衡。
 *
 * <p><b>放完必须 cancel</b>：这是「一次右键放出两个方块」的真正原因。
 * 不 cancel 的话，原方法会继续往下把「使用物品」这个按键标记成按下，
 * 下一个 tick 的 {@code Minecraft.handleKeybinds} 就会拿它再走一次 {@code startUseItem}；
 * 而此时我们刚放下的方块已经在 3 格内，准星从「打空」变成了「打中新方块」，
 * 于是又正常放了一个 —— 两个方块就是这么来的。
 * tweakeroo 那边由 malilib 在 {@code onMouseClick} 返回 true 后 cancel 掉同一次鼠标事件，
 * 效果一样；这里我们直接 cancel 原方法。
 *
 * <p>也因此不需要抄 {@code rightClickDelay} 那把闸：按键根本不会被标记为按下，
 * 按住右键不会再触发 {@code startUseItem}。要连放就得多点几下，和参考项目手感一致。
 */
@Mixin(net.minecraft.client.MouseHandler.class)
public abstract class MixinMouseHandlerAngelBlock {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void spacejoy$angelBlockPlace(long window, net.minecraft.client.input.MouseButtonInfo buttonInfo,
                                          int action, CallbackInfo ci) {
        // action == 1 是「按下」，抬起和重复事件都不处理。
        if (action != 1) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!spacejoy$canUseAngelBlock(mc)) {
            return;
        }

        // 这里只关心按键匹配，坐标用不上，填 0 即可。
        if (!mc.options.keyUse.matchesMouse(new MouseButtonEvent(0.0D, 0.0D, buttonInfo))) {
            return;
        }

        if (spacejoy$tryPlace(mc)) {
            // 吞掉这次鼠标事件，别让原版再把使用键标记成按下（见类注释）。
            ci.cancel();
        }
    }

    private static boolean spacejoy$canUseAngelBlock(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.gameMode == null || mc.hitResult == null) {
            return false;
        }
        if (!SpaceJoyConfig.isAngelBlock() || !mc.player.isCreative()) {
            return false;
        }
        // 开着任何界面时鼠标不属于游戏世界。
        if (mc.gui != null && mc.gui.screen() != null) {
            return false;
        }
        // 只有打空了才顶替。
        return mc.hitResult.getType() == HitResult.Type.MISS;
    }

    private static boolean spacejoy$tryPlace(Minecraft mc) {
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 viewVec = mc.player.getViewVector(1.0F);
        Vec3 target = eyePos.add(viewVec.scale(SpaceJoyConfig.getAngelBlockPlacementDistance()));

        BlockHitResult hit = mc.level.clip(new ClipContext(
                eyePos, target,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.SOURCE_ONLY,
                mc.player));

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = mc.player.getItemInHand(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
                continue;
            }

            mc.gameMode.useItemOn(mc.player, hand, hit);
            mc.player.swing(hand, SwingAnimation.DEFAULT, true);
            return true;
        }

        return false;
    }
}
