package net.spacejoy.mod.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import net.spacejoy.mod.client.ClientMobLabel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把「这只生物会掉什么能力」塞进原版头顶标签的渲染通道。
 *
 * <p><b>为什么改这里。</b>{@code EntityRenderer#extractNameTags(Entity, EntityRenderState, float, double, double)}
 * 是原版唯一决定「这只生物头顶显示什么文本」的地方：它在方法里填
 * {@code EntityRenderState.nameTag}（名字）、{@code scoreText}（名字下方那一行）、
 * {@code nameTagAttachment}（文本挂点）。填完就交给 {@code submitNameDisplay} 去画，
 * 深度、遮挡、光影映射全部由原版处理 —— 这正是我们要的：不再自己碰渲染管线。
 *
 * <p><b>上下顺序。</b>{@code submitNameDisplay} 先画 {@code scoreText}，再把坐标抬高
 * {@code 9 × 1.15 × 0.025} 格画 {@code nameTag}。所以 <b>{@code nameTag} 在上、{@code scoreText} 在下</b>
 * （这也正是原版计分板「belowName」显示在名字下面的原因）。据此：
 * <ul>
 *   <li>生物没有自定义名 → 标签直接占 {@code nameTag}，跟普通命名牌一模一样；</li>
 *   <li>生物已经有名字 → 名字让到下面那一行（{@code scoreText}），标签占上面一行。
 *       这就是「被命名时上移让位」：标签始终在最上面，名字紧贴其下，两者不重叠。</li>
 *   <li>已经有计分板分数占着下面那行 → 不抢，这次不显示标签。</li>
 * </ul>
 *
 * <p><b>挂点自己算。</b>原版只有在 {@code shouldShowName} 为真时才填 {@code nameTagAttachment}，
 * 而大多数带能力的生物本来不该显示名字，那个字段是 null。所以这里直接问
 * {@code EntityAttachments} 要 {@code NAME_TAG} 挂点，不依赖原版填过没有。
 *
 * <p>为什么放弃原来的发光描边：原版描边不参与深度测试，隔着山也能看见，而且被别的实体
 * 挡住的那部分照样亮 —— 想补「被实体遮挡也要剔除」等于重写整套描边渲染。头顶标签走原版
 * 文本通道，这些全都天然正确。
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V",
            at = @At("TAIL"))
    private void spacejoy$appendAbilityLabel(Entity entity, EntityRenderState state, float partialTick,
                                             double nameDistance, double scoreDistance, CallbackInfo ci) {
        Component label = ClientMobLabel.labelFor(entity, state.distanceToCameraSq);
        if (label == null) {
            return;
        }
        Vec3 attachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, partialTick);
        if (attachment == null) {
            // 这个实体没有 NAME_TAG 挂点（理论上都有，防御性返回）。
            return;
        }
        if (state.nameTag == null) {
            // 没有名字：标签占名字的位置，跟普通命名牌完全一致。
            state.nameTagAttachment = attachment;
            state.nameTag = label;
        } else if (state.scoreText == null) {
            // 已经有名字：名字挪到下面那行，标签占上面那行。
            state.nameTagAttachment = attachment;
            state.scoreText = state.nameTag;
            state.nameTag = label;
        }
        // 下面那行已经被计分板分数占着时不覆盖，这一次就不显示标签。
    }
}
