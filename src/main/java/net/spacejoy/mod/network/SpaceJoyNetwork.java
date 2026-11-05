package net.spacejoy.mod.network;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.spacejoy.mod.CompatFlags;
import net.spacejoy.mod.compat.SwthbCompat;
import net.spacejoy.mod.config.SpaceJoyServerConfig;
import net.spacejoy.mod.network.SpaceJoyPackets.ConfigC2S;
import net.spacejoy.mod.network.SpaceJoyPackets.ConfigS2C;
import net.spacejoy.mod.network.SpaceJoyPackets.StatusRequestC2S;
import net.spacejoy.mod.network.SpaceJoyPackets.WaypointC2S;

/**
 * 服务端侧：注册自定义包、配置广播、路径点登记，以及 {@code /sjwp} 传送命令。
 *
 * <p>安全模型：配置改动 {[@code ConfigC2S]} 在服务端用 {@code PlayerList.isOp} 重新鉴权，
 * 客户端 GUI 的置灰只是显示；{@code /sjwp} 不放 OP，但只在「坐标已登记过」时才传送，
 * 非 OP 手敲任意坐标也传不了（表里没有）。
 */
public final class SpaceJoyNetwork {
    private SpaceJoyNetwork() {
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(ConfigC2S.TYPE, ConfigC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(WaypointC2S.TYPE, WaypointC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StatusRequestC2S.TYPE, StatusRequestC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ConfigS2C.TYPE, ConfigS2C.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ConfigC2S.TYPE, SpaceJoyNetwork::onConfig);
        ServerPlayNetworking.registerGlobalReceiver(WaypointC2S.TYPE, SpaceJoyNetwork::onWaypoint);
        ServerPlayNetworking.registerGlobalReceiver(StatusRequestC2S.TYPE, SpaceJoyNetwork::onStatusRequest);

        // 玩家进服：下发当前权威配置 + 这个人能不能改（OP）。
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.player;
            if (p != null) {
                ServerPlayNetworking.send(p, SpaceJoyServerConfig.snapshot(isOp(server, p)));
            }
        });

        CommandRegistrationCallback.EVENT.register(SpaceJoyNetwork::registerCommands);
    }

    private static boolean isOp(MinecraftServer server, ServerPlayer player) {
        return server.getPlayerList().isOp(player.nameAndId());
    }

    /**
     * 主动把「当前这个人的权限快照」推给他。
     *
     * <p>由 {@code PlayerListOpSyncMixin} 在 {@code /op}、{@code /deop} 之后调用，
     * 也让客户端打开配置界面时的那次查询复用同一条路径。
     */
    public static void pushStatus(MinecraftServer server, ServerPlayer player) {
        ServerPlayNetworking.send(player, SpaceJoyServerConfig.snapshot(isOp(server, player)));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
                                         CommandBuildContext buildContext,
                                         Commands.CommandSelection selection) {
        dispatcher.register(Commands.literal("sjwp")
                .then(Commands.argument("coords", StringArgumentType.greedyString())
                        .executes(SpaceJoyNetwork::executeSjwp)));
    }

    /** 收到 GUI 的配置改动：只在服务端确认是 OP 才写，写完广播给所有人。 */
    private static void onConfig(ConfigC2S payload, ServerPlayNetworking.Context ctx) {
        MinecraftServer server = ctx.server();
        ServerPlayer player = ctx.player();
        if (!isOp(server, player)) {
            player.sendSystemMessage(Component.literal("§c只有管理员能修改服务端设置"));
            // 顺手把真实权限推回去：客户端手里的「我是 OP」可能是进服时那份旧快照，
            // 界面会立刻按新状态置灰，不用等玩家关掉重开。
            ServerPlayNetworking.send(player, SpaceJoyServerConfig.snapshot(false));
            return;
        }
        SpaceJoyServerConfig.set(payload.key(), payload.value());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, SpaceJoyServerConfig.snapshot(isOp(server, p)));
        }
    }

    /** 客户端打开配置界面时来问一次：按当前连接重新查 OP，把最新快照推回去。 */
    private static void onStatusRequest(StatusRequestC2S payload, ServerPlayNetworking.Context ctx) {
        pushStatus(ctx.server(), ctx.player());
    }

    /** 客户端建点/删点时同步登记表；不需要 OP（跟 /sethome 一样，登记自己的点）。 */
    private static void onWaypoint(WaypointC2S payload, ServerPlayNetworking.Context ctx) {
        if (!SpaceJoyServerConfig.isWaypointEnabled()) {
            return; // 总开关关掉时联动整体失效
        }
        ServerPlayer player = ctx.player();
        String uuid = player.getUUID().toString();
        if ("add".equals(payload.op())) {
            SpaceJoyServerConfig.addWaypoint(uuid, payload.dimension(),
                    payload.x(), payload.y(), payload.z());
        } else if ("del".equals(payload.op())) {
            SpaceJoyServerConfig.removeWaypoint(uuid, payload.dimension(),
                    payload.x(), payload.y(), payload.z());
        }
    }

    /** Xaero 路径点传送实际执行的命令：只认表里登记过的坐标。 */
    private static int executeSjwp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("只有玩家能用这条命令"));
            return 0;
        }
        if (!SpaceJoyServerConfig.isWaypointEnabled()) {
            player.sendSystemMessage(Component.literal("§c服务端已关闭路径点传送"));
            return 0;
        }
        String[] parts = StringArgumentType.getString(ctx, "coords").trim().split("\\s+");
        if (parts.length < 3) {
            player.sendSystemMessage(Component.literal("§c用法：/sjwp <x> <y> <z> [yaw]"));
            return 0;
        }
        double x;
        double y;
        double z;
        float yaw = player.getYRot();
        try {
            x = Double.parseDouble(parts[0]);
            y = Double.parseDouble(parts[1]);
            z = Double.parseDouble(parts[2]);
            if (parts.length >= 4) {
                yaw = Float.parseFloat(parts[3]);
            }
        } catch (NumberFormatException e) {
            player.sendSystemMessage(Component.literal("§c坐标不是数字"));
            return 0;
        }
        String dim = player.level().dimension().identifier().toString();
        if (!SpaceJoyServerConfig.hasWaypoint(player.getUUID().toString(), dim, x, y, z)) {
            player.sendSystemMessage(Component.literal("§c这个坐标没有登记过的路径点（先在地图上建一个路径点）"));
            return 0;
        }
        // 同维度传送；Xaero 的 y 常带 0.5，直接用它给的坐标。
        // 先记返回点再传送：和 /home、/warp 的顺序一致，否则 /back 拿不到「之前的位置」。
        if (CompatFlags.SWTHB_LOADED) {
            SwthbCompat.saveBackPoint(player);
        }
        TeleportTransition transition = new TeleportTransition(player.level(),
                new Vec3(x, y, z), player.getDeltaMovement(), yaw, player.getXRot(),
                TeleportTransition.DO_NOTHING);
        Runnable onArrived = () -> player.sendSystemMessage(Component.literal("§a已传送到路径点"));
        if (CompatFlags.SWTHB_LOADED) {
            // 走它的通道：传送延时这些设置对路径点一样生效，落地后才提示。
            SwthbCompat.teleport(player, transition, onArrived);
        } else {
            player.teleport(transition);
            onArrived.run();
        }
        return 1;
    }
}
