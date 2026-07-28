package me.ssbtt.amusic.event;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.history.HistoryManager;
import me.ssbtt.amusic.playback.InfoParser;
import me.ssbtt.amusic.playback.NowPlaying;


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
            log.warn("Ignored empty AMusic play url");
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

        // 保存上一首歌的歌词
        NowPlaying np = AMusic.getNowPlaying();
        if (np != null && !np.getName().isEmpty()) {
            HistoryManager hm = AMusic.getHistoryManager();
            if (hm != null) {
                hm.saveLyrics(np.getName());
            }
        }
        // 记录新 URL，重置播放信息（歌名等 [Info] 推送后再填）
        if (np != null) {
            String prevName = np.getName();
            np.reset();
            np.setUrl(data);
            np.setPlayStartTime(System.currentTimeMillis());
            // 保留歌名供 saveLyrics 已在上面处理；这里已 reset
            if (!prevName.isEmpty()) {
                log.info("Switching song: prev={}, new url={}", prevName, data);
            }
        }

        if (AMusic.getSoundManager() == null) {
            log.warn("AMusic SoundManager is not initialized, skip stopping vanilla music");
        } else {
            log.info("Stopping vanilla music (MUSIC + RECORDS) before AMusic playback");
            try {
                AMusic.getSoundManager().stop();
                log.info("Vanilla music stopped successfully");
            } catch (Throwable t) {
                log.error("Failed to stop vanilla music: {}", t.getMessage(), t);
            }
        }
        if (AMusic.getPlayer() == null) {
            log.warn("AMusic player is not initialized, abort play");
            return;
        }
        log.info("Calling AMusicPlayer.playAsync with url={}", data);
        AMusic.getPlayer().playAsync(data);
        log.info("AMusicPlayer.playAsync returned for url={}", data);
    }

    public static void onStop() {
        log.info("PacketEvent.onStop: stopping AMusic playback");
        // 停止时保存当前歌词
        NowPlaying np = AMusic.getNowPlaying();
        if (np != null && !np.getName().isEmpty()) {
            HistoryManager hm = AMusic.getHistoryManager();
            if (hm != null) {
                hm.saveLyrics(np.getName());
            }
        }
        if (AMusic.getPlayer() == null) {
            log.warn("AMusic player is not initialized, abort stop");
            return;
        }
        AMusic.getPlayer().stopAsync();
        log.info("AMusicPlayer.stopAsync returned");
    }

    public static void onLyric(String data) {
        NowPlaying np = AMusic.getNowPlaying();
        if (np == null) {
            return;
        }
        String line = InfoParser.parseLyric(data);
        if (line.isEmpty()) {
            return;
        }
        np.setCurrentLyric(line);
        // 累积到历史歌词
        HistoryManager hm = AMusic.getHistoryManager();
        if (hm != null && !np.getName().isEmpty()) {
            hm.appendLyric(np.getName(), line);
        }
    }

    public static void onInfo(String data) {
        NowPlaying np = AMusic.getNowPlaying();
        if (np == null) {
            return;
        }
        boolean isNewSong = InfoParser.parseInfo(data, np);
        if (isNewSong && !np.getName().isEmpty()) {
            HistoryManager hm = AMusic.getHistoryManager();
            if (hm != null) {
                hm.addSong(np.getName(), np.getUrl(), np.getPlatform());
                log.info("New song added to history: {} - platform={}", np.getName(), np.getPlatform());
            }
        }
    }

    public static void onImg(String data) {
        // TODO: 专辑图片
    }
}
