package me.zhenxin.zmusic;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.config.ZMusicConfig;
import me.zhenxin.zmusic.favorite.FavoriteManager;
import me.zhenxin.zmusic.history.HistoryManager;
import me.zhenxin.zmusic.manager.SoundManager;
import me.zhenxin.zmusic.playback.NowPlaying;
import me.zhenxin.zmusic.playlist.PlaylistManager;
import me.zhenxin.zmusic.playlist.PlaylistPlayer;

import java.io.File;


/**
 * AMusic 主入口
 *
 * @author ssbtt
 * @since 2026-04-24
 */
@SuppressWarnings({"AlibabaClassNamingShouldBeCamel", "AlibabaConstantFieldShouldBeUpperCase"})
@Log4j2
public class ZMusic {
    @Getter
    private static ZMusicPlayer player;
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
    private static ZMusicConfig config;
    /** 历史记录管理器 */
    @Getter
    private static HistoryManager historyManager;
    /** 收藏管理器 */
    @Getter
    private static FavoriteManager favoriteManager;
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
        log.info("=== ZMusic Platform Info ===");
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
        log.info("=== ZMusic Platform Info End ===");

        if (player != null) {
            log.info("Destroying previous ZMusic player instance");
            player.destroy();
        }
        // 初始化配置和历史记录（configDir 由平台模块在 onEnable 前设置）
        if (configDir != null) {
            try {
                config = new ZMusicConfig(configDir);
                historyManager = new HistoryManager(configDir);
                favoriteManager = new FavoriteManager(configDir);
                playlistManager = new PlaylistManager(configDir);
                log.info("ZMusic config dir: {}", configDir);
            } catch (Throwable t) {
                log.warn("Failed to init ZMusic config/history: {}", t.getMessage(), t);
            }
        } else {
            log.warn("ZMusic configDir not set, config/history disabled");
        }
        log.info("Creating new ZMusicPlayer instance");
        player = new ZMusicPlayer();
        player.setEventListener(new ZMusicPlayer.EventListener() {
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
                log.info("ZMusic player state changed: {} ({})", stateName, state);
            }

            @Override
            public void onTrackEnded() {
                log.info("ZMusic track ended (callback)");
                // 歌单播放中：根据播放顺序自动推进
                PlaylistPlayer pp = playlistPlayer;
                if (pp != null && pp.isActive()) {
                    pp.onTrackEnded();
                }
            }

            @Override
            public void onProgress(long positionMs, long durationMs) {
                log.debug("ZMusic progress: pos={}ms, duration={}ms", positionMs, durationMs);
            }

            @Override
            public void onError(String message) {
                log.error("ZMusic player error (callback): {}", message, new Throwable("error callback stack"));
            }

            @Override
            public void onBuffering(boolean buffering) {
                log.info("ZMusic player buffering: {}", buffering);
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

    private static void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(ZMusic::onDisable, "zmusic-shutdown"));
        shutdownHookRegistered = true;
    }
}
