package net.spacejoy.mod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 软依赖探测集中在这里：只碰 FabricLoader，不引用任何第三方模组的类，
 * 这样对应的模组缺失时也不会触发 NoClassDefFoundError。
 */
public final class CompatFlags {
    public static final boolean JADE_LOADED = FabricLoader.getInstance().isModLoaded("jade");
    public static final boolean EA_LOADED = FabricLoader.getInstance().isModLoaded("everlastingabilities");
    public static final boolean BUFFMOBS_LOADED = FabricLoader.getInstance().isModLoaded("buffmobs");
    public static final boolean POLYMER_LOADED = FabricLoader.getInstance().isModLoaded("polymer-core");
    public static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
    public static final boolean CLOTH_CONFIG_LOADED = FabricLoader.getInstance().isModLoaded("cloth-config");
    public static final boolean MODMENU_LOADED = FabricLoader.getInstance().isModLoaded("modmenu");

    private CompatFlags() {
    }
}
