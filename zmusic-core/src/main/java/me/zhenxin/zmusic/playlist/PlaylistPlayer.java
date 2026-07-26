package me.zhenxin.zmusic.playlist;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.history.HistoryEntry;

import java.util.List;
import java.util.Random;

/**
 * 歌单播放控制器。
 *
 * <p>管理当前活动歌单的播放状态，根据 {@link PlayOrder} 自动推进：</p>
 * <ul>
 *   <li>{@link PlayOrder#SEQUENCE} - 顺序播放，播完最后一首停止</li>
 *   <li>{@link PlayOrder#RANDOM} - 随机播放，随机选择下一首</li>
 *   <li>{@link PlayOrder#LOOP} - 列表循环，播完最后一首回到第一首</li>
 *   <li>{@link PlayOrder#SINGLE} - 单曲循环，重复播放当前歌曲</li>
 * </ul>
 *
 * <p>实际播放动作通过 {@link PlayCallback} 回调由平台模块实现（发送点歌命令）。</p>
 *
 * @author 真心
 * @since 2026-07-25
 */
@Log4j2
public class PlaylistPlayer {

    /**
     * 播放回调，由平台模块实现以发送点歌命令。
     */
    public interface PlayCallback {
        /**
         * 播放指定歌曲。
         *
         * @param entry 歌曲条目
         */
        void play(HistoryEntry entry);
    }

    private volatile Playlist activePlaylist;
    private volatile int currentIndex = -1;
    /** 待播放的下一首索引（>= 0 时表示有挂起的播放请求，等播放器空闲时触发）。
     *  用于 LOOP/RANDOM/SINGLE 模式：歌曲自然结束后不立即播放下一首，
     *  而是等播放器真正停止后由 tick 触发。这样播完的歌曲仍为"当前歌曲"。 */
    private volatile int pendingNextIndex = -1;
    private volatile PlayCallback callback;
    private final Random random = new Random();

    /**
     * 设置播放回调。
     *
     * @param callback 回调
     */
    public void setCallback(PlayCallback callback) {
        this.callback = callback;
    }

    /**
     * 当前是否有活动歌单正在播放。
     *
     * @return true 表示歌单播放中
     */
    public boolean isActive() {
        return activePlaylist != null && currentIndex >= 0;
    }

    /**
     * 获取活动歌单。
     */
    public Playlist getActivePlaylist() {
        return activePlaylist;
    }

    /**
     * 获取当前播放索引。
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * 开始播放歌单。
     *
     * @param playlist   歌单
     * @param startIndex 起始索引（-1 表示从头开始）
     */
    public void start(Playlist playlist, int startIndex) {
        if (playlist == null || playlist.size() == 0) {
            log.warn("Cannot start playlist: empty or null");
            return;
        }
        this.activePlaylist = playlist;
        int idx = startIndex;
        if (idx < 0 || idx >= playlist.size()) {
            idx = 0;
        }
        this.currentIndex = idx;
        this.pendingNextIndex = -1;
        playCurrent();
    }

    /**
     * 停止歌单播放。
     */
    public void stop() {
        activePlaylist = null;
        currentIndex = -1;
        pendingNextIndex = -1;
    }

    /**
     * 手动下一首。
     */
    public void next() {
        if (!isActive()) return;
        pendingNextIndex = -1;
        currentIndex = computeNextIndex(false);
        if (currentIndex >= 0) {
            playCurrent();
        } else {
            stop();
        }
    }

    /**
     * 手动上一首。
     */
    public void previous() {
        if (!isActive()) return;
        pendingNextIndex = -1;
        int size = activePlaylist.size();
        if (size == 0) {
            stop();
            return;
        }
        currentIndex = (currentIndex - 1 + size) % size;
        playCurrent();
    }

    /**
     * 当前歌曲播放结束时的回调（由 ZMusicPlayer onTrackEnded 触发）。
     *
     * <p>所有播放模式统一行为：设置 pendingNextIndex 但不立即播放，也不推进 currentIndex。
     * 实际播放由 {@link #tryPlayIfIdle()} 在播放器状态变为 STOPPED 时触发
     *（由 ZMusic.onStateChanged 回调调用）。</p>
     *
     * <p>这样播完的歌曲在下一首实际开始前仍为"当前歌曲"（currentIndex 不变），
     * 满足"如果一首歌播放完了，那么这首歌是当前的，不是上一首"的需求。</p>
     *
     * <p>SEQUENCE 模式下如果已到末尾，则停止歌单播放。</p>
     */
    public void onTrackEnded() {
        if (!isActive()) return;
        PlayOrder order = activePlaylist.getPlayOrder();
        if (order == null) order = PlayOrder.SEQUENCE;

        int next = computeNextIndex(true);
        if (next < 0) {
            log.info("Playlist finished (SEQUENCE mode reached end)");
            stop();
            return;
        }
        // 仅设置待播索引，不推进 currentIndex
        // tryPlayIfIdle 会在播放器 STOPPED 时由状态回调触发
        pendingNextIndex = next;
        log.info("Track ended, pending next index: {} (mode={}, current={})",
                pendingNextIndex, order, currentIndex);
    }

    /**
     * 尝试在播放器空闲时播放待播歌曲。
     *
     * <p>由 ZMusic.onStateChanged 回调调用：当播放器状态变为 STOPPED（MP3 播放完毕）时，
     * 如果有待播歌曲则推进 currentIndex 并播放。</p>
     *
     * @return true 表示触发了播放
     */
    public boolean tryPlayIfIdle() {
        if (!isActive() || pendingNextIndex < 0) return false;
        int next = pendingNextIndex;
        pendingNextIndex = -1;
        currentIndex = next;
        log.info("Playlist idle-triggered play: index={} (mode={})", currentIndex,
                activePlaylist != null ? activePlaylist.getPlayOrder() : "?");
        playCurrent();
        return true;
    }

    /**
     * 播放器停止时的统一处理（由 ZMusic.onStateChanged 回调调用）。
     *
     * <p>当播放器状态变为 STOPPED 时，如果歌单活动且没有待播歌曲，
     * 视为当前歌曲结束，计算并播放下一首。</p>
     *
     * <p>这处理了两种情况：</p>
     * <ul>
     *   <li>JLayerBackend 自然结束：先调用 onTrackEnded 设置 pendingNextIndex，
     *       然后状态变化触发本方法，直接 tryPlayIfIdle</li>
     *   <li>服务器 [Stop] 包导致停止：onTrackEnded 未被调用，pendingNextIndex &lt; 0，
     *       本方法先调用 onTrackEnded 计算下一首，再 tryPlayIfIdle</li>
     * </ul>
     *
     * <p>用户手动停止（调用 {@link #stop()}）时 isActive() 返回 false，本方法直接返回，
     * 不会触发下一首。</p>
     */
    public void handlePlayerStopped() {
        if (!isActive()) return;
        if (pendingNextIndex < 0) {
            // onTrackEnded 未被调用（可能是服务器 [Stop] 包导致），
            // 主动计算下一首
            log.info("Player stopped without onTrackEnded, computing next index");
            onTrackEnded();
        }
        tryPlayIfIdle();
    }

    /**
     * 计算下一首索引。
     *
     * @param autoAdvance true 表示自动推进（歌曲自然结束），false 表示手动跳过
     * @return 下一首索引，-1 表示应停止
     */
    private int computeNextIndex(boolean autoAdvance) {
        if (activePlaylist == null || activePlaylist.size() == 0) return -1;
        int size = activePlaylist.size();
        PlayOrder order = activePlaylist.getPlayOrder();
        if (order == null) order = PlayOrder.SEQUENCE;

        switch (order) {
            case SINGLE:
                // 单曲循环：自动推进时重播当前；手动跳过时进入下一首
                if (autoAdvance) {
                    return currentIndex;
                }
                return (currentIndex + 1) % size;
            case RANDOM: {
                if (size == 1) return 0;
                int r;
                do {
                    r = random.nextInt(size);
                } while (r == currentIndex);
                return r;
            }
            case LOOP:
                return (currentIndex + 1) % size;
            case SEQUENCE:
            default: {
                int next = currentIndex + 1;
                if (next >= size) {
                    return -1;
                }
                return next;
            }
        }
    }

    /**
     * 播放当前索引的歌曲。
     */
    private void playCurrent() {
        if (activePlaylist == null || currentIndex < 0) return;
        List<HistoryEntry> songs = activePlaylist.getSongs();
        if (currentIndex >= songs.size()) {
            log.warn("Playlist index out of range: {} >= {}", currentIndex, songs.size());
            stop();
            return;
        }
        HistoryEntry entry = songs.get(currentIndex);
        if (entry == null) {
            log.warn("Playlist entry null at index {}", currentIndex);
            return;
        }
        log.info("Playlist playing [{}]: {} ({}/{})",
                activePlaylist.getPlayOrder(), entry.getName(),
                currentIndex + 1, activePlaylist.size());
        if (callback != null) {
            callback.play(entry);
        } else {
            log.warn("PlaylistPlayer callback not set, cannot play");
        }
    }
}
