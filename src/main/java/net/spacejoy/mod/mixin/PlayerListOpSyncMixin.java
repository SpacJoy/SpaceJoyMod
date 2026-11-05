package net.spacejoy.mod.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.spacejoy.mod.network.SpaceJoyNetwork;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家被 {@code /op}、{@code /deop} 之后，立刻把新的权限快照推给他。
 *
 * <p>原版只在「进服」和「权限等级变化」时给客户端发自己的权限等级，
 * 我们的配置界面需要知道的是「这个人现在能不能改服务端设置」，
 * 所以在这一刻补一包过去。客户端收到后发现状态变了就重建界面，
 * 不需要界面开着定时问服务端（那样白烧服务器带宽）。
 *
 * <p>只挂 {@code op}/{@code deop} 两个人会点到的入口；
 * 直接编辑 ops.json 再 reload 这种罕见路径不覆盖（重新打开界面时问一次就够了）。
 */
@Mixin(PlayerList.class)
public abstract class PlayerListOpSyncMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "op(Lnet/minecraft/server/players/NameAndId;)V", at = @At("TAIL"))
    private void spacejoy$afterOp(NameAndId id, CallbackInfo ci) {
        spacejoy$pushStatus(id);
    }

    @Inject(method = "deop(Lnet/minecraft/server/players/NameAndId;)V", at = @At("TAIL"))
    private void spacejoy$afterDeop(NameAndId id, CallbackInfo ci) {
        spacejoy$pushStatus(id);
    }

    private void spacejoy$pushStatus(NameAndId id) {
        // 只推给在线的那个人；离线的人进服时 JOIN 事件会补上。
        ServerPlayer player = server.getPlayerList().getPlayer(id.id());
        if (player != null) {
            SpaceJoyNetwork.pushStatus(server, player);
        }
    }
}
