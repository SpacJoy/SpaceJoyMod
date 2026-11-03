package net.spacejoy.mod.irisdim.compat.sodium;

import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.caffeinemc.mods.sodium.api.config.structure.ModOptionsBuilder;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.spacejoy.mod.SpaceJoyMod;

/**
 * 把光影规则的入口挂到 Sodium 的视频设置页里。
 *
 * <p>Sodium 把原版视频设置换成了由各模组提供的选项页面拼起来的界面，并且是通过 Fabric 入口点
 * {@code sodium:config_api_user} 来收集，而不是 mixin。Iris 自己的「光影包」那一行就是同样的东西，
 * 所以我们这里注册的是一个 <b>external page</b>（一行按钮，点下去交给我们的 {@code Screen}），
 * 而不是一页滑块。
 *
 * <p>这是整个模组里唯一不能用反射的地方：Fabric 会实例化这个类并强转成 {@link ConfigEntryPoint}，
 * 所以接口必须在编译期可见（{@code compileOnly files('libs/sodium.jar')}）。强转本身也意味着
 * 版本漂移只能在运行期发现，因此下面用 try/catch(Throwable) 兜底 —— Sodium 的 API 变了顶多丢一个
 * 入口，不该让游戏崩。
 *
 * <p>没有装 Sodium 时这个类根本不会被加载（入口点只在对应模组存在时才被调用），所以不需要额外判断。
 */
public final class SodiumConfigEntrypoint implements ConfigEntryPoint {
    /**
     * 单色方块、白色、二元 alpha —— Sodium 会用主题色给它染色。
     *
     * <p>不能拿模组图标顶替：那是彩色图，染色之后会糊成一团。
     */
    private static final Identifier ICON = Identifier.fromNamespaceAndPath(
            SpaceJoyMod.MOD_ID, "textures/gui/config_icon.png");

    /** 青色：跟 Iris 自己的品红区分开，两个相邻的标题不会被看成一组。 */
    private static final int THEME_RGB = 0xC74BFF;

    /** 标题名对应的语言键。 */
    private static final String MOD_NAME_KEY = "spacejoy_mod.sodium.modName";

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        try {
            ModOptionsBuilder mod = builder.registerOwnModOptions()
                    .setIcon(ICON)
                    .setColorTheme(builder.createColorTheme().setBaseThemeRGB(THEME_RGB))
                    .addPage(builder.createExternalPage()
                            .setName(Component.translatable("spacejoy_mod.sodium.rules"))
                            // 用方法引用而不是直接调：Sodium 是玩家点这一行时才执行它，
                            // 那时控制器早就建好了，而且它会把视频设置界面的 Screen 传进来，
                            // 按 Esc 会回到视频设置而不是主菜单。
                            .setScreenConsumer(SodiumConfigEntrypoint::openRules));

            String name = localisedHeader();
            if (name != null) {
                mod.setName(name);
            }
        } catch (Throwable t) {
            SpaceJoyMod.LOGGER.warn(
                    "Sodium's config API did not accept this mod's entry; "
                            + "the Video Settings screen will simply not list it.", t);
        }
    }

    /**
     * 点下去打开规则编辑器。
     *
     * <p>Iris 或本功能不可用时什么都不做 —— 宁可按钮没反应，也不要弹一个空界面。
     */
    private static void openRules(net.minecraft.client.gui.screens.Screen parent) {
        net.spacejoy.mod.irisdim.IrisDimClient.openRulesFrom(parent);
    }

    /**
     * 标题在玩家语言里的名字，没有对应语言项时返回 {@code null}。
     *
     * <p>Sodium 把标题当纯 {@link String} 存、用 {@code Component.literal} 画，所以这里不能像页面名
     * 那样交个 {@code Component} 让它自己解析，必须当场翻好。当场翻是安全的：Sodium 在
     * {@code buildInitialScreens} 里调这个入口点，那是初次资源加载的完成回调，语言文件刚好也在这时加载完。
     *
     * <p>返回 {@code null} 而不是占位串，这样语言文件缺键时会回落到模组元数据里的名字，
     * 而不是把键名直接印在标题上。
     */
    private static String localisedHeader() {
        return Language.getInstance().has(MOD_NAME_KEY) ? I18n.get(MOD_NAME_KEY) : null;
    }
}
