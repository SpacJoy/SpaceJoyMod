package net.spacejoy.mod.jade;

import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import net.spacejoy.mod.CompatFlags;

/**
 * 通过 Jade 的 {@code jade} entrypoint 注册，不需要改 Jade 本体。
 *
 * <p>纯客户端特性：Everlasting Abilities 自己已经把每个实体的能力同步到客户端了
 * （RequestAbilityStorePacket / SendAbilityStorePacket），所以不需要 Jade 的服务端数据通道，
 * 连服务器时也能正常显示。
 */
public class MobAbilityJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        if (!CompatFlags.EA_LOADED) {
            return;
        }
        registration.addConfig(MobAbilityProvider.UID, MobAbilityProvider.INSTANCE.getConfigIcon(), true);
        registration.markAsClientFeature(MobAbilityProvider.UID);
        registration.registerEntityComponent(MobAbilityProvider.INSTANCE, LivingEntity.class);
    }
}