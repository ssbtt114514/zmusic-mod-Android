package me.zhenxin.zmusic;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;

import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.Obuffer;
import javazoom.jl.decoder.SampleBuffer;
import javazoom.jl.player.Player;

/**
 * 纯 Java 音频播放器，替代 JNI native 实现。
 *
 * <p>Android 平台通过反射调用 {@code android.media.MediaPlayer}，
 * 原生支持 HTTP MP3 流式播放。
 * 桌面平台（含 Android FCL 启动器）通过 JLayer 纯 Java MP3 解码器播放。</p>
 *
 * <p>保持与原 native 版本完全相同的公开接口，调用方无需修改。</p>
 *
 * @author 真心
 * @since 2026-07-24
 */
@Log4j2
public class ZMusicPlayer {

    // ---- 事件常量 ----
    public static final int EVENT_NONE = 0;
    public static final int EVENT_STATE_CHANGED = 1;
    public static final int EVENT_TRACK_ENDED = 2;
    public static final int EVENT_PROGRESS_UPDATE = 3;
    public static final int EVENT_ERROR = 4;
    public static final int EVENT_BUFFERING = 5;

    // ---- 播放状态常量 ----
    public static final int STATE_STOPPED = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_PLAYING = 2;
    public static final int STATE_PAUSED = 3;
    public static final int STATE_ERROR = 4;

    // ---- 循环模式常量 ----
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_ALL = 2;

    // ---- 内部状态 ----
    private volatile int state = STATE_STOPPED;
    private volatile float currentVolume = -1.0f;
    private volatile boolean running = true;
    private volatile EventListener listener;

    private final Object playLock = new Object();
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "zmusic-command");
            thread.setDaemon(true);
            return thread;
        }
    });

    /** 音频后端，由平台检测决定使用 Android MediaPlayer 还是 JLayer */
    private final AudioBackend backend;

    public ZMusicPlayer() {
        log.info("ZMusicPlayer constructor start");
        backend = createBackend();
        log.info("ZMusic player initialized (pure Java, backend={})", backend.name());
    }

    // ---- 公开接口（与原 native 版本完全一致）----

    public int play(String url) {
        log.info("ZMusicPlayer.play() called: url={}, thread={}", url, Thread.currentThread().getName());
        doPlay(url);
        log.info("ZMusicPlayer.play() returned for url={}", url);
        return 0;
    }

    public void playAsync(final String url) {
        log.info("ZMusicPlayer.playAsync() called: url={}, thread={}", url, Thread.currentThread().getName());
        executeCommand(new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    log.warn("ZMusicPlayer.playAsync: player not running, abort");
                    return;
                }
                log.info("ZMusicPlayer.playAsync: doStop before new play");
                doStop();
                log.info("ZMusicPlayer.playAsync: doPlay with url={}", url);
                doPlay(url);
            }
        });
    }

    public int pause() {
        log.info("ZMusicPlayer.pause() called, current state={}", state);
        backend.pause();
        setState(STATE_PAUSED);
        return 0;
    }

    public int stop() {
        log.info("ZMusicPlayer.stop() called, current state={}", state);
        doStop();
        return 0;
    }

    public void stopAsync() {
        log.info("ZMusicPlayer.stopAsync() called, current state={}", state);
        executeCommand(new Runnable() {
            @Override
            public void run() {
                doStop();
            }
        });
    }

    public int resume() {
        log.info("ZMusicPlayer.resume() called, current state={}", state);
        backend.resume();
        setState(STATE_PLAYING);
        return 0;
    }

    public int seek(long positionMs) {
        log.info("ZMusicPlayer.seek() called: pos={}ms, current state={}", positionMs, state);
        backend.seek(positionMs);
        return 0;
    }

    public int getState() { return state; }

    public long getPosition() { return backend.getPosition(); }

    public long getDuration() { return backend.getDuration(); }

    public float getVolume() { return currentVolume < 0 ? 1.0f : currentVolume; }

    public int setVolume(final float volume) {
        if (Float.compare(currentVolume, volume) == 0) {
            return 0;
        }
        currentVolume = volume;
        log.info("ZMusic volume change: {}", volume);
        executeCommand(new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                backend.setVolume(volume);
            }
        });
        return 0;
    }

    public synchronized void destroy() {
        log.info("ZMusicPlayer.destroy() called, backend={}", backend == null ? "null" : backend.name());
        running = false;
        commandExecutor.shutdownNow();
        synchronized (playLock) {
            try {
                backend.destroy();
                log.info("ZMusicPlayer.destroy: backend.destroy() returned");
            } catch (Throwable t) {
                log.warn("ZMusicPlayer.destroy: backend.destroy() threw: {}", t.getMessage(), t);
            }
        }
        log.info("ZMusicPlayer.destroy end");
    }

    // ---- 队列操作（当前协议未使用，stub 实现）----
    public void enqueue(String url, String title, String artist) { /* stub */ }
    public void enqueueNext(String url, String title, String artist) { /* stub */ }
    public void removeFromQueue(int index) { /* stub */ }
    public void clearQueue() { /* stub */ }
    public void playNext() { /* stub */ }
    public void playPrevious() { /* stub */ }
    public void playAtIndex(int index) { /* stub */ }
    public int getQueueSize() { return 0; }
    public int getCurrentIndex() { return 0; }

    // ---- 歌词（当前协议未使用，stub 实现）----
    public void loadLyrics(String lrcContent) { /* stub */ }
    public String getCurrentLyric() { return ""; }

    // ---- 模式控制（stub）----
    public void setRepeatMode(int mode) { /* stub */ }
    public void setShuffle(boolean enabled) { /* stub */ }

    // ---- 事件监听 ----
    public interface EventListener {
        void onStateChanged(int state);
        void onTrackEnded();
        void onProgress(long positionMs, long durationMs);
        void onError(String message);
        void onBuffering(boolean buffering);
    }

    public void setEventListener(EventListener listener) {
        this.listener = listener;
    }

    // ---- 内部实现 ----

    private void doPlay(String url) {
        if (url == null || url.trim().isEmpty()) {
            log.warn("ZMusic play called with empty URL");
            return;
        }
        log.info("doPlay start: url={}, backend={}, thread={}", url, backend.name(), Thread.currentThread().getName());
        synchronized (playLock) {
            log.info("doPlay: acquired playLock");
            setState(STATE_LOADING);
            try {
                log.info("doPlay: calling backend.play({})", url);
                backend.play(url);
                log.info("doPlay: backend.play returned, setting state to PLAYING");
                setState(STATE_PLAYING);
                log.info("ZMusic playback started: {}", url);
            } catch (Exception e) {
                log.error("ZMusic playback failed: {} - {}", url, e.getMessage(), e);
                setState(STATE_ERROR);
                notifyError(e.getMessage());
            }
        }
        log.info("doPlay end: url={}", url);
    }

    private void doStop() {
        log.info("doStop start: backend={}", backend.name());
        synchronized (playLock) {
            log.info("doStop: acquired playLock");
            try {
                log.info("doStop: calling backend.stop()");
                backend.stop();
                log.info("doStop: backend.stop returned");
            } catch (Exception e) {
                log.warn("ZMusic stop error: {}", e.getMessage());
            }
            setState(STATE_STOPPED);
        }
        log.info("doStop end");
    }

    private void setState(int newState) {
        if (state == newState) return;
        state = newState;
        EventListener l = listener;
        if (l != null) {
            l.onStateChanged(newState);
        }
    }

    private void notifyError(String message) {
        EventListener l = listener;
        if (l != null) {
            l.onError(message);
        }
    }

    private void notifyTrackEnded() {
        EventListener l = listener;
        if (l != null) {
            l.onTrackEnded();
        }
    }

    private void executeCommand(Runnable command) {
        try {
            commandExecutor.execute(command);
        } catch (RejectedExecutionException ignored) {
        }
    }

    // ---- 平台检测 ----

    /**
     * 检测当前是否运行在 Android 运行时（Dalvik/ART）。
     *
     * <p>FCL 等 Android 启动器使用桌面版 JVM（非 Dalvik/ART），
     * 但 java.boot.class.path 或 java.class.path 中会包含 Android 特有路径。
     * 也检查 java.io.tmpdir 是否指向 /storage/emulated/。</p>
     */
    private static boolean isAndroid() {
        log.info("isAndroid detection start");
        // 主检测：VM 名称
        String vmName = System.getProperty("java.vm.name", "");
        log.info("isAndroid: java.vm.name={}", vmName);
        if (vmName.startsWith("Dalvik") || vmName.startsWith("ART")) {
            log.info("isAndroid: detected Android via VM name");
            return true;
        }

        // 备用检测：尝试加载 Android 特有类
        try {
            Class.forName("android.os.Build");
            log.info("isAndroid: detected Android via android.os.Build");
            return true;
        } catch (ClassNotFoundException ignored) {
            log.info("isAndroid: android.os.Build not found");
        }

        // FCL 启动器检测：Android 上 java.io.tmpdir 通常指向 /storage/emulated/ 或 /data/data/
        String tmpDir = System.getProperty("java.io.tmpdir", "");
        log.info("isAndroid: java.io.tmpdir={}", tmpDir);
        if (tmpDir.contains("/storage/emulated/") || tmpDir.contains("/data/data/")) {
            log.info("isAndroid: detected Android via tmpDir");
            return true;
        }

        // FCL 启动器检测：检查 user.home 或 user.dir 是否在 Android 路径下
        String userDir = System.getProperty("user.dir", "");
        String userHome = System.getProperty("user.home", "");
        log.info("isAndroid: user.dir={}, user.home={}", userDir, userHome);
        if (userDir.contains("/storage/emulated/") || userDir.contains("FCL")
            || userHome.contains("/storage/emulated/") || userHome.contains("FCL")) {
            log.info("isAndroid: detected Android via user.dir/user.home (FCL)");
            return true;
        }

        log.info("isAndroid: not detected as Android, will use JLayer backend");
        return false;
    }

    private AudioBackend createBackend() {
        log.info("createBackend: start");
        if (isAndroid()) {
            log.info("createBackend: isAndroid=true, trying AndroidMediaPlayerBackend");
            try {
                AndroidMediaPlayerBackend backend = new AndroidMediaPlayerBackend();
                log.info("createBackend: AndroidMediaPlayerBackend created successfully");
                return backend;
            } catch (Throwable e) {
                log.warn("createBackend: Android MediaPlayer not available, falling back to JLayer: {}", e.getMessage(), e);
            }
        } else {
            log.info("createBackend: isAndroid=false, using JLayerBackend directly");
        }
        JLayerBackend backend = new JLayerBackend();
        log.info("createBackend: JLayerBackend created");
        return backend;
    }

    // ---- 音频后端接口 ----

    private interface AudioBackend {
        String name();
        void play(String url) throws Exception;
        void stop();
        void pause();
        void resume();
        void seek(long positionMs);
        void setVolume(float volume);
        long getPosition();
        long getDuration();
        void destroy();
    }

    // ---- Android MediaPlayer 后端（反射调用）----

    private class AndroidMediaPlayerBackend implements AudioBackend {
        private Object mediaPlayer;
        private Class<?> mpClass;
        private Class<?> onPreparedListenerClass;
        private Class<?> onCompletionListenerClass;
        private Class<?> onErrorListenerClass;
        private volatile long durationMs;

        AndroidMediaPlayerBackend() {
            try {
                mpClass = Class.forName("android.media.MediaPlayer");
                onPreparedListenerClass = Class.forName("android.media.MediaPlayer$OnPreparedListener");
                onCompletionListenerClass = Class.forName("android.media.MediaPlayer$OnCompletionListener");
                onErrorListenerClass = Class.forName("android.media.MediaPlayer$OnErrorListener");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Android MediaPlayer not available", e);
            }
        }

        @Override
        public String name() { return "Android MediaPlayer"; }

        @Override
        public void play(String url) throws Exception {
            log.info("AndroidMediaPlayerBackend.play start: url={}", url);
            // 先释放旧的 MediaPlayer
            log.info("Android MediaPlayer: releasing previous instance");
            releaseInternal();

            // 创建新的 MediaPlayer
            log.info("Android MediaPlayer: creating new instance via reflection");
            mediaPlayer = mpClass.getDeclaredConstructor().newInstance();
            log.info("Android MediaPlayer: new instance created: {}", mediaPlayer);

            // 设置数据源
            log.info("Android MediaPlayer: setDataSource({})", url);
            Method setDataSource = mpClass.getMethod("setDataSource", String.class);
            try {
                setDataSource.invoke(mediaPlayer, url);
                log.info("Android MediaPlayer: setDataSource success");
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                log.error("Android MediaPlayer: setDataSource failed: {}", cause == null ? "?" : cause.getMessage(), cause);
                if (cause != null) throw new Exception("setDataSource failed: " + cause.getMessage(), cause);
                throw e;
            }

            // 设置 OnPreparedListener：准备完成后开始播放
            log.info("Android MediaPlayer: setting OnPreparedListener");
            Object preparedListener = Proxy.newProxyInstance(
                onPreparedListenerClass.getClassLoader(),
                new Class[]{onPreparedListenerClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        log.info("Android MediaPlayer: onPrepared callback received");
                        if ("onPrepared".equals(method.getName())) {
                            try {
                                // 获取时长
                                Method getDuration = mpClass.getMethod("getDuration");
                                Object dur = getDuration.invoke(mediaPlayer);
                                if (dur instanceof Integer) {
                                    durationMs = (Integer) dur;
                                    log.info("Android MediaPlayer: duration = {}ms", durationMs);
                                }
                                // 开始播放
                                log.info("Android MediaPlayer: calling start()");
                                mpClass.getMethod("start").invoke(mediaPlayer);
                                log.info("Android MediaPlayer: start() returned, playback should begin");
                            } catch (Exception ex) {
                                log.error("Android MediaPlayer onPrepared error: {}", ex.getMessage(), ex);
                            }
                        }
                        return null;
                    }
                }
            );
            mpClass.getMethod("setOnPreparedListener", onPreparedListenerClass)
                .invoke(mediaPlayer, preparedListener);
            log.info("Android MediaPlayer: OnPreparedListener set");

            // 设置 OnCompletionListener：播放完成
            log.info("Android MediaPlayer: setting OnCompletionListener");
            Object completionListener = Proxy.newProxyInstance(
                onCompletionListenerClass.getClassLoader(),
                new Class[]{onCompletionListenerClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        log.info("Android MediaPlayer: onCompletion callback received");
                        if ("onCompletion".equals(method.getName())) {
                            notifyTrackEnded();
                            setState(STATE_STOPPED);
                        }
                        return null;
                    }
                }
            );
            mpClass.getMethod("setOnCompletionListener", onCompletionListenerClass)
                .invoke(mediaPlayer, completionListener);
            log.info("Android MediaPlayer: OnCompletionListener set");

            // 设置 OnErrorListener：错误处理
            log.info("Android MediaPlayer: setting OnErrorListener");
            Object errorListener = Proxy.newProxyInstance(
                onErrorListenerClass.getClassLoader(),
                new Class[]{onErrorListenerClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("onError".equals(method.getName())) {
                            int what = args.length > 1 ? (Integer) args[1] : -1;
                            int extra = args.length > 2 ? (Integer) args[2] : -1;
                            String msg = "MediaPlayer error (what=" + what + ", extra=" + extra + ")";
                            log.error("Android MediaPlayer: onError callback: {}", msg);
                            notifyError(msg);
                            setState(STATE_ERROR);
                            releaseInternal();
                            return true; // 表示已处理错误
                        }
                        return false;
                    }
                }
            );
            mpClass.getMethod("setOnErrorListener", onErrorListenerClass)
                .invoke(mediaPlayer, errorListener);
            log.info("Android MediaPlayer: OnErrorListener set");

            // 应用当前音量
            if (currentVolume >= 0) {
                log.info("Android MediaPlayer: applying currentVolume={}", currentVolume);
                setVolume(currentVolume);
            }

            // 异步准备
            log.info("Android MediaPlayer: calling prepareAsync() for url={}", url);
            mpClass.getMethod("prepareAsync").invoke(mediaPlayer);
            log.info("Android MediaPlayer: prepareAsync() returned, waiting for onPrepared callback");
        }

        @Override
        public void stop() {
            try {
                if (mediaPlayer != null) {
                    Method isPlaying = mpClass.getMethod("isPlaying");
                    Object playing = isPlaying.invoke(mediaPlayer);
                    if (Boolean.TRUE.equals(playing)) {
                        mpClass.getMethod("stop").invoke(mediaPlayer);
                    }
                }
            } catch (Exception e) {
                log.debug("Android MediaPlayer stop: {}", e.getMessage());
            }
            releaseInternal();
            durationMs = 0;
        }

        @Override
        public void pause() {
            try {
                if (mediaPlayer != null) {
                    mpClass.getMethod("pause").invoke(mediaPlayer);
                }
            } catch (Exception e) {
                log.debug("Android MediaPlayer pause: {}", e.getMessage());
            }
        }

        @Override
        public void resume() {
            try {
                if (mediaPlayer != null) {
                    mpClass.getMethod("start").invoke(mediaPlayer);
                }
            } catch (Exception e) {
                log.debug("Android MediaPlayer resume: {}", e.getMessage());
            }
        }

        @Override
        public void seek(long positionMs) {
            try {
                if (mediaPlayer != null) {
                    mpClass.getMethod("seekTo", int.class).invoke(mediaPlayer, (int) positionMs);
                }
            } catch (Exception e) {
                log.debug("Android MediaPlayer seek: {}", e.getMessage());
            }
        }

        @Override
        public void setVolume(float volume) {
            try {
                if (mediaPlayer != null) {
                    mpClass.getMethod("setVolume", float.class, float.class)
                        .invoke(mediaPlayer, volume, volume);
                }
            } catch (Exception e) {
                log.debug("Android MediaPlayer setVolume: {}", e.getMessage());
            }
        }

        @Override
        public long getPosition() {
            try {
                if (mediaPlayer != null) {
                    Method getCurrentPosition = mpClass.getMethod("getCurrentPosition");
                    Object pos = getCurrentPosition.invoke(mediaPlayer);
                    if (pos instanceof Integer) return (Integer) pos;
                }
            } catch (Exception e) {
                // ignore
            }
            return 0;
        }

        @Override
        public long getDuration() {
            return durationMs;
        }

        @Override
        public void destroy() {
            releaseInternal();
        }

        private void releaseInternal() {
            if (mediaPlayer == null) return;
            try {
                mpClass.getMethod("release").invoke(mediaPlayer);
            } catch (Exception e) {
                log.debug("Android MediaPlayer release: {}", e.getMessage());
            }
            mediaPlayer = null;
        }
    }

    // ---- JLayer + OpenAL 后端（纯 Java MP3 解码 + LWJGL OpenAL 播放）----
    // 在所有平台上工作，不依赖 javax.sound.sampled（Android 无实现）。
    // MC 已依赖 LWJGL，OpenAL 在运行时可用。

    private class JLayerBackend implements AudioBackend {
        private volatile InputStream audioStream;
        private volatile HttpURLConnection connection;
        private volatile Thread playThread;
        private volatile boolean stopped;

        // OpenAL 资源
        private int alSource = -1;
        private volatile float alVolume = 1.0f;
        private volatile boolean alInitialized = false;

        // 本地 MP3 总时长（毫秒），从首帧比特率 + 文件大小估算
        private volatile long durationMs = 0;
        // 播放起始时间戳（用于估算已播放时长）
        private volatile long playStartTime = 0;

        // 每帧解码的 PCM 缓冲区大小（样本数）
        private static final int BUFFER_SIZE = 4096;

        @Override
        public String name() { return "JLayer + OpenAL"; }

        @Override
        public void play(String url) throws Exception {
            log.info("JLayerBackend.play start: url={}", url);
            stop();
            stopped = false;

            log.info("OpenAL: opening connection to {}", url);
            long connStart = System.currentTimeMillis();
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "ZMusic/3.7");
            connection.setInstanceFollowRedirects(true);
            log.info("OpenAL: HTTP connecting (timeout: connect=15s, read=30s)");
            connection.connect();
            long connElapsed = System.currentTimeMillis() - connStart;
            log.info("OpenAL: HTTP connected in {}ms", connElapsed);

            int responseCode = connection.getResponseCode();
            String responseMsg = connection.getResponseMessage();
            log.info("OpenAL: HTTP response: {} {} (elapsed={}ms)", responseCode, responseMsg, connElapsed);
            if (responseCode != 200) {
                log.error("OpenAL: HTTP response not 200: {} {} for url={}", responseCode, responseMsg, url);
                throw new Exception("HTTP " + responseCode + " for " + url);
            }

            // 打印响应头关键信息
            int contentLength = connection.getContentLength();
            String contentType = connection.getContentType();
            String contentEncoding = connection.getContentEncoding();
            log.info("OpenAL: HTTP headers: Content-Type={}, Content-Length={}, Content-Encoding={}",
                contentType, contentLength, contentEncoding);

            // 读取配置决定使用下载模式还是流式模式
            boolean streamingMode = false;
            me.zhenxin.zmusic.config.ZMusicConfig cfg = ZMusic.getConfig();
            if (cfg != null) {
                streamingMode = cfg.isStreamingMode();
            }
            log.info("OpenAL: playback mode: {}", streamingMode ? "STREAMING (边下边播)" : "DOWNLOAD (完整下载)");

            if (streamingMode) {
                // 流式模式：直接使用 HTTP InputStream，边下边播
                // 优点：启动快，无需等待完整下载
                // 缺点：网络波动可能导致 underrun，无法精确估算时长
                audioStream = connection.getInputStream();
                log.info("OpenAL: streaming mode, using HTTP InputStream directly");
                // 流式模式下无法精确获取文件大小，时长估算设为 0（由 [Info] 包提供）
                durationMs = 0;
                playStartTime = System.currentTimeMillis();
            } else {
                // 下载模式：完整下载 MP3 到内存，避免流式播放因网络波动中断
                // 好处：1) 播放期间不再依赖网络；2) seek/循环更流畅；3) 避免 Android FCL 网络栈不稳定
                // 代价：需等待完整下载后才开始播放，大文件占用内存（典型 4 分钟 MP3 约 4-6MB）
                InputStream httpStream = connection.getInputStream();
                long downloadStart = System.currentTimeMillis();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(
                    contentLength > 0 ? contentLength : 64 * 1024);
                byte[] chunk = new byte[16 * 1024];
                int totalRead = 0;
                int lastProgressPct = -1;
                while (!stopped) {
                    int n = httpStream.read(chunk);
                    if (n < 0) break;
                    if (n == 0) continue;
                    baos.write(chunk, 0, n);
                    totalRead += n;
                    if (contentLength > 0) {
                        int pct = totalRead * 100 / contentLength;
                        if (pct >= lastProgressPct + 20) {
                            log.info("OpenAL: downloading MP3... {}% ({} / {} KB)",
                                pct, totalRead / 1024, contentLength / 1024);
                            lastProgressPct = pct;
                        }
                    }
                }
                httpStream.close();
                long downloadElapsed = System.currentTimeMillis() - downloadStart;
                byte[] mp3Data = baos.toByteArray();
                baos.close();
                log.info("OpenAL: MP3 fully downloaded to memory: {} KB in {}ms ({} bytes)",
                    mp3Data.length / 1024, downloadElapsed, mp3Data.length);

                if (stopped) {
                    log.info("OpenAL: stopped during download, aborting playback");
                    return;
                }
                if (mp3Data.length == 0) {
                    log.error("OpenAL: downloaded MP3 data is empty");
                    throw new Exception("Downloaded MP3 data is empty for " + url);
                }

                audioStream = new java.io.ByteArrayInputStream(mp3Data);
                log.info("OpenAL: ByteArrayInputStream created, available={}", audioStream.available());

                // 估算 MP3 总时长：读取首帧比特率，用文件大小计算
                durationMs = estimateMp3Duration(mp3Data);
                log.info("OpenAL: estimated MP3 duration: {}ms", durationMs);
                playStartTime = System.currentTimeMillis();
            }

            playThread = new Thread(() -> {
                log.info("OpenAL: playback thread started");
                try {
                    decodeAndPlay();
                } catch (Exception e) {
                    if (!stopped) {
                        log.error("OpenAL: playback error: {}", e.getMessage(), e);
                        notifyError(e.getMessage());
                        setState(STATE_ERROR);
                    } else {
                        log.info("OpenAL: playback exception after stop (ignored): {}", e.getMessage());
                    }
                } finally {
                    log.info("OpenAL: playback thread ending, cleaning up");
                    cleanupAlResources();
                    cleanupResources();
                    log.info("OpenAL: playback thread cleanup done");
                }
            }, "zmusic-playback");
            playThread.setDaemon(true);
            playThread.start();
            log.info("JLayerBackend.play: playback thread started, returning");
        }

        private void decodeAndPlay() throws Exception {
            int avail = -1;
            try {
                if (audioStream != null) avail = audioStream.available();
            } catch (Exception ignored) {
                // 流式模式下 available() 可能阻塞或抛异常，忽略
            }
            log.info("OpenAL: decodeAndPlay started, stream available={}", avail);
            javazoom.jl.decoder.Bitstream bitstream = new javazoom.jl.decoder.Bitstream(audioStream);
            Decoder decoder = new Decoder();
            log.info("OpenAL: JLayer Bitstream and Decoder created");

            initOpenAL();

            log.info("OpenAL: playback thread started, source={}", alSource);

            int numBuffers = 4;
            int[] buffers = new int[numBuffers];
            org.lwjgl.openal.AL10.alGenBuffers(buffers);
            checkAlError("alGenBuffers");
            log.info("OpenAL: buffers created: [{}, {}, {}, {}]", buffers[0], buffers[1], buffers[2], buffers[3]);

            int channels = 2;
            int sampleRate = 44100;
            int format = org.lwjgl.openal.AL10.AL_FORMAT_STEREO16;
            boolean firstFrame = true;
            int totalFramesDecoded = 0;
            long totalPcmBytes = 0;

            // 阶段 1：先填充所有 buffer 并排队，然后开始播放
            int initialFilled = 0;
            for (int i = 0; i < numBuffers && !stopped; i++) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    log.info("OpenAL: no more frames at initial fill (i={})", i);
                    break;
                }

                if (firstFrame) {
                    sampleRate = header.frequency();
                    channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
                    format = (channels == 1) ?
                        org.lwjgl.openal.AL10.AL_FORMAT_MONO16 :
                        org.lwjgl.openal.AL10.AL_FORMAT_STEREO16;
                    log.info("OpenAL: MP3 info: {}Hz, {}ch, format=0x{}, layer={}", sampleRate, channels,
                        Integer.toHexString(format), header.layer());
                    firstFrame = false;
                }

                // JLayer 的 decoder.decodeFrame 返回包含解码 PCM 数据的 SampleBuffer
                // 不能自建 SampleBuffer（会是空的），必须使用返回值
                Obuffer output = decoder.decodeFrame(header, bitstream);
                short[] samples;
                int sampleCount;
                if (output instanceof SampleBuffer) {
                    SampleBuffer sampleBuffer = (SampleBuffer) output;
                    samples = sampleBuffer.getBuffer();
                    sampleCount = sampleBuffer.getBufferLength();
                } else {
                    log.warn("OpenAL: frame {} decoder returned non-SampleBuffer: {}", initialFilled, output.getClass());
                    samples = new short[0];
                    sampleCount = 0;
                }

                log.info("OpenAL: frame {} decoded: {} samples, {} bytes PCM",
                    initialFilled, sampleCount, sampleCount * 2);

                if (sampleCount == 0) {
                    log.warn("OpenAL: frame {} decoded 0 samples, skipping", initialFilled);
                    bitstream.closeFrame();
                    continue;
                }

                ByteBuffer pcmBuffer = ByteBuffer.allocateDirect(sampleCount * 2);
                pcmBuffer.order(ByteOrder.LITTLE_ENDIAN);
                for (int j = 0; j < sampleCount; j++) {
                    pcmBuffer.putShort(samples[j]);
                }
                pcmBuffer.flip();

                // 检查前几个样本值，确认 PCM 数据非零
                int firstSample = samples.length > 0 ? samples[0] : 0;
                int midSample = samples.length > 0 ? samples[samples.length / 2] : 0;
                log.info("OpenAL: PCM samples: first={}, mid={}, buffer_limit={}", firstSample, midSample, pcmBuffer.limit());

                org.lwjgl.openal.AL10.alBufferData(buffers[i], format, pcmBuffer, sampleRate);
                checkAlError("alBufferData[" + i + "]");
                org.lwjgl.openal.AL10.alSourceQueueBuffers(alSource, buffers[i]);
                checkAlError("alSourceQueueBuffers");
                initialFilled++;
                bitstream.closeFrame();
            }

            if (initialFilled == 0) {
                log.warn("OpenAL: no audio frames decoded");
                org.lwjgl.openal.AL10.alDeleteBuffers(buffers);
                return;
            }

            log.info("OpenAL: initial fill complete, {} buffers queued", initialFilled);

            // 打印 OpenAL source 参数
            log.info("OpenAL: source params: AL_GAIN={}, AL_PITCH={}, AL_SOURCE_TYPE={}",
                org.lwjgl.openal.AL10.alGetSourcef(alSource, org.lwjgl.openal.AL10.AL_GAIN),
                org.lwjgl.openal.AL10.alGetSourcef(alSource, org.lwjgl.openal.AL10.AL_PITCH),
                org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_SOURCE_TYPE));

            // 开始播放
            org.lwjgl.openal.AL10.alSourcePlay(alSource);
            checkAlError("alSourcePlay");

            int state = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_SOURCE_STATE);
            int queued = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_BUFFERS_QUEUED);
            log.info("OpenAL: playback started, state={}(PLAYING=4114), queued={}", state, queued);

            // 阶段 2：流式播放，持续解码并填充回收的 buffer
            int frameCounter = initialFilled;
            long lastLogTime = System.currentTimeMillis();
            while (!stopped) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    log.info("OpenAL: end of stream at frame {}", frameCounter);
                    break;
                }

                // 使用 decoder.decodeFrame 的返回值（包含解码后的 PCM 数据）
                Obuffer output = decoder.decodeFrame(header, bitstream);
                short[] samples;
                int sampleCount;
                if (output instanceof SampleBuffer) {
                    SampleBuffer sampleBuffer = (SampleBuffer) output;
                    samples = sampleBuffer.getBuffer();
                    sampleCount = sampleBuffer.getBufferLength();
                } else {
                    samples = new short[0];
                    sampleCount = 0;
                }
                totalPcmBytes += sampleCount * 2;
                frameCounter++;
                totalFramesDecoded++;

                if (sampleCount == 0) {
                    log.warn("OpenAL: frame {} decoded 0 samples, skipping", frameCounter);
                    bitstream.closeFrame();
                    continue;
                }

                ByteBuffer pcmBuffer = ByteBuffer.allocateDirect(sampleCount * 2);
                pcmBuffer.order(ByteOrder.LITTLE_ENDIAN);
                for (int j = 0; j < sampleCount; j++) {
                    pcmBuffer.putShort(samples[j]);
                }
                pcmBuffer.flip();

                // 等待至少一个 buffer 被处理完
                int attempts = 0;
                while (org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED) == 0 && !stopped) {
                    Thread.sleep(5);
                    if (++attempts % 200 == 0) {
                        state = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_SOURCE_STATE);
                        queued = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_BUFFERS_QUEUED);
                        int processed = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED);
                        log.warn("OpenAL: waiting for free buffer ({}ms), state={}, queued={}, processed={}",
                            attempts * 5, state, queued, processed);
                    }
                }
                if (stopped) break;

                // 回收一个已处理的 buffer
                int[] unqueued = new int[1];
                org.lwjgl.openal.AL10.alSourceUnqueueBuffers(alSource, unqueued);
                checkAlError("alSourceUnqueueBuffers");

                // 填充新数据并重新排队
                org.lwjgl.openal.AL10.alBufferData(unqueued[0], format, pcmBuffer, sampleRate);
                checkAlError("alBufferData");
                org.lwjgl.openal.AL10.alSourceQueueBuffers(alSource, unqueued[0]);
                checkAlError("alSourceQueueBuffers");

                // 如果播放停止了（例如 underrun），重新开始
                state = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_SOURCE_STATE);
                if (state != org.lwjgl.openal.AL10.AL_PLAYING) {
                    org.lwjgl.openal.AL10.alSourcePlay(alSource);
                    log.warn("OpenAL: playback underrun at frame {}, restarting (state={})", frameCounter, state);
                }

                // 每 5 秒打印一次播放状态
                long now = System.currentTimeMillis();
                if (now - lastLogTime > 5000) {
                    state = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_SOURCE_STATE);
                    queued = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_BUFFERS_QUEUED);
                    int processed = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED);
                    float secOffset = 0;
                    try { secOffset = org.lwjgl.openal.AL10.alGetSourcef(alSource, org.lwjgl.openal.AL11.AL_SEC_OFFSET); } catch (Exception ignored) {}
                    log.info("OpenAL: status frame={}, state={}, queued={}, processed={}, sec_offset={}, total_pcm={}KB",
                        frameCounter, state, queued, processed, secOffset, totalPcmBytes / 1024);
                    lastLogTime = now;
                }

                bitstream.closeFrame();
            }

            log.info("OpenAL: decode loop ended, totalFrames={}, totalPcm={}KB, stopped={}",
                totalFramesDecoded, totalPcmBytes / 1024, stopped);

            if (!stopped) {
                log.info("OpenAL: all frames decoded, waiting for playback to finish");
                while (!stopped) {
                    int q = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_BUFFERS_QUEUED);
                    if (q == 0) break;
                    Thread.sleep(20);
                }
                log.info("OpenAL: playback completed naturally");
                notifyTrackEnded();
                setState(STATE_STOPPED);
            }

            org.lwjgl.openal.AL10.alDeleteBuffers(buffers);
        }

        private void checkAlError(String op) {
            int err = org.lwjgl.openal.AL10.alGetError();
            if (err != org.lwjgl.openal.AL10.AL_NO_ERROR) {
                log.error("OpenAL error in {}: 0x{}", op, Integer.toHexString(err));
            }
        }

        private void initOpenAL() {
            // 尝试在当前线程设置 OpenAL 上下文
            // MC 在 Render thread 上初始化 OpenAL，播放线程需要确保上下文可用
            try {
                long context = org.lwjgl.openal.ALC10.alcGetCurrentContext();
                if (context != 0) {
                    long device = org.lwjgl.openal.ALC10.alcGetContextsDevice(context);
                    org.lwjgl.openal.ALCCapabilities deviceCaps = org.lwjgl.openal.ALC.createCapabilities(device);
                    org.lwjgl.openal.AL.createCapabilities(deviceCaps);
                    log.info("OpenAL: context set on playback thread");
                }
            } catch (Exception e) {
                log.warn("OpenAL: failed to set context on playback thread: {}", e.getMessage());
            }

            org.lwjgl.openal.AL10.alGetError(); // 清除错误
            alSource = org.lwjgl.openal.AL10.alGenSources();
            org.lwjgl.openal.AL10.alSourcef(alSource, org.lwjgl.openal.AL10.AL_GAIN, alVolume);
            org.lwjgl.openal.AL10.alSourcef(alSource, org.lwjgl.openal.AL10.AL_PITCH, 1.0f);
            alInitialized = true;
            log.info("OpenAL: source created: {}", alSource);
        }

        private void cleanupAlResources() {
            if (alInitialized && alSource != -1) {
                try {
                    org.lwjgl.openal.AL10.alSourceStop(alSource);
                    // 清空队列
                    int queued = org.lwjgl.openal.AL10.alGetSourcei(alSource, org.lwjgl.openal.AL10.AL_BUFFERS_QUEUED);
                    while (queued-- > 0) {
                        int[] unqueued = new int[1];
                        org.lwjgl.openal.AL10.alSourceUnqueueBuffers(alSource, unqueued);
                    }
                    org.lwjgl.openal.AL10.alDeleteSources(alSource);
                    log.info("OpenAL: source {} deleted", alSource);
                } catch (Exception e) {
                    log.debug("OpenAL: cleanup error: {}", e.getMessage());
                }
                alSource = -1;
                alInitialized = false;
            }
        }

        @Override
        public void stop() {
            stopped = true;
            if (playThread != null && playThread.isAlive() && Thread.currentThread() != playThread) {
                try {
                    playThread.interrupt();
                    playThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            playThread = null;
            durationMs = 0;
            playStartTime = 0;
            cleanupAlResources();
            cleanupResources();
        }

        @Override
        public void pause() {
            if (alInitialized && alSource != -1) {
                org.lwjgl.openal.AL10.alSourcePause(alSource);
            }
        }

        @Override
        public void resume() {
            if (alInitialized && alSource != -1) {
                org.lwjgl.openal.AL10.alSourcePlay(alSource);
            }
        }

        @Override
        public void seek(long positionMs) {
            log.warn("OpenAL: seek not supported");
        }

        @Override
        public void setVolume(float volume) {
            alVolume = volume;
            if (alInitialized && alSource != -1) {
                org.lwjgl.openal.AL10.alSourcef(alSource, org.lwjgl.openal.AL10.AL_GAIN, volume);
            }
        }

        @Override
        public long getPosition() {
            // 优先使用 OpenAL 的 AL_SEC_OFFSET（精确的已播放时长）
            if (alInitialized && alSource != -1) {
                try {
                    float sec = org.lwjgl.openal.AL10.alGetSourcef(alSource, org.lwjgl.openal.AL11.AL_SEC_OFFSET);
                    if (sec > 0) {
                        return (long) (sec * 1000);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            // 回退：用播放起始时间估算
            if (playStartTime > 0) {
                long elapsed = System.currentTimeMillis() - playStartTime;
                if (durationMs > 0 && elapsed > durationMs) elapsed = durationMs;
                return elapsed;
            }
            return 0;
        }

        @Override
        public long getDuration() {
            return durationMs;
        }

        /**
         * 估算 MP3 总时长（毫秒）。
         *
         * <p>方法：读取首帧比特率，用文件大小估算。
         * 公式：时长 = 文件大小 * 8 / 比特率 * 1000。
         * 对 CBR MP3 准确，对 VBR MP3 为近似值。</p>
         *
         * @param mp3Data MP3 文件字节数组
         * @return 估算的总时长（毫秒），失败返回 0
         */
        private long estimateMp3Duration(byte[] mp3Data) {
            try {
                java.io.ByteArrayInputStream probe = new java.io.ByteArrayInputStream(mp3Data);
                javazoom.jl.decoder.Bitstream bs = new javazoom.jl.decoder.Bitstream(probe);
                Header header = bs.readFrame();
                if (header == null) {
                    bs.close();
                    return 0;
                }
                // 获取比特率（bps）
                int bitrate = header.bitrate();
                bs.close();
                if (bitrate <= 0) return 0;
                // 时长(ms) = 文件大小(byte) * 8 / 比特率(bit/s) * 1000
                long duration = (long) ((double) mp3Data.length * 8 / bitrate * 1000);
                log.info("OpenAL: duration estimate: file={}KB, bitrate={}kbps, duration={}ms",
                    mp3Data.length / 1024, bitrate / 1000, duration);
                return duration;
            } catch (Exception e) {
                log.warn("OpenAL: failed to estimate MP3 duration: {}", e.getMessage());
                return 0;
            }
        }

        @Override
        public void destroy() {
            stop();
        }

        private void cleanupResources() {
            if (audioStream != null) {
                try {
                    audioStream.close();
                } catch (Exception e) {
                    log.debug("OpenAL: stream close error: {}", e.getMessage());
                }
                audioStream = null;
            }
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Exception e) {
                    log.debug("OpenAL: connection disconnect error: {}", e.getMessage());
                }
                connection = null;
            }
        }
    }
}
