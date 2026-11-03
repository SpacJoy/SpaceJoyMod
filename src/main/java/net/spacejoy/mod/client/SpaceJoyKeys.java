package net.spacejoy.mod.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.spacejoy.mod.CompatFlags;
import net.spacejoy.mod.SpaceJoyMod;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.config.SpaceJoyConfigScreen;
import net.spacejoy.mod.irisdim.IrisDimClient;
import net.spacejoy.mod.irisdim.client.DimensionSwitchController;
import net.spacejoy.mod.irisdim.client.gui.ConfigScreen;

/**
 * 两个界面快捷键。
 *
 * <p>主配置界面默认绑在<b>小键盘 8</b>：主键盘区一个键都不占，小键盘又只在玩家真去按的时候才被占用，
 * 比 F 键系列更不容易撞车（F 键在整合包里被各种模组抢得很厉害）。规则编辑器默认<b>不绑键</b> ——
 * 它是低频操作，有 ModMenu 里的入口按钮、Sodium 视频设置里的那一行、还有 {@code /spacejoydim gui}
 * 三处能进，默认绑一个键反而占地方。玩家可以在「选项 → 控制 → 按键绑定 → SpaceJoyMod」里随时改。
 *
 * <p>"不绑键"用 {@code InputConstants.UNKNOWN.getValue()}（也就是键盘值 0）来构造：
 * {@code Type.getOrCreate} 是带缓存的，取出来的就是 {@code InputConstants.UNKNOWN} 那个单例本身，
 * 所以 {@link KeyMapping#isUnbound()} 会返回 true，按键注册列表里显示为「未绑定」，
 * 同时 {@code consumeClick()} 永远不会触发。
 *
 * <p>键位注册必须走 {@link KeyMappingHelper#registerKeyMapping}，不能只 new 一个
 * {@link KeyMapping} —— 那样它不会出现在按键绑定列表里，也没法保存自定义键。
 */
@Environment(EnvType.CLIENT)
public final class SpaceJoyKeys implements ClientModInitializer {
    private static final String KEY_CATEGORY_KEY = "key.categories.spacejoy_mod";
    private static final String OPEN_CONFIG_KEY = "key.spacejoy_mod.open_config";
    private static final String OPEN_RULES_KEY = "key.spacejoy_mod.open_rules";

    private static KeyMapping openConfigKey;
    private static KeyMapping openRulesKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(SpaceJoyMod.MOD_ID, "main"));

        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                OPEN_CONFIG_KEY, InputConstants.Type.KEYBOARD, InputConstants.KEY_NUMPAD8, category));
        // 0 == InputConstants.UNKNOWN 的键值，等于「默认没绑键」。
        openRulesKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                OPEN_RULES_KEY, InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), category));

        ClientTickEvents.END_CLIENT_TICK.register(SpaceJoyKeys::handleKeys);

        // 能力标签跑在客户端（服务端零开销），初始化它的缓存与换世界清理。
        ClientMobLabel.init();

        if (SpaceJoyConfig.isDebugLog()) {
            SpaceJoyMod.LOGGER.info("keybinds registered: numpad 8 = config screen, rules editor unbound by default");
        }
    }

    private static void handleKeys(Minecraft client) {
        // consumeClick 一次只返回一个按下事件；循环清空，避免连点丢失。
        while (openConfigKey.consumeClick()) {
            openConfig(client);
        }
        // 默认没绑键，但玩家可能自己绑上，所以这条路径要保留。
        while (openRulesKey.consumeClick()) {
            openRules(client);
        }
    }

    private static void openConfig(Minecraft client) {
        // 已经有界面开着时不抢焦点：玩家可能正在输入框里打字。
        if (client.gui.screen() != null) {
            return;
        }
        client.setScreenAndShow(SpaceJoyConfigScreen.create(parentOf(client)));
    }

    private static void openRules(Minecraft client) {
        if (client.gui.screen() != null) {
            return;
        }
        if (!SpaceJoyConfig.isEnabled() || !SpaceJoyConfig.isIrisDimensionShaders()) {
            return;
        }
        if (!CompatFlags.IRIS_LOADED) {
            return;
        }
        DimensionSwitchController controller = IrisDimClient.controller();
        if (controller == null) {
            return;
        }
        client.setScreenAndShow(new ConfigScreen(controller, parentOf(client)));
    }

    /** 聊天栏打开时不要把它当父界面，否则关界面会弹回一个空聊天框。 */
    private static Screen parentOf(Minecraft client) {
        Screen current = client.gui.screen();
        return current instanceof ChatScreen ? null : current;
    }
}
