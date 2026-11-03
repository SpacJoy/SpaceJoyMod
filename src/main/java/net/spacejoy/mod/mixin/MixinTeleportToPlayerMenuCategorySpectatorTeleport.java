package net.spacejoy.mod.mixin;

import net.minecraft.client.gui.spectator.PlayerMenuItem;
import net.minecraft.client.gui.spectator.SpectatorMenuItem;
import net.minecraft.client.gui.spectator.categories.TeleportToPlayerMenuCategory;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.spacejoy.mod.config.SpaceJoyConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 旁观者传送：玩家列表带上所有玩家，包括旁观者自己（移植自 tweakeroo 的 tweakSpectatorTeleport）。
 *
 * <p>原版构造时会把「已是旁观者」的玩家过滤掉。这里在构造结束后整表重建，
 * 按原版自己的排序规则重排，条目类型也换成可点击的 {@link PlayerMenuItem}。
 */
@Mixin(value = TeleportToPlayerMenuCategory.class, priority = 1001)
public abstract class MixinTeleportToPlayerMenuCategorySpectatorTeleport {
    @Shadow
    @Final
    private static Comparator<PlayerInfo> PROFILE_ORDER;

    @Shadow
    @Final
    @Mutable
    private List<SpectatorMenuItem> items;

    @Inject(method = "<init>(Ljava/util/Collection;)V", at = @At("RETURN"))
    private void spacejoy$includeAllPlayers(Collection<PlayerInfo> profiles, CallbackInfo ci) {
        if (!SpaceJoyConfig.isSpectatorTeleport() || profiles == null) {
            return;
        }

        this.items = profiles.stream()
                .sorted(PROFILE_ORDER)
                .map(entry -> (SpectatorMenuItem) new PlayerMenuItem(entry))
                .toList();
    }
}
