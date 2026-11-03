package net.spacejoy.mod.mixin.freecam;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import net.spacejoy.mod.config.SpaceJoyConfig;
import net.spacejoy.mod.feature.freecam.SpaceJoyFreeCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自由相机：相机移动后把扫过的区块标脏。
 *
 * <p>原版换了相机实体会自己重建视图，Sodium 接管区块构建后这步要手动补，
 * 否则相机飞过去的区块是空的。HEAD 记旧位置、TAIL 拿新位置做差集。
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRendererFreeCameraSodium {
    @Shadow
    private ViewArea viewArea;

    @Unique
    private int spacejoy$lastChunkX = Integer.MIN_VALUE;

    @Unique
    private int spacejoy$lastChunkZ = Integer.MIN_VALUE;

    @Inject(method = "repositionCamera", at = @At("HEAD"))
    private void spacejoy$recordCameraChunk(CallbackInfo ci) {
        if (!SpaceJoyConfig.isFreeCamera() || this.viewArea == null) {
            return;
        }

        SectionPos pos = this.viewArea.getCameraSectionPos();
        this.spacejoy$lastChunkX = pos.x();
        this.spacejoy$lastChunkZ = pos.z();
    }

    @Inject(method = "repositionCamera", at = @At("TAIL"))
    private void spacejoy$rebuildSweptChunks(CallbackInfo ci) {
        if (!SpaceJoyConfig.isFreeCamera()
                || this.viewArea == null
                || this.spacejoy$lastChunkX == Integer.MIN_VALUE) {
            return;
        }

        SectionPos pos = this.viewArea.getCameraSectionPos();
        int chunkX = pos.x();
        int chunkZ = pos.z();

        SpaceJoyFreeCamera.markChunksForRebuild(
                Minecraft.getInstance(), chunkX, chunkZ,
                this.spacejoy$lastChunkX, this.spacejoy$lastChunkZ);
    }
}
