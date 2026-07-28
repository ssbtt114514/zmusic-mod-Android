package me.ssbtt.amusic.client;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.AMusicMod;
import me.ssbtt.amusic.playlist.Playlist;
import me.ssbtt.amusic.playlist.PlaylistShare;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * 歌单网络请求发送器（Forge 版本）。
 *
 * <p>通过 Forge 的 {@link net.minecraftforge.network.SimpleChannel}（{@code AMusicMod.CHANNEL}）
 * 向服务端发送歌单相关请求。</p>
 *
 * <p>数据包格式（客户端→服务端）：</p>
 * <ul>
 *   <li>{@code [PLUpload]<json>} - 上传歌单</li>
 *   <li>{@code [PLListPublic]} - 请求公开歌单列表</li>
 *   <li>{@code [PLListMine]} - 请求自己的歌单列表</li>
 *   <li>{@code [PLDownload]<id>} - 下载指定 ID 的歌单</li>
 * </ul>
 *
 * @author ssbtt
 * @since 2026-07-27
 */
@Log4j2
public class PlaylistNetSender {

    private PlaylistNetSender() {}

    /**
     * 发送数据包到服务端。
     *
     * @param message 消息内容（含前缀标记）
     */
    private static void send(String message) {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null) {
            log.warn("Cannot send playlist packet: not connected to server");
            return;
        }
        try {
            if (AMusicMod.CHANNEL == null) {
                log.warn("Cannot send playlist packet: AMusicMod.CHANNEL not initialized");
                return;
            }
            AMusicMod.CHANNEL.sendToServer(message);
            log.info("Sent playlist packet: {} (len={})",
                    message.substring(0, Math.min(50, message.length())), message.length());
        } catch (Exception e) {
            log.warn("Failed to send playlist packet: {}", e.getMessage());
        }
    }

    public static void uploadPlaylist(Playlist playlist) {
        if (playlist == null) return;
        String json = PlaylistShare.encode(playlist);
        send("[PLUpload]" + json);
    }

    public static void requestPublicList() {
        send("[PLListPublic]");
    }

    public static void requestMyList() {
        send("[PLListMine]");
    }

    public static void downloadPlaylist(String id) {
        if (id == null || id.isEmpty()) return;
        send("[PLDownload]" + id);
    }
}
