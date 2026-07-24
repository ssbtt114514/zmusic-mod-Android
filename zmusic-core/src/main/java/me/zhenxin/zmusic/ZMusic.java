package me.zhenxin.zmusic;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.manager.SoundManager;


/**
 * ZMusic 主入口
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/28 13:08
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
    private static String version = "3.7.0";

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
        log.info("Welcome use ZMusic! v{}", version);
        log.info("Homepage: https://m.zplu.cc");
        log.info("Github: https://github.com/starhui-dev/zmusic-mod");
        log.info("Discord: https://discord.gg/twQgJNufYn");
        log.info("QQ Group: 1032722724");
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
