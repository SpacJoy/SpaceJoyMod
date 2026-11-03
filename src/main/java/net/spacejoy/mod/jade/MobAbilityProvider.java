package net.spacejoy.mod.jade;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.spacejoy.mod.CompatFlags;
import net.spacejoy.mod.compat.EACompat;
import net.spacejoy.mod.config.SpaceJoyConfig;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.ConfigIcon;
import snownee.jade.api.config.IPluginConfig;

/**
 * 在 Jade 的实体提示里追加「这个生物会掉什么能力」。
 * 生物没有能力时不显示任何东西，所以看到的发光生物基本就是有能力的那些。
 */
public class MobAbilityProvider implements IEntityComponentProvider {
    public static final MobAbilityProvider INSTANCE = new MobAbilityProvider();
    public static final Identifier UID =
            Identifier.fromNamespaceAndPath("spacejoy_mod", "mob_abilities");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        // 先判软依赖：EA 不在时这里直接返回，EACompat 的类永远不会被加载。
        if (!CompatFlags.EA_LOADED) {
            return;
        }
        if (!SpaceJoyConfig.isEnabled() || !SpaceJoyConfig.isJadeAbilityDisplay()) {
            return;
        }
        Entity entity = accessor.getEntity();
        if (entity instanceof Player) {
            return;
        }
        List<Component> lines = EACompat.getAbilityLines(entity);
        if (lines.isEmpty()) {
            return;
        }
        tooltip.add(Component.translatable("spacejoy_mod.jade.mob_abilities"));
        for (Component line : lines) {
            tooltip.add(line);
        }
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public ConfigIcon getConfigIcon() {
        return ConfigIcon.item(Items.EXPERIENCE_BOTTLE);
    }
}