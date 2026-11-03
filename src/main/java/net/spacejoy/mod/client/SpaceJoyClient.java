package net.spacejoy.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.spacejoy.mod.feature.OffhandTorchGuard;
import net.spacejoy.mod.feature.freecam.SpaceJoyFreeCamera;
import net.minecraft.network.chat.Component;

/**
 * 客户端入口点：接上所有只在客户端跑的每 tick 逻辑，以及副手火把的提示。
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
        SpaceJoyFreeCamera.setFeedback(SpaceJoyClient::showFreeCameraToggled);

        // 自由相机每 tick 推进一次：相机自己飞，或者跟着玩家走。
        ClientTickEvents.END_CLIENT_TICK.register(client -> SpaceJoyFreeCamera.onClientTick());

        // 刚进世界时把自由相机清成关闭 —— 上次退出时是开着的，也不该一进来就接管视角。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SpaceJoyFreeCamera.onJoinWorld());

        // 换世界 / 切维度时相机实体必须丢掉，旧世界的实体不能跨世界引用。
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpaceJoyFreeCamera.onLevelUnload());
    }

    private static void showBlocked(int remaining) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        // 动作栏而不是聊天框：挖矿时连续右键会反复触发，聊天框会被刷屏。
        // 参数是副手**当前剩余**的支数，不是配置上限。
        client.gui.hud.setOverlayMessage(Component.translatable("spacejoy_mod.offhand_torch.blocked", remaining), false);
    }

    private static void showFreeCameraToggled(boolean enabled) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }
        // 动作栏：开关是高频操作，聊天框里留一行没意义。
        client.gui.hud.setOverlayMessage(
                Component.translatable(enabled
                        ? "spacejoy_mod.free_camera.enabled"
                        : "spacejoy_mod.free_camera.disabled"), false);
    }
}
