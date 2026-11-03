package net.spacejoy.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.spacejoy.mod.feature.OffhandTorchGuard;

/**
 * 客户端入口点：把「副手火把被拦下」的提示接到公共代码上。
 *
 * <p>公共代码（{@code OffhandTorchGuard}）在专用服务端也会加载，所以它不能直接引用
 * {@code Minecraft}；提示的实现放在这里，启动时通过
 * {@link OffhandTorchGuard#setFeedback(java.util.function.IntConsumer)} 注册进去。
 */
@Environment(EnvType.CLIENT)
public final class SpaceJoyClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        OffhandTorchGuard.setFeedback(SpaceJoyClient::showBlocked);
    }

    private static void showBlocked(int keep) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        // 动作栏而不是聊天框：挖矿时连续右键会反复触发，聊天框会被刷屏。
        client.gui.hud.setOverlayMessage(Component.translatable("spacejoy_mod.offhand_torch.blocked", keep), false);
    }
}
