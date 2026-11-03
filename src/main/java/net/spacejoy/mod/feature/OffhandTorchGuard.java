package net.spacejoy.mod.feature;

import java.util.function.IntConsumer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.spacejoy.mod.config.SpaceJoyConfig;

/**
 * 副手火把保护：副手那摞火把快用完时，不再允许从副手放置。
 *
 * <p><b>要解决的问题。</b>主手拿工具挖矿、副手放火把照明是常见玩法，但右键一下就把副手那摞
 * 火把放到只剩一支，之后摸黑找不到路。这里在放置前拦一下：副手火把数量降到配置的保留数
 * （默认 1）就拒绝这次放置，手里永远留得住最后那几支。
 *
 * <p><b>为什么用 {@code UseBlockCallback} 而不是 mixin。</b>Fabric 的交互事件在客户端
 * （{@code MultiPlayerGameMode#interactBlock}，{@code startUseItem} 走的就是它）和服务端
 * （{@code ServerPlayerGameMode#interactBlock}）各挂了一次，返回 {@link InteractionResult#FAIL}
 * 会取消原方法体：客户端连发包都不会发生，服务端也拦得住改了客户端的玩家。两端各自独立生效，
 * 不需要额外做同步。
 *
 * <p><b>为什么只认 {@link TorchBlock}。</b>红石火把是 {@code RedstoneTorchBlock}，继承的是
 * {@code BaseTorchBlock} 而不是 {@code TorchBlock}，天然被排除在外 —— 它是红石元件，按个位数量
 * 摆，不该受「留几支照明」的限制。火把、灵魂火把、铜火把（含它们的墙挂形态）都算。
 *
 * <p><b>提示为什么走 {@link IntConsumer}。</b>这个类在专用服务端也会加载，直接引用
 * {@code Minecraft} 会让服务端在校验字节码时把客户端类拖进来。所以这里只留一个回调字段，
 * 由客户端入口点 {@code net.spacejoy.mod.client.SpaceJoyClient} 在启动时填进来。
 */
public final class OffhandTorchGuard {
    /** 被拦下时的提示回调；服务端上保持空实现。 */
    private static IntConsumer feedback = keep -> {};

    private OffhandTorchGuard() {
    }

    public static void init() {
        // 事件在两端都会触发，这里只注册一次；服务端那份负责拦住绕过客户端拦截的玩家。
        UseBlockCallback.EVENT.register(OffhandTorchGuard::onUseBlock);
    }

    /** 客户端入口点在启动时调用，把「被拦下」的提示接上。 */
    public static void setFeedback(IntConsumer consumer) {
        feedback = consumer == null ? keep -> {} : consumer;
    }

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }
        if (!SpaceJoyConfig.isEnabled() || !SpaceJoyConfig.isOffhandTorchGuard()) {
            return InteractionResult.PASS;
        }
        // 0 表示不保留，等于关掉这条限制。
        int keep = SpaceJoyConfig.getOffhandTorchKeepCount();
        if (keep <= 0) {
            return InteractionResult.PASS;
        }
        // 创造模式物品不会真的减少，拦下来只会让人困惑。
        if (player.hasInfiniteMaterials()) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!isTorch(stack) || stack.getCount() > keep) {
            return InteractionResult.PASS;
        }
        // 只有客户端那一次给提示；服务端那份不发，否则同一次点击会提示两遍。
        if (level.isClientSide()) {
            feedback.accept(keep);
        }
        return InteractionResult.FAIL;
    }

    /** 火把类物品：方块是 {@link TorchBlock} 的 {@link BlockItem}。 */
    private static boolean isTorch(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof TorchBlock;
    }
}
