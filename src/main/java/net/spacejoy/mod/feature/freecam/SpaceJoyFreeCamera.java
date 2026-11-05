package net.spacejoy.mod.feature.freecam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.spacejoy.mod.config.SpaceJoyConfig;

import java.util.function.Consumer;

/**
 * 自由相机（移植自 tweakeroo 的 tweakFreeCamera）。
 *
 * <p>做法很直接：用 {@code Minecraft.setCameraEntity} 把观察点换成一个假玩家
 * {@link FreeCameraEntity}，渲染便跟着相机走，而真正的玩家实体留在原地。
 *
 * <p>相机开着时本体一律冻结：不跟着 WASD 走，也不挖、不放、不攻击。只有
 * {@code freeCameraPlayerInputs} 一个开关，用来放开本体的操作。
 *
 * <p><b>状态只允许接管一次。</b>配置界面关窗时会把每个开关都回写一遍，
 * 于是「相机开着 + 关窗」就会再调一次 {@code setState(true)}。要是那时又去抓一次
 * 「原版相机实体」，抓到的就成了我们自己建的假相机，之后关闭相机等于把视角还原到假相机上，
 * 表现为视角卡住、位置诡异、转头无效。所以接管与否由 {@link #applied} 单独记，
 * 与 {@link #camera} 是否存在解耦：只有没接管过才记录原值。
 */
public final class SpaceJoyFreeCamera {
    private static FreeCameraEntity camera;
    private static Entity originalCameraEntity;
    private static boolean originalCameraWasPlayer;
    private static boolean originalSmartCull;
    private static Vec3 cameraMotion = Vec3.ZERO;
    private static boolean sprinting;
    /** 渲染进行到第一人称阶段时为 true，用来让玩家本体不被画出来。 */
    private static boolean freeCameraSpectator;
    private static float cameraYaw;
    private static float cameraPitch;

    /**
     * 是否已经把自己的相机接管进 {@code Minecraft}。
     *
     * <p>不能用 {@link #camera} 判断：相机实体存在不代表已经接管，接管了也不该被重复接管。
     */
    private static boolean applied;

    /** 接管时挂靠的玩家对象，用来发现「死亡重生后玩家换了个实例」。 */
    private static LocalPlayer appliedPlayer;

    /** 开关状态变化的提示回调，参数是新的开关状态；服务端 / 无界面环境下保持空实现。 */
    private static Consumer<Boolean> feedback = enabled -> {};

    private SpaceJoyFreeCamera() {
    }

    /** 客户端入口点在启动时调用，把开关提示接上。 */
    public static void setFeedback(Consumer<Boolean> consumer) {
        feedback = consumer == null ? enabled -> {} : consumer;
    }

    // ---------------------------------------------------------------- 开关

    /**
     * 统一的开/关入口：配置项与快捷键都走这里。
     *
     * <p>切维度或退出世界时会以 {@code false} 调用，所以这里必须能安全处理空世界 ——
     * 那时只把配置值写回去，等进了世界再真正建立相机。
     *
     * <p>重复调用（同一个状态再来一次）是安全的：已经接管就不会再抓一遍原值。
     */
    public static void setState(boolean enabled) {
        Minecraft mc = Minecraft.getInstance();
        boolean changed = SpaceJoyConfig.isFreeCamera() != enabled;

        SpaceJoyConfig.setFreeCamera(enabled);

        if (mc.level == null || mc.player == null) {
            // 没有世界时无法建相机实体，先记下开关，进世界后由 tick 补上。
            return;
        }

        if (changed) {
            // 只在真的有世界时提示，否则进服、退服也会弹一条莫名其妙的「已关闭」。
            feedback.accept(enabled);
        }

        if (enabled) {
            createCamera(mc);
            applyCamera(mc);
        } else {
            restoreCamera(mc);
        }
    }

    /** 切换开关，给快捷键用。 */
    public static void toggle() {
        setState(!SpaceJoyConfig.isFreeCamera());
    }

    /**
     * 离开世界 / 切维度时调用。
     *
     * <p>此时旧世界的实体全部失效，绝不能留着相机实体跨世界引用；
     * 但配置里的开关保留，回到世界里会自动重建相机。
     */
    public static void onLevelUnload() {
        clearState();
    }

    /**
     * 刚进世界时调用：按参考项目的做法，进世界一律先关掉自由相机。
     *
     * <p>上次退出时相机是开的，重新进来若直接接管，玩家会以上次的相机位置开始，
     * 而那多半已经在别处，体验很差。
     */
    public static void onJoinWorld() {
        clearState();
        SpaceJoyConfig.setFreeCamera(false);
    }

    private static void clearState() {
        camera = null;
        originalCameraEntity = null;
        originalCameraWasPlayer = false;
        applied = false;
        appliedPlayer = null;
        cameraMotion = Vec3.ZERO;
        sprinting = false;
        freeCameraSpectator = false;
    }

    public static boolean isActive() {
        return camera != null;
    }

    /**
     * 相机开着时本体始终冻结：不吃输入、不跟着 WASD 走。
     *
     * <p>这是固定的行为，没有开关 —— 自由相机的意义就是身体留在原地。
     */
    public static boolean shouldPreventPlayerMovement() {
        return SpaceJoyConfig.isFreeCamera();
    }

    /** 本体不能操作方块与攻击。 */
    public static boolean shouldPreventPlayerInputs() {
        return SpaceJoyConfig.isFreeCamera() && !SpaceJoyConfig.isFreeCameraPlayerInputs();
    }

    /**
     * 目标是不是「玩家自己」或「相机实体」。
     *
     * <p>{@link FreeCameraEntity#getId()} 直接返回原玩家的网络 id，所以服务端收到攻击包时
     * 是按玩家 id 去查实体的：查不到 → {@code invalid_entity_attacked} 断线（日志里那句
     * 「tried to attack an invalid entity」），查到了是自己 → 同样拒绝。
     *
     * <p>因此这类目标必须一律拦掉，不能只看 {@link #shouldPreventPlayerInputs()}：
     * 那个开关只管「本体能不能操作」，管不到「准星落在相机上」这种自伤发包。
     */
    public static boolean isSelfOrCameraTarget(Entity target) {
        if (target == null) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            // 没有玩家就没有可攻击的自己，交给原版逻辑。
            return false;
        }

        if (target == mc.player || target instanceof FreeCameraEntity) {
            return true;
        }

        if (camera != null && target == camera) {
            return true;
        }

        // 相机复用了玩家的 id，其它路径可能拿到包装过的同 id 实体。
        return target.getId() == mc.player.getId();
    }

    public static boolean originalCameraWasPlayer() {
        return originalCameraWasPlayer;
    }

    public static boolean isSpectatorPhase() {
        return freeCameraSpectator;
    }

    public static void setSpectatorPhase(boolean value) {
        freeCameraSpectator = value;
    }

    public static Vec3 cameraMotion() {
        return cameraMotion;
    }

    // ---------------------------------------------------------------- 每 tick

    /**
     * 每客户端 tick 调一次，处理相机移动。
     */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        // 切维度时 mc.level 换成了新世界，但相机还指着旧世界的实体。
        // 这种跨世界引用必须立刻丢掉，否则相机停在原地不动、提交渲染时还会读到已卸载的区块。
        if (camera != null && camera.level() != mc.level) {
            clearState();
        }

        // 死亡重生会换一个新的 LocalPlayer 实例，旧实例的相机再挂着就没意义了。
        if (applied && appliedPlayer != null && appliedPlayer != mc.player) {
            restoreCamera(mc);
            return;
        }

        // 一死就自动关掉自由相机：死亡界面里要能点头、点按钮，相机还挂着会挡操作。
        // 这里改的是配置本身，重生后不会自己再打开。
        if (SpaceJoyConfig.isFreeCamera() && mc.player != null && mc.player.isDeadOrDying()) {
            setState(false);
            return;
        }

        if (!SpaceJoyConfig.isFreeCamera()) {
            // 配置被界面关掉了，但相机还挂着 —— 补一次收尾。
            if (applied || camera != null) {
                restoreCamera(mc);
            }
            return;
        }

        if (camera == null) {
            if (mc.level != null && mc.player != null) {
                // 进世界 / 切维度之后开关还开着：把相机补建出来。
                createCamera(mc);
                applyCamera(mc);
            }
            return;
        }

        camera.movementTick();
    }

    // ---------------------------------------------------------------- 相机实体生命周期

    private static void createCamera(Minecraft mc) {
        if (camera != null) {
            return;
        }

        camera = FreeCameraEntity.create(mc);
        if (camera == null) {
            SpaceJoyConfig.setFreeCamera(false);
            return;
        }

        cameraYaw = camera.getYRot();
        cameraPitch = camera.getXRot();
    }

    /**
     * 把相机接管进游戏。
     *
     * <p>原值只记一次。已经接管过就什么都不做，否则重复接管会把「原版相机」
     * 记成我们自己的假相机，关闭时还原到假相机上，视角就锁死了。
     */
    private static void applyCamera(Minecraft mc) {
        if (applied || camera == null) {
            return;
        }

        originalCameraEntity = mc.getCameraEntity();
        originalCameraWasPlayer = originalCameraEntity == mc.player;
        originalSmartCull = mc.smartCull;
        appliedPlayer = mc.player;
        applied = true;

        mc.setCameraEntity(camera);
        // 相机离开玩家身边后，视锥剔除必须关掉，否则画面会整块消失。
        mc.smartCull = false;
    }

    private static void restoreCamera(Minecraft mc) {
        if (!applied) {
            // 没接管过就没什么可还原的，只把残留的相机实体丢掉。
            clearState();
            return;
        }

        int lastChunkX = camera == null ? 0 : Math.floorDiv((int) Math.floor(camera.getX()), 16);
        int lastChunkZ = camera == null ? 0 : Math.floorDiv((int) Math.floor(camera.getZ()), 16);

        // 还原目标永远是「真正的玩家」优先：死亡重生后 mc.player 已经是新实例，
        // 而 originalCameraEntity 可能指向旧实例。
        Entity target = originalCameraWasPlayer || originalCameraEntity == null
                || originalCameraEntity == camera
                ? mc.player
                : originalCameraEntity;

        if (target != null) {
            mc.setCameraEntity(target);
        }
        mc.smartCull = originalSmartCull;

        if (camera != null) {
            markChunksForRebuildOnDeactivation(mc, lastChunkX, lastChunkZ);
        }

        clearState();
    }

    /** 相机离开后，它停留过的区块要重新构建，否则会留下空洞。 */
    private static void markChunksForRebuildOnDeactivation(Minecraft mc, int lastChunkX, int lastChunkZ) {
        Entity cameraEntity = mc.getCameraEntity();
        if (mc.level == null || mc.levelRenderer == null || cameraEntity == null) {
            return;
        }

        int chunkX = Math.floorDiv((int) Math.floor(cameraEntity.getX()), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(cameraEntity.getZ()), 16);

        markChunksForRebuild(mc, chunkX, chunkZ, lastChunkX, lastChunkZ);
    }

    /**
     * 把 from 到 to 之间扫过的区块标脏。
     *
     * <p>只处理真正变化的那条轴：沿 Z 走就刷整排 X，反之亦然。
     */
    public static void markChunksForRebuild(Minecraft mc, int chunkX, int chunkZ, int lastChunkX, int lastChunkZ) {
        if (mc.level == null || mc.levelRenderer == null) {
            return;
        }
        if (chunkX == lastChunkX && chunkZ == lastChunkZ) {
            return;
        }

        int viewDistance = mc.options.renderDistance().get();
        int minX = Math.min(chunkX, lastChunkX);
        int maxX = Math.max(chunkX, lastChunkX);
        int minZ = Math.min(chunkZ, lastChunkZ);
        int maxZ = Math.max(chunkZ, lastChunkZ);

        if (chunkX != lastChunkX) {
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = chunkZ - viewDistance; cz <= chunkZ + viewDistance; cz++) {
                    markChunkForReRender(mc, cx, cz);
                }
            }
        }

        if (chunkZ != lastChunkZ) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                for (int cx = chunkX - viewDistance; cx <= chunkX + viewDistance; cx++) {
                    markChunkForReRender(mc, cx, cz);
                }
            }
        }
    }

    private static void markChunkForReRender(Minecraft mc, int chunkX, int chunkZ) {
        if (!isClientChunkLoaded(mc, chunkX, chunkZ)) {
            return;
        }

        // 1.18 之后世界高度超出 16 段，这里沿用 tweakeroo 的固定 16 段循环。
        for (int sectionY = 0; sectionY < 16; sectionY++) {
            mc.levelExtractor.setSectionDirty(chunkX, sectionY, chunkZ);
        }
    }

    private static boolean isClientChunkLoaded(Minecraft mc, int chunkX, int chunkZ) {
        if (mc.level == null) {
            return false;
        }

        return mc.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
    }

    // ---------------------------------------------------------------- 转向

    /** 自由相机模式下，鼠标位移转给相机而不是玩家。 */
    public static void updateCameraRotations(float deltaYaw, float deltaPitch) {
        if (camera == null) {
            return;
        }

        // 0.15 是 tweakeroo 定下的手感系数。
        float newYaw = cameraYaw + deltaYaw * 0.15F;
        float newPitch = Mth.clamp(cameraPitch + deltaPitch * 0.15F, -90.0F, 90.0F);

        setCameraRotations(newYaw, newPitch);
    }

    private static void setCameraRotations(float yaw, float pitch) {
        if (camera == null) {
            return;
        }

        cameraYaw = yaw;
        cameraPitch = pitch;
        camera.setYRot(yaw);
        camera.setXRot(pitch);
        camera.yRotO = yaw;
        camera.xRotO = pitch;
    }

    /** 给 {@link FreeCameraEntity#movementTick()} 读玩家按键用。 */
    static Input playerInput(Minecraft mc) {
        if (mc.player == null) {
            return Input.EMPTY;
        }

        ClientInput input = mc.player.input;
        return input == null ? Input.EMPTY : input.keyPresses;
    }

    static boolean isSprinting() {
        return sprinting;
    }

    static void setSprinting(boolean value) {
        sprinting = value;
    }

    static void setCameraMotion(Vec3 motion) {
        cameraMotion = motion;
    }
}
