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
 * 副手火把保护：副手那摞火把快用完时，不再允许从副手放置，永远留几支照明。
 *
 * <p><b>纯客户端功能。</b>事件只在客户端入口 {@code SpaceJoyClient} 里注册一次：客户端
 * 返回 {@link InteractionResult#FAIL} 会直接取消 {@code MultiPlayerGameMode#interactBlock}，
 * 连 {@code ServerboundUseItemOnPacket} 都不会发出去，服务端那份永远不触发 —— 单人/进服
 * 行为一致，也没有「进服后等服务端下发配置」的窗口期。这是防手滑的本地便利功能，
 * 没有作弊动机，不需要服务端权威配置、不需要 OP 权限。
 *
 * <p><b>取值语义（与配置界面、语言文件一致）：</b>
 * <ul>
 *   <li>{@code -1}（TORCH_KEEP_ALWAYS）：整摞都不许从副手放置（最强保护）</li>
 *   <li>{@code 0}：不保留，等于关掉这条限制</li>
 *   <li>{@code 1~16}：副手至少留下这么多支，剩到这个数就拦</li>
 * </ul>
 *
 * <p><b>为什么只认 {@link TorchBlock}。</b>红石火把是 {@code RedstoneTorchBlock}，继承
 * {@code BaseTorchBlock} 而不是 {@code TorchBlock}，天然被排除 —— 它是红石元件，不该受
 * 「留几支照明」的限制。火把、灵魂火把、铜火把（含墙挂形态）都算。
 *
 * <p><b>提示为什么走 {@link IntConsumer}。</b>这个类不直接引用 {@code Minecraft}，
 * 由客户端入口把动作栏提示接进来，保持公共代码干净。
 */
public final class OffhandTorchGuard {
    /** 被拦下时的提示回调，参数是副手火把的**实际剩余数量**。 */
    private static IntConsumer feedback = remaining -> {};

    private OffhandTorchGuard() {
    }

    /** 只在客户端入口点调用一次。 */
    public static void init() {
        UseBlockCallback.EVENT.register(OffhandTorchGuard::onUseBlock);
    }

    /** 客户端入口点在启动时调用，把「被拦下」的提示接上。 */
    public static void setFeedback(IntConsumer consumer) {
        feedback = consumer == null ? remaining -> {} : consumer;
    }

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.OFF_HAND) {
            return InteractionResult.PASS;
        }
        if (!SpaceJoyConfig.isEnabled() || !SpaceJoyConfig.isOffhandTorchGuard()) {
            return InteractionResult.PASS;
        }
        int keep = SpaceJoyConfig.getOffhandTorchKeepCount();
        if (keep == 0) {
            // 0 = 不保留 = 关掉这条限制，永远放行。
            return InteractionResult.PASS;
        }
        // 创造模式物品不会真的减少，拦下来只会让人困惑。
        if (player.hasInfiniteMaterials()) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!isTorch(stack)) {
            return InteractionResult.PASS;
        }
        // -1（TORCH_KEEP_ALWAYS）= 全拦；1~16 = 剩到不超过 keep 就拦。
        if (keep != SpaceJoyConfig.TORCH_KEEP_ALWAYS && stack.getCount() > keep) {
            return InteractionResult.PASS;
        }
        // 提示里报的是「现在还剩几支」，不是配置值，否则玩家会看到跟手里对不上的数字。
        feedback.accept(stack.getCount());
        return InteractionResult.FAIL;
    }

    /** 火把类物品：方块是 {@link TorchBlock} 的 {@link BlockItem}。 */
    private static boolean isTorch(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof TorchBlock;
    }
}
