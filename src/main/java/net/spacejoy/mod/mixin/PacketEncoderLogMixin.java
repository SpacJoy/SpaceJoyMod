package net.spacejoy.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.protocol.PacketType;
import net.spacejoy.mod.config.SpaceJoyServerConfig;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 静音「disconnect 包编码失败」那一条 ERROR，不改任何发包行为。
 *
 * <p>玩家异常掉线（Netty 读异常、超时）时 {@code Connection.exceptionCaught} 会给对端补发一个
 * {@code ClientboundDisconnectPacket}。如果这条连接此时已经不在 play 协议下（polymer / fabric
 * 网络层把包重新包装过），{@code IdDispatchCodec} 在 id 表里查不到这个包类型，抛
 * {@code EncoderException: Sending unknown packet 'clientbound/minecraft:disconnect'}，
 * {@code PacketEncoder.encode} 的 catch 把它连堆栈一起打成 ERROR —— 就是控制台刷屏的那段：
 * <pre>
 * [Netty Epoll IO #5/ERROR]: Error sending packet clientbound/minecraft:disconnect
 * io.netty.handler.codec.EncoderException: Sending unknown packet 'clientbound/minecraft:disconnect'
 *     at net.minecraft.network.codec.IdDispatchCodec.encode(IdDispatchCodec.java:50)
 *     at net.minecraft.network.PacketEncoder.encode(PacketEncoder.java:...)
 * </pre>
 *
 * <p>这里只在日志调用点做判断：包类型是 {@code minecraft:disconnect} 且开关打开时，跳过这一次
 * {@code Logger.error}；异常照常向上抛，连接照常断开，disconnect 包也照常尝试发送。其它任何包的
 * 编码失败日志都会原样输出，排查真问题时不会漏掉信息。
 *
 * <p>用 {@code @WrapOperation} 而不是 {@code @Redirect}：前者能和别的 mod 对同一调用点的包装共存
 * （本项目 {@code PolymerJeiCompatibilityMixin} 出于同样理由也用它）。
 *
 * <p>注入点用 {@code require = 1}：这条 catch 块是本模组要静音的唯一日志点，如果哪天它被别的 mod
 * 改写掉、注入点消失，宁可启动阶段就报错，也不要静默变成「开关看着在、实际没生效」。
 * 同一调用点被别的 mod 也包装一层是能共存的，所以常见的网络层模组不会造成冲突。
 */
@Mixin(PacketEncoder.class)
public abstract class PacketEncoderLogMixin {
    @WrapOperation(
            method = "encode(Lio/netty/channel/ChannelHandlerContext;"
                    + "Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
            at = @At(value = "INVOKE",
                    target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
            require = 1
    )
    private void spacejoy$quietDisconnectEncodeError(Logger logger, String message,
                                                     Object packetType, Object thrown,
                                                     Operation<Void> original) {
        if (isDisconnect(packetType) && SpaceJoyServerConfig.isQuietDisconnectEncodeError()) {
            return;
        }
        original.call(logger, message, packetType, thrown);
    }

    /**
     * 判断日志参数里的包类型是不是 disconnect。
     *
     * <p>{@code PacketEncoder} 传给 {@code Logger.error} 的第二个参数就是 {@code packet.type()}，
     * 所以直接比 {@code Identifier} 的命名空间与路径，不依赖 {@code toString()} 的拼接格式，
     * 也不用管 flow 是 clientbound 还是 serverbound。
     */
    private static boolean isDisconnect(Object packetType) {
        return packetType instanceof PacketType<?> type
                && "minecraft".equals(type.id().getNamespace())
                && "disconnect".equals(type.id().getPath());
    }
}
