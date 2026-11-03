package net.spacejoy.mod.irisdim;

import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.spacejoy.mod.CompatFlags;
import net.spacejoy.mod.SpaceJoyMod;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.irisdim.client.DimensionSwitchController;
import net.spacejoy.mod.irisdim.client.gui.ConfigScreen;
import net.spacejoy.mod.irisdim.compat.iris.IrisBackendSupport;
import net.spacejoy.mod.irisdim.compat.iris.IrisBridge;
import net.spacejoy.mod.irisdim.config.ConfigStore;
import net.spacejoy.mod.irisdim.config.ModConfig;
import net.spacejoy.mod.irisdim.core.DimensionRule;

/**
 * 维度光影的客户端接线。
 *
 * <p>整个功能是纯客户端的：规则匹配看的是当前客户端所在维度，改的是 Iris 的运行时状态。
 * 没有 Iris 时这里照常初始化，但控制器会在每次应用前自己判断并放弃，配置界面也会整页置灰。
 */
@Environment(EnvType.CLIENT)
public final class IrisDimClient implements ClientModInitializer {
    private static DimensionSwitchController controller;

    public static DimensionSwitchController controller() {
        return controller;
    }

    /**
     * 打开规则编辑器，父界面用传进来的这个。
     *
     * <p>Sodium 视频设置页会把「按 Esc 应该回到的界面」传进来，这里直接用它，玩家关掉规则编辑器
     * 就回到视频设置，而不是掉到主菜单。
     *
     * <p>Iris 没装或功能被关时不打开：宁可按钮没反应，也不要弹一个全灰的空界面。
     */
    public static void openRulesFrom(Screen parent) {
        if (controller == null || !CompatFlags.IRIS_LOADED
                || !SpaceJoyConfig.isEnabled() || !SpaceJoyConfig.isIrisDimensionShaders()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        client.setScreenAndShow(new ConfigScreen(controller, parent == null ? client.gui.screen() : parent));
    }

    @Override
    public void onInitializeClient() {
        ConfigStore store = new ConfigStore(FabricLoader.getInstance().getConfigDir(), SpaceJoyMod.LOGGER);
        ModConfig config = store.loadOrCreate();
        // 维度光影现在只有一个总开关（spacejoy-mod.json 里的 irisDimensionShaders），
        // 规则文件里的 settings.enabled 跟着它走，启动时对齐一次，免得两个地方状态打架。
        config.settings.enabled = SpaceJoyConfig.isIrisDimensionShaders();
        controller = new DimensionSwitchController(store, config, SpaceJoyMod.LOGGER, IrisDimClient::announce);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 总开关或本功能关闭时整个跳过：不读规则、不碰 Iris。
            if (!SpaceJoyConfig.isEnabled() || !SpaceJoyConfig.isIrisDimensionShaders()) {
                return;
            }
            if (!CompatFlags.IRIS_LOADED) {
                return;
            }
            controller.onEndTick(client);
        });

        registerCommand();

        if (SpaceJoyConfig.isDebugLog()) {
            if (CompatFlags.IRIS_LOADED && IrisBridge.isAvailable()) {
                SpaceJoyMod.LOGGER.info("dimension shaders ready: {} rule(s) active, config at {}",
                        controller.ruleSet().rules().size(), store.configPath());
            } else {
                SpaceJoyMod.LOGGER.warn("dimension shaders idle: iris={}, api={}",
                        CompatFlags.IRIS_LOADED, IrisBridge.unavailableReason());
            }
        }
        IrisBackendSupport.unavailableReason().ifPresent(problem ->
                SpaceJoyMod.LOGGER.warn("{} Shader rules will be kept but not applied.", problem));
    }

    private static void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommands.literal("spacejoydim")
                        .then(ClientCommands.literal("status").executes(ctx -> {
                            reply(controller.statusLine());
                            for (String warning : controller.configWarnings()) {
                                reply("  ! " + warning);
                            }
                            return 1;
                        }))
                        .then(ClientCommands.literal("rules").executes(ctx -> {
                            List<DimensionRule> rules = controller.ruleSet().rules();
                            reply(rules.size() + " active rule(s), highest priority first:");
                            for (DimensionRule rule : rules) {
                                reply("  " + rule.describe());
                            }
                            return rules.size();
                        }))
                        .then(ClientCommands.literal("apply").executes(ctx -> {
                            controller.forceApply(Minecraft.getInstance());
                            return 1;
                        }))
                        .then(ClientCommands.literal("reload").executes(ctx -> {
                            controller.reloadConfig();
                            reply("config reloaded");
                            return 1;
                        }))
                        .then(ClientCommands.literal("dims").executes(ctx -> {
                            reply(controller.discovered().summary());
                            return 1;
                        }))
                        // /spacejoydim gui —— 打开规则编辑器。这是快捷键之外的第二条入口：
                        // 玩家改过键位、或者忘了 F9 时也能进来。
                        .then(ClientCommands.literal("gui").executes(ctx -> {
                            Minecraft client = Minecraft.getInstance();
                            client.setScreenAndShow(new ConfigScreen(controller, client.gui.screen()));
                            return 1;
                        }))));
    }

    private static void reply(String message) {
        Minecraft client = Minecraft.getInstance();
        SpaceJoyMod.LOGGER.info(message);
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private static void announce(String message) {
        SpaceJoyMod.LOGGER.info(message);
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && controller != null && controller.config().settings.announceInChat) {
            client.gui.hud.setOverlayMessage(Component.literal(message), false);
        }
    }
}
