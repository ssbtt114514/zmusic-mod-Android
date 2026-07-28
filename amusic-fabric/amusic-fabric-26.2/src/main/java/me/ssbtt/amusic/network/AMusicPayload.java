package me.ssbtt.amusic.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * AMusic 插件消息载荷（Fabric 26.1 Mojmap CustomPacketPayload API）。
 *
 * <p>服务端协议为 {@code [1字节前缀] + UTF-8 文本}，文本内容交给核心层解析。</p>
 *
 * @author ssbtt
 * @since 2026-07-28
 */
public record AMusicPayload(String message) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AMusicPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("amusic", "channel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AMusicPayload> STREAM_CODEC = StreamCodec.of(
            AMusicPayload::write,
            AMusicPayload::read
    );

    private static AMusicPayload read(RegistryFriendlyByteBuf buf) {
        int readable = buf.readableBytes();
        if (readable <= 1) {
            buf.skipBytes(readable);
            return new AMusicPayload("");
        }
        buf.skipBytes(1);
        String msg = buf.readCharSequence(buf.readableBytes(), StandardCharsets.UTF_8).toString();
        return new AMusicPayload(msg);
    }

    private static void write(RegistryFriendlyByteBuf buf, AMusicPayload payload) {
        buf.writeByte(666);
        buf.writeCharSequence(payload.message, StandardCharsets.UTF_8);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
