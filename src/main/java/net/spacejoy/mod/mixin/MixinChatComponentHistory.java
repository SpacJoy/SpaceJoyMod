package net.spacejoy.mod.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.ChatComponent;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 放宽聊天记录上限（移植自 JackFred 的 More Chat History 2.0.0，改成本模组的可配置开关）。
 *
 * <p>原版把「聊天框里能回看多少条」和「输入框里按上键能翻多少条」都写死成 100：
 * <ul>
 *   <li>{@code allMessages}（聊天框全部消息）在 {@code addMessageToQueue} 里超过 100 就丢最老的</li>
 *   <li>{@code trimmedMessages}（当前已排版的可见行）在 {@code addMessageToDisplayQueue} 末尾同理</li>
 *   <li>{@code recentChat}（输入框上键历史）在 {@code addRecentChat} 里满了 100 就 {@code removeFirst}</li>
 *   <li>构造函数里 {@code new ArrayListDeque(100)} 只是初始容量，队列满了会自己扩容</li>
 * </ul>
 *
 * <p>这四处用的是同一个字面量 100，所以用 {@code @ModifyExpressionValue} 在 CONSTANT 锚点上统一替换，
 * 不必逐个 {@code @Redirect}：只换常量本身，原版的三段裁剪逻辑一行都不用重写，
 * 也不会因为别的模组改同一处而互相顶掉（{@code @ModifyExpressionValue} 可以叠）。
 *
 * <p>上限每次裁剪时现读配置，改完立刻生效。已经超出新上限的旧消息要等下一条消息进来才会被裁掉。
 */
@Mixin(ChatComponent.class)
public abstract class MixinChatComponentHistory {
    /** 构造函数里队列的初始容量，跟着配置走，省掉开局几次扩容。 */
    @ModifyExpressionValue(method = "<init>",
            at = @At(value = "CONSTANT", args = "intValue=100"))
    private int spacejoy$initialCapacity(int original) {
        return spacejoy$limit(original);
    }

    /** 聊天框的消息上限。 */
    @ModifyExpressionValue(method = "addMessageToQueue",
            at = @At(value = "CONSTANT", args = "intValue=100"))
    private int spacejoy$messageLimit(int original) {
        return spacejoy$limit(original);
    }

    /** 已排版行的上限。 */
    @ModifyExpressionValue(method = "addMessageToDisplayQueue",
            at = @At(value = "CONSTANT", args = "intValue=100"))
    private int spacejoy$trimmedLimit(int original) {
        return spacejoy$limit(original);
    }

    /** 输入框上键历史的上限。 */
    @ModifyExpressionValue(method = "addRecentChat",
            at = @At(value = "CONSTANT", args = "intValue=100"))
    private int spacejoy$recentChatLimit(int original) {
        return spacejoy$limit(original);
    }

    /**
     * 功能关掉时原样返回原版的值。
     *
     * <p>不直接返回 {@code original} 的语义差别：关掉后仍然保留原版的 100，
     * 而不是退回模组自己的默认值 —— 否则「关掉」反而会变成 16384。
     */
    private static int spacejoy$limit(int original) {
        return SpaceJoyConfig.isChatHistory() ? SpaceJoyConfig.getChatHistoryLength() : original;
    }
}
