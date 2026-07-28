package me.ssbtt.amusic;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.config.AMusicConfig;
import me.ssbtt.amusic.history.HistoryEntry;
import me.ssbtt.amusic.history.HistoryManager;
import me.ssbtt.amusic.manager.SoundManager;
import me.ssbtt.amusic.playback.NowPlaying;
import me.ssbtt.amusic.playlist.Playlist;
import me.ssbtt.amusic.playlist.PlaylistManager;
import me.ssbtt.amusic.playlist.PlaylistPlayer;

import java.io.File;


/**
 * AMusic 主入口
 *
 * @author ssbtt
 * @since 2026-04-24
 */
@SuppressWarnings({"AlibabaClassNamingShouldBeCamel", "AlibabaConstantFieldShouldBeUpperCase"})
@Log4j2
public class AMusic {
    @Getter
    private static AMusicPlayer player;
    private static boolean shutdownHookRegistered;
    @Getter
    @Setter
    private static SoundManager soundManager;
    @Getter
    private static String version = "1.1";
    /** 客户端配置目录（gameDir/config），由平台模块在初始化时设置 */
    @Getter
    @Setter
    private static File configDir;
    /** 客户端配置 */
    @Getter
    private static AMusicConfig config;
    /** 历史记录管理器 */
    @Getter
    private static HistoryManager historyManager;
    /** 歌单管理器 */
    @Getter
    private static PlaylistManager playlistManager;
    /** 歌单播放控制器 */
    @Getter
    private static PlaylistPlayer playlistPlayer = new PlaylistPlayer();
    /** 当前播放信息 */
    @Getter
    private static NowPlaying nowPlaying = new NowPlaying();

    public static void onEnable() {
        // 打印详细平台信息，便于定位 Android 兼容性问题
        log.info("=== AMusic Platform Info ===");
        log.info("java.vm.name={}", System.getProperty("java.vm.name", "?"));
        log.info("java.vm.version={}", System.getProperty("java.vm.version", "?"));
        log.info("java.runtime.name={}", System.getProperty("java.runtime.name", "?"));
        log.info("os.name={}", System.getProperty("os.name", "?"));
        log.info("os.arch={}", System.getProperty("os.arch", "?"));
        log.info("java.io.tmpdir={}", System.getProperty("java.io.tmpdir", "?"));
        log.info("user.dir={}", System.getProperty("user.dir", "?"));
        log.info("user.home={}", System.getProperty("user.home", "?"));
        log.info("java.class.path (first 500)={}",
            System.getProperty("java.class.path", "").substring(0,
                Math.min(500, System.getProperty("java.class.path", "").length())));
        // 尝试加载 android.os.Build 检测
        try {
            Class.forName("android.os.Build");
            log.info("android.os.Build detected: this is Android runtime");
        } catch (ClassNotFoundException e) {
            log.info("android.os.Build not found: this is desktop JVM");
        }
        log.info("=== AMusic Platform Info End ===");

        if (player != null) {
            log.info("Destroying previous AMusic player instance");
            player.destroy();
        }
        // 初始化配置和历史记录（configDir 由平台模块在 onEnable 前设置）
        if (configDir != null) {
            try {
                config = new AMusicConfig(configDir);
                historyManager = new HistoryManager(configDir);
                playlistManager = new PlaylistManager(configDir);
                // 迁移旧版 favorite.json 到歌单「收藏」
                migrateFavoritesToPlaylist();
                log.info("AMusic config dir: {}", configDir);
            } catch (Throwable t) {
                log.warn("Failed to init AMusic config/history: {}", t.getMessage(), t);
            }
        } else {
            log.warn("AMusic configDir not set, config/history disabled");
        }
        log.info("Creating new AMusicPlayer instance");
        player = new AMusicPlayer();
        player.setEventListener(new AMusicPlayer.EventListener() {
            @Override
            public void onStateChanged(int state) {
                String stateName;
                switch (state) {
                    case 0: stateName = "STOPPED"; break;
                    case 1: stateName = "LOADING"; break;
                    case 2: stateName = "PLAYING"; break;
                    case 3: stateName = "PAUSED"; break;
                    case 4: stateName = "ERROR"; break;
                    default: stateName = "UNKNOWN(" + state + ")"; break;
                }
                log.info("AMusic player state changed: {} ({})", stateName, state);
                // 当播放器状态变为 STOPPED（MP3 播放完毕或服务器 [Stop] 包）时，
                // 触发歌单下一首。handlePlayerStopped 会区分自然结束和外部停止，
                // 并在歌单活动时自动计算并播放下一首。
                if (state == AMusicPlayer.STATE_STOPPED) {
                    PlaylistPlayer pp = playlistPlayer;
                    if (pp != null) {
                        pp.handlePlayerStopped();
                    }
                }
            }

            @Override
            public void onTrackEnded() {
                log.info("AMusic track ended (callback)");
                // 歌单播放中：根据播放顺序自动推进
                PlaylistPlayer pp = playlistPlayer;
                if (pp != null && pp.isActive()) {
                    pp.onTrackEnded();
                }
            }

            @Override
            public void onProgress(long positionMs, long durationMs) {
                log.debug("AMusic progress: pos={}ms, duration={}ms", positionMs, durationMs);
            }

            @Override
            public void onError(String message) {
                log.error("AMusic player error (callback): {}", message, new Throwable("error callback stack"));
            }

            @Override
            public void onBuffering(boolean buffering) {
                log.info("AMusic player buffering: {}", buffering);
            }
        });
        registerShutdownHook();
        log.info("Welcome use AMusic! v{}", version);
        log.info("Author: ssbtt");
        log.info("Github: https://github.com/ssbtt114514");
    }

    public static void onDisable() {
        if (player != null) {
            player.destroy();
            player = null;
        }
    }

    /**
     * 迁移旧版 favorite.json 到歌单「收藏」。
     *
     * <p>如果存在旧版 {@code config/AMusic/favorite.json} 且歌单「收藏」不存在，
     * 则将收藏列表导入为名为「收藏」的歌单，然后将旧文件重命名为 .bak 避免重复迁移。</p>
     */
    private static void migrateFavoritesToPlaylist() {
        if (configDir == null || playlistManager == null) return;
        try {
            File favoriteFile = new File(new File(configDir, "AMusic"), "favorite.json");
            if (!favoriteFile.exists()) return;
            // 已有「收藏」歌单则不覆盖
            if (playlistManager.loadPlaylist("收藏") != null) {
                log.info("Favorites migration skipped: playlist「收藏」already exists");
                return;
            }
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.reflect.TypeToken<java.util.List<HistoryEntry>> type =
                    new com.google.gson.reflect.TypeToken<java.util.List<HistoryEntry>>() {};
            String json = new String(java.nio.file.Files.readAllBytes(favoriteFile.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
            java.util.List<HistoryEntry> favorites = gson.fromJson(json, type.getType());
            if (favorites == null) favorites = new java.util.ArrayList<>();
            Playlist pl = new Playlist("收藏");
            for (HistoryEntry e : favorites) {
                pl.addSong(e);
            }
            playlistManager.savePlaylist(pl);
            // 重命名旧文件避免重复迁移
            File bak = new File(favoriteFile.getParentFile(), "favorite.json.bak");
            if (!favoriteFile.renameTo(bak)) {
                favoriteFile.delete();
            }
            log.info("Favorites migrated to playlist「收藏」: {} songs", favorites.size());
        } catch (Exception e) {
            log.warn("Favorites migration failed: {}", e.getMessage());
        }
    }

    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(AMusic::onDisable, "amusic-shutdown"));
        shutdownHookRegistered = true;
    }
}
