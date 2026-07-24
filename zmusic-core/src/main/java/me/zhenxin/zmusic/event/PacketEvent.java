package me.zhenxin.zmusic.event;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;


/**
 * 发包事件
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/29 22:50
 */
@Log4j2
class PacketEvent {

    public static void onPlay(String data) {
        log.info("PacketEvent.onPlay start: url={}, url_len={}", data, data == null ? 0 : data.length());
        if (data == null || data.trim().isEmpty()) {
            log.warn("Ignored empty ZMusic play url");
            return;
        }
        // 打印 URL 详细信息，便于诊断网络问题
        try {
            if (data.startsWith("http://") || data.startsWith("https://")) {
                java.net.URL u = new java.net.URL(data);
                log.info("Play URL parsed: protocol={}, host={}, port={}, path={}, query={}",
                    u.getProtocol(), u.getHost(), u.getPort(), u.getPath(), u.getQuery());
            } else {
                log.info("Play URL is non-http scheme: {}", data.substring(0, Math.min(20, data.length())));
            }
        } catch (Exception e) {
            log.warn("Failed to parse play URL: {}", e.getMessage());
        }

        if (ZMusic.getSoundManager() == null) {
            log.warn("ZMusic SoundManager is not initialized, skip stopping vanilla music");
        } else {
            log.info("Stopping vanilla music (MUSIC + RECORDS) before ZMusic playback");
            try {
                ZMusic.getSoundManager().stop();
                log.info("Vanilla music stopped successfully");
            } catch (Throwable t) {
                log.error("Failed to stop vanilla music: {}", t.getMessage(), t);
            }
        }
        if (ZMusic.getPlayer() == null) {
            log.warn("ZMusic player is not initialized, abort play");
            return;
        }
        log.info("Calling ZMusicPlayer.playAsync with url={}", data);
        ZMusic.getPlayer().playAsync(data);
        log.info("ZMusicPlayer.playAsync returned for url={}", data);
    }

    public static void onStop() {
        log.info("PacketEvent.onStop: stopping ZMusic playback");
        if (ZMusic.getPlayer() == null) {
            log.warn("ZMusic player is not initialized, abort stop");
            return;
        }
        ZMusic.getPlayer().stopAsync();
        log.info("ZMusicPlayer.stopAsync returned");
    }

    public static void onLyric(String data) {
        // TODO: 歌词
    }

    public static void onInfo(String data) {
        // TODO: 信息
    }

    public static void onImg(String data) {
        // TODO: 专辑图片
    }
}
