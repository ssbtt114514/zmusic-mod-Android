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
        playCurrent();
    }

    /**
     * 停止歌单播放。
     */
    public void stop() {
        activePlaylist = null;
        currentIndex = -1;
    }

    /**
     * 手动下一首。
     */
    public void next() {
        if (!isActive()) return;
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
     * <p>根据播放顺序决定下一首动作：</p>
     * <ul>
     *   <li>SINGLE - 重新播放当前歌曲</li>
     *   <li>RANDOM - 随机选择下一首</li>
     *   <li>LOOP - 循环到下一首（末尾回到开头）</li>
     *   <li>SEQUENCE - 顺序播放下一首，末尾则停止</li>
     * </ul>
     */
    public void onTrackEnded() {
        if (!isActive()) return;
        int next = computeNextIndex(true);
        if (next < 0) {
            log.info("Playlist finished (SEQUENCE mode reached end)");
            stop();
            return;
        }
        currentIndex = next;
        playCurrent();
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
