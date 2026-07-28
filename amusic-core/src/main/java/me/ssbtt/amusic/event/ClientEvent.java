package me.ssbtt.amusic.event;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.playback.InfoParser;
import me.ssbtt.amusic.playlist.PlaylistNetClient;

/**
 * 客户端事件
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/29 22:52
 */
@Log4j2
public class ClientEvent {

    @SuppressWarnings("AlibabaUndefineMagicConstant")
    public static void onPacket(String message) {
        log.info("ClientEvent.onPacket invoked, thread={}", Thread.currentThread().getName());
        if (message == null) {
            log.warn("Received null AMusic packet message");
            return;
        }
        log.info("Received AMusic packet message (len={}, raw={})", message.length(), message);
        if (message.startsWith("[Play]")) {
            String data = message.replace("[Play]", "");
            log.info("Parsed AMusic play command: url={}, url_len={}", data, data.length());
            try {
                PacketEvent.onPlay(data);
                log.info("PacketEvent.onPlay returned for url={}", data);
            } catch (Throwable t) {
                log.error("PacketEvent.onPlay threw exception for url={}", data, t);
            }
        } else if ("[Stop]".equals(message)) {
            log.info("Parsed AMusic stop command");
            try {
                PacketEvent.onStop();
                log.info("PacketEvent.onStop returned");
            } catch (Throwable t) {
                log.error("PacketEvent.onStop threw exception", t);
            }
        } else if (message.startsWith("[Info]")) {
            String data = message.substring("[Info]".length());
            try {
                PacketEvent.onInfo(data);
            } catch (Throwable t) {
                log.error("PacketEvent.onInfo threw exception", t);
            }
        } else if (message.startsWith("[Lyric]")) {
            String data = message.substring("[Lyric]".length());
            try {
                PacketEvent.onLyric(data);
            } catch (Throwable t) {
                log.error("PacketEvent.onLyric threw exception", t);
            }
        } else if (message.startsWith("[PLListResp]") || message.startsWith("[PLData]") || message.startsWith("[PLResult]")) {
            // 歌单网络相关数据包
            try {
                PlaylistNetClient.getInstance().onPacket(message);
            } catch (Throwable t) {
                log.error("PlaylistNetClient.onPacket threw exception", t);
            }
        } else {
            log.warn("Ignored unknown AMusic packet message: {}", message);
        }
    }

    public static void onDisconnect() {
        log.info("AMusic client disconnected, stopping native player");
        PacketEvent.onStop();
    }
}
