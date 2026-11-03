package net.spacejoy.mod.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.spacejoy.mod.config.SpaceJoyConfigScreen;

/**
 * ModMenu 的配置按钮入口。
 *
 * <p>Cloth Config 是软依赖：没装的话直接返回原界面，配置按钮点下去等于没反应，
 * 也不会因为找不到 Cloth 的类而崩。
 */
@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<Screen> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            // 没装 Cloth Config 时返回原界面，别把配置按钮点成崩溃。
            return parent -> parent;
        }
        return SpaceJoyConfigScreen::create;
    }
}