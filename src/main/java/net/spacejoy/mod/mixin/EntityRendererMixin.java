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
 * <p><b>标签占 {@code scoreText}，不占 {@code nameTag}。</b>{@code nameTag} 是原版「这只生物有名字」
 * 的唯一通道：往里面写文本，等于凭空给它加了个名字 —— 没名字的生物看起来就像被命名牌命名过，
 * 有名字的生物则要反过来把名字挤走。两条都不对。所以标签统一写 {@code scoreText}，也就是
 * 原版名字下方那一行（计分板 {@code BELOW_NAME} 用的同一格），名字本身一个字都不动：
 * <ul>
 *   <li>有命名牌名字的生物：名字照旧画在 {@code nameTag}，能力标签挂在它下面那一行</li>
 *   <li>没有名字的生物：只有标签这一行，位置就在名字本该在的地方 ——
 *       {@code submitNameDisplay} 只在 {@code nameTag} 非空时才向下平移，所以标签正好贴着挂点</li>
 *   <li>计分板已经占了 {@code BELOW_NAME}（{@code scoreText != null}）：不画标签，让给计分板</li>
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
        if (state.scoreText != null) {
            // 计分板的名字下方显示占着这一格，让给它。
            return;
        }
        Vec3 attachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, partialTick);
        if (attachment == null) {
            // 这个实体没有 NAME_TAG 挂点（理论上都有，防御性返回）。
            return;
        }
        state.nameTagAttachment = attachment;
        state.scoreText = label;
        // nameTag 一个字都不动：那是原版「有名字」的通道，写进去等于凭空给生物起名。
    }
}
