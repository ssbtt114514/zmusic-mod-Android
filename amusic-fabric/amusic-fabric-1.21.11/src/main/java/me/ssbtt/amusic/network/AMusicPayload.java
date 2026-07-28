package me.ssbtt.amusic.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * AMusic 插件消息载荷（Fabric 1.20.6+ CustomPayload API）。
 *
 * <p>服务端协议为 {@code [1字节前缀] + UTF-8 文本}，文本内容交给核心层解析。</p>
 *
 * @author ssbtt
 * @since 2026-07-28
 */
public record AMusicPayload(String message) implements CustomPayload {

    public static final Id<AMusicPayload> ID = new CustomPayload.Id<>(Identifier.of("amusic", "channel"));
    public static final PacketCodec<PacketByteBuf, AMusicPayload> CODEC = PacketCodec.ofStatic(
            AMusicPayload::write,
            AMusicPayload::read
    );

    private static AMusicPayload read(PacketByteBuf buf) {
        int readable = buf.readableBytes();
        if (readable <= 1) {
            buf.skipBytes(readable);
            return new AMusicPayload("");
        }
        buf.skipBytes(1);
        String msg = buf.readCharSequence(buf.readableBytes(), StandardCharsets.UTF_8).toString();
        return new AMusicPayload(msg);
    }

    private static void write(PacketByteBuf buf, AMusicPayload payload) {
        buf.writeByte(666);
        buf.writeCharSequence(payload.message, StandardCharsets.UTF_8);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
