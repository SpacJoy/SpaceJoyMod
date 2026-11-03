package net.spacejoy.mod.feature.freecam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.player.ItemActivation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * 自由相机用的假玩家实体（移植自 tweakeroo 的 CameraEntity）。
 *
 * <p>为什么继承 {@code LocalPlayer}：{@code Minecraft.setCameraEntity} 拿到的实体要能提供
 * 第一人称渲染需要的一切（视角、位置、液体判定）。直接用普通 Entity 会缺一堆状态。
 * 这里构造时把输入置成 {@link Input#EMPTY}，让它永远不会自己移动，位移全部由
 * {@link #movementTick()} 手算。
 */
public class FreeCameraEntity extends LocalPlayer {
    /** 相机自己的速度，带惯性，不直接等于按键。 */
    private Vec3 motion = Vec3.ZERO;
    private boolean sprinting;
    /** 用来还原 id，让相机实体的网络 id 和原玩家一致（部分渲染路径按 id 查实体）。 */
    private Entity idSource;

    private FreeCameraEntity(Minecraft mc,
                             ClientLevel level,
                             ClientPacketListener connection,
                             StatsCounter stats,
                             net.minecraft.client.ClientRecipeBook recipeBook,
                             Input input,
                             boolean sprinting) {
        super(mc, level, connection, stats, recipeBook, input, sprinting,
                ChatAbilities.NO_RESTRICTIONS, new ItemActivation());
    }

    /**
     * 建一个相机实体，位置取玩家当前眼睛稍上方一点的位置。
     *
     * <p>用 0.125 的抬升是 tweakeroo 的做法，避免相机正好卡在方块边界上。
     */
    public static FreeCameraEntity create(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }

        LocalPlayer player = mc.player;
        FreeCameraEntity camera = new FreeCameraEntity(
                mc,
                mc.level,
                player.connection,
                player.getStats(),
                player.getRecipeBook(),
                Input.EMPTY,
                false);

        // 自由相机不受碰撞影响，否则飞进方块里会被弹开。
        camera.noPhysics = true;
        camera.setPosRaw(player.getX(), player.getY() + 0.125F, player.getZ());
        camera.setYRot(player.getYRot());
        camera.setXRot(player.getXRot());
        camera.setDeltaMovement(Vec3.ZERO);
        camera.idSource = player;

        return camera;
    }

    @Override
    public boolean isSpectator() {
        return true;
    }

    @Override
    public int getId() {
        // 复用原玩家的 id，别的代码按 id 反查实体时才不会断。
        return idSource == null ? super.getId() : idSource.getId();
    }

    /** 相机每 tick 的移动，只有本体不接管移动时才会被调用。 */
    void movementTick() {
        Input input = SpaceJoyFreeCamera.playerInput(Minecraft.getInstance());

        // 先修正上一 tick 的位置快照，否则插值渲染会抖。
        updateLastTickPosition();

        if (input.sprint()) {
            sprinting = true;
        } else if (!input.forward() && !input.backward()) {
            // 松手就不再冲刺。侧移单独按不算前进，和原版一致。
            sprinting = false;
        }

        motion = calculateMotion(motion, 0.15D, 0.4D, input);

        double forward = sprinting ? motion.x * 3.0D : motion.x;
        handleMotion(forward, motion.y, motion.z);
    }

    private void handleMotion(double forward, double up, double strafe) {
        float yaw = getYRot();
        double xFactor = Math.sin(yaw * Math.PI / 180.0D);
        double zFactor = Math.cos(yaw * Math.PI / 180.0D);
        double speed = getMoveSpeed();

        double x = (strafe * zFactor - forward * xFactor) * speed;
        double y = up * speed;
        double z = (forward * zFactor + strafe * xFactor) * speed;

        setDeltaMovement(x, y, z);
        move(MoverType.SELF, getDeltaMovement());
    }

    /** 相机飞行速度。0.07 * 10 换算成「每 tick 格数」，和 tweakeroo 的基准一致。 */
    private double getMoveSpeed() {
        return 0.07D * 10.0D;
    }

    /** 把上一 tick 的插值基准点全部对齐到当前位置。 */
    private void updateLastTickPosition() {
        xOld = getX();
        yOld = getY();
        zOld = getZ();

        xo = getX();
        yo = getY();
        zo = getZ();

        yRotO = getYRot();
        xRotO = getXRot();
        yHeadRotO = yHeadRot;
    }

    /**
     * 带惯性的速度计算（移植自 tweakeroo 的 {@code calculatePlayerMotionWithDeceleration}）。
     *
     * @param lastMotion 上一 tick 的速度
     * @param ramp       按键方向上的加速度
     * @param decel      松手后的衰减系数
     */
    private static Vec3 calculateMotion(Vec3 lastMotion, double ramp, double decel, Input input) {
        int forward = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        int strafe = (input.left() ? 1 : 0) - (input.right() ? 1 : 0);
        int vertical = (input.jump() ? 1 : 0) - (input.shift() ? 1 : 0);

        // 斜着走要比直走慢一点才是等速，原版也是这么处理的。
        double speed = forward != 0 && strafe != 0 ? 1.2D : 1.0D;

        return new Vec3(
                rampAxis(lastMotion.x, forward, ramp, decel) / speed,
                rampAxis(lastMotion.y, vertical, ramp, decel),
                rampAxis(lastMotion.z, strafe, ramp, decel) / speed);
    }

    private static double rampAxis(double current, int input, double ramp, double decel) {
        if (input != 0) {
            // 反向时先归零再加速，否则会有一瞬间速度直接反号。
            if (current * input < 0.0D) {
                current = 0.0D;
            }
            return Mth.clamp(current + ramp * input, -1.0D, 1.0D);
        }

        return current * decel;
    }
}
