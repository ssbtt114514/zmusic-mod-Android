package me.ssbtt.amusic.client;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.network.AMusicPayload;
import me.ssbtt.amusic.playlist.Playlist;
import me.ssbtt.amusic.playlist.PlaylistShare;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

/**
 * 歌单网络请求发送器（Fabric 版本）。
 *
 * <p>通过 AMusic 插件通道（{@code amusic:channel}）向服务端发送歌单相关请求。</p>
 *
 * <p>数据包格式（客户端→服务端）：</p>
 * <ul>
 *   <li>{@code [PLUpload]<json>} - 上传歌单（JSON 为完整歌单数据）</li>
 *   <li>{@code [PLListPublic]} - 请求公开歌单列表</li>
 *   <li>{@code [PLListMine]} - 请求自己的歌单列表</li>
 *   <li>{@code [PLDownload]<id>} - 下载指定 ID 的歌单</li>
 * </ul>
 *
 * @author ssbtt
 * @since 2026-07-28
 */
@Log4j2
public class PlaylistNetSender {

    private PlaylistNetSender() {
    }

    /**
     * 发送数据包到服务端。
     *
     * @param message 消息内容（含前缀标记）
     */
    private static void send(String message) {
        ClientPlayNetworkHandler listener = MinecraftClient.getInstance().getNetworkHandler();
        if (listener == null) {
            log.warn("Cannot send playlist packet: not connected to server");
            return;
        }
        try {
            ClientPlayNetworking.send(new AMusicPayload(message));
            log.info("Sent playlist packet: {} (len={})",
                    message.substring(0, Math.min(50, message.length())), message.length());
        } catch (Exception e) {
            log.warn("Failed to send playlist packet: {}", e.getMessage());
        }
    }

    /**
     * 上传歌单到服务器。
     */
    public static void uploadPlaylist(Playlist playlist) {
        if (playlist == null) return;
        String json = PlaylistShare.encode(playlist);
        send("[PLUpload]" + json);
    }

    /**
     * 请求公开歌单列表。
     */
    public static void requestPublicList() {
        send("[PLListPublic]");
    }

    /**
     * 请求自己的歌单列表。
     */
    public static void requestMyList() {
        send("[PLListMine]");
    }

    /**
     * 下载指定 ID 的歌单。
     *
     * @param id 歌单 ID（6 位字母数字）
     */
    public static void downloadPlaylist(String id) {
        if (id == null || id.isEmpty()) return;
        send("[PLDownload]" + id);
    }
}
