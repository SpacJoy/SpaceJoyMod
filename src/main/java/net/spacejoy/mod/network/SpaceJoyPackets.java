package net.spacejoy.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 服务端权威配置 / 路径点登记用的三个自定义包。26.3 用 record + Type + StreamCodec。 */
public final class SpaceJoyPackets {
    private SpaceJoyPackets() {
    }

    /** 服务端 → 客户端：配置快照 + 这个玩家能不能改（是不是 OP）。 */
    public record ConfigS2C(boolean canEdit, boolean waypointEnabled,
                            boolean eliteAbilityDrop, String eliteAbilityMinRarity,
                            boolean polymerRecipeFix, boolean taczConcurrencyFix,
                            boolean quietDisconnectEncodeError)
            implements CustomPacketPayload {
        public static final Type<ConfigS2C> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("spacejoy_mod", "config_s2c"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigS2C> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBoolean(p.canEdit);
                            buf.writeBoolean(p.waypointEnabled);
                            buf.writeBoolean(p.eliteAbilityDrop);
                            buf.writeUtf(p.eliteAbilityMinRarity);
                            buf.writeBoolean(p.polymerRecipeFix);
                            buf.writeBoolean(p.taczConcurrencyFix);
                            buf.writeBoolean(p.quietDisconnectEncodeError);
                        },
                        buf -> new ConfigS2C(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                                buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

        @Override
        public Type<ConfigS2C> type() {
            return TYPE;
        }
    }

    /**
     * 客户端 → 服务端：请把最新的配置快照（含「我是不是 OP」）再发一次。
     *
     * <p>没有字段：玩家是谁已经在连接上了，服务端按连接重新查 {@code PlayerList.isOp} 即可。
     * 存在的理由见 {@link #ConfigS2C} 的说明——OP 只在进服时下发一次，被 {@code /op}、
     * {@code /deop} 改掉之后客户端不会自己知道。
     */
    public record StatusRequestC2S() implements CustomPacketPayload {
        public static final Type<StatusRequestC2S> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("spacejoy_mod", "status_request_c2s"));
        /** 空包：不写任何字节，解码出来永远是同一个实例（record 无状态，共享安全）。 */
        public static final StreamCodec<RegistryFriendlyByteBuf, StatusRequestC2S> CODEC =
                StreamCodec.unit(new StatusRequestC2S());

        @Override
        public Type<StatusRequestC2S> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：改某个服务端配置。value 统一用字符串（布尔传 "true"/"false"）。 */
    public record ConfigC2S(String key, String value) implements CustomPacketPayload {
        public static final Type<ConfigC2S> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("spacejoy_mod", "config_c2s"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeUtf(p.key);
                            buf.writeUtf(p.value);
                        },
                        buf -> new ConfigC2S(buf.readUtf(), buf.readUtf()));

        @Override
        public Type<ConfigC2S> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：登记/删除路径点。op = "add" 或 "del"。 */
    public record WaypointC2S(String op, String name, int x, int y, int z, String dimension)
            implements CustomPacketPayload {
        public static final Type<WaypointC2S> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath("spacejoy_mod", "waypoint_c2s"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WaypointC2S> CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeUtf(p.op);
                            buf.writeUtf(p.name);
                            buf.writeInt(p.x);
                            buf.writeInt(p.y);
                            buf.writeInt(p.z);
                            buf.writeUtf(p.dimension);
                        },
                        buf -> new WaypointC2S(buf.readUtf(), buf.readUtf(),
                                buf.readInt(), buf.readInt(), buf.readInt(), buf.readUtf()));

        @Override
        public Type<WaypointC2S> type() {
            return TYPE;
        }
    }
}
