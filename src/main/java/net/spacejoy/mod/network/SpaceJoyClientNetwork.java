package net.spacejoy.mod.network;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.spacejoy.mod.config.SpaceJoyConfigScreen;
import net.spacejoy.mod.config.SpaceJoyServerConfig;
import net.spacejoy.mod.network.SpaceJoyPackets.ConfigC2S;
import net.spacejoy.mod.network.SpaceJoyPackets.ConfigS2C;
import net.spacejoy.mod.network.SpaceJoyPackets.StatusRequestC2S;
import net.spacejoy.mod.network.SpaceJoyPackets.WaypointC2S;

/** 客户端侧：收服务端配置快照、把 GUI 改动发回服务端、上报路径点增删。 */
public final class SpaceJoyClientNetwork {
    /** 世界加载阶段 Xaero 会批量 add 路径点，那时连接还没就绪，先攒着，JOIN 后补发。 */
    private static final int MAX_PENDING = 512;
    private static final List<WaypointC2S> PENDING = new ArrayList<>();

    private SpaceJoyClientNetwork() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(ConfigS2C.TYPE, (payload, ctx) -> {
            // 单人时 integrated server 也会下发快照，但没开作弊的存档里玩家不是 OP，
            // 那会把「自己的世界」也置灰。单人一律视为可改（服务端本来就在这个 JVM 里）。
            boolean edit = isSingleplayer() || payload.canEdit();
            boolean changed = SpaceJoyServerConfig.canEdit() != edit;
            SpaceJoyServerConfig.setFromS2C(edit, payload.waypointEnabled(),
                    payload.eliteAbilityDrop(), payload.eliteAbilityMinRarity(),
                    payload.polymerRecipeFix(), payload.taczConcurrencyFix(),
                    payload.quietDisconnectEncodeError(), payload.buffMobsStateGuard(),
                    payload.buffMobsStateLimit(), payload.buffMobsStateTrimFactor());
            if (changed) {
                // 权限变了：界面开着的话重建一次，让置灰状态立刻跟上，不用关掉重开。
                // 必须在客户端线程改界面，收包是在网络线程。
                Minecraft.getInstance().execute(SpaceJoyConfigScreen::markRefreshPending);
            }
        });

        // 进服时把加载期攒下的登记包补发，保证磁盘里已有的路径点也被服务端认可。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            synchronized (PENDING) {
                for (WaypointC2S p : PENDING) {
                    try {
                        if (ClientPlayNetworking.canSend(WaypointC2S.TYPE)) {
                            ClientPlayNetworking.send(p);
                        }
                    } catch (RuntimeException ignored) {
                    }
                }
                PENDING.clear();
            }
        });
    }

    /**
     * 向服务端要一份最新的权限/配置快照。
     *
     * <p>服务端只在进服那一刻推一次 {@link ConfigS2C}，之后玩家被 {@code /op} 或
     * {@code /deop} 改了权限，客户端毫不知情——打开配置界面时看到的还是进服时那份旧状态。
     * 所以每次打开界面都主动问一次。
     *
     * <p>单人不用问：integrated server 就在同一个 JVM 里，本地那份就是权威。
     */
    public static void requestStatus() {
        if (isSingleplayer()) {
            return;
        }
        try {
            if (ClientPlayNetworking.canSend(StatusRequestC2S.TYPE)) {
                ClientPlayNetworking.send(new StatusRequestC2S());
            }
        } catch (RuntimeException ignored) {
            // 连接还没就绪（例如在主菜单按快捷键）：忽略，下次打开界面再问。
        }
    }

    /** GUI 保存一个服务端配置项：单人本地直接改（不鉴权），多人发 C2S 让服务端按 OP 复核。 */
    public static void sendConfig(String key, String value) {
        if (isSingleplayer()) {
            SpaceJoyServerConfig.set(key, value);
        } else if (ClientPlayNetworking.canSend(ConfigC2S.TYPE)) {
            ClientPlayNetworking.send(new ConfigC2S(key, value));
        }
    }

    /** 客户端建/删路径点时同步登记表。单人本地直接写，多人发 C2S（连接没好就攒着）。 */
    public static void sendWaypoint(String op, String name, int x, int y, int z, String dim) {
        if (isSingleplayer()) {
            var p = Minecraft.getInstance().player;
            if (p != null) {
                String uuid = p.getUUID().toString();
                if ("add".equals(op)) {
                    SpaceJoyServerConfig.addWaypoint(uuid, dim, x, y, z);
                } else {
                    SpaceJoyServerConfig.removeWaypoint(uuid, dim, x, y, z);
                }
            }
            return;
        }
        WaypointC2S packet = new WaypointC2S(op, name, x, y, z, dim);
        // canSend/send 只能在 play 网络线程上下文里安全调用；Xaero 在世界加载/后台 IO 线程
        // 也会触发 WaypointSet.add，这时直接调会抛 IllegalStateException。全部兜住改攒包。
        boolean sent = false;
        try {
            if (ClientPlayNetworking.canSend(WaypointC2S.TYPE)) {
                ClientPlayNetworking.send(packet);
                sent = true;
            }
        } catch (RuntimeException ignored) {
            // 落到下面攒包
        }
        if (!sent) {
            synchronized (PENDING) {
                if (PENDING.size() < MAX_PENDING) {
                    PENDING.add(packet);
                }
            }
        }
    }

    private static boolean isSingleplayer() {
        // 单人时服务端就在同一个 JVM 里，配置/路径点表直接改本地即可，不用绕网络包。
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }
}
