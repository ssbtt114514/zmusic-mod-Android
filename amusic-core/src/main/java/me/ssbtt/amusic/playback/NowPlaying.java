package me.ssbtt.amusic.playback;

/**
 * 当前播放歌曲信息。
 *
 * <p>由服务端推送的 {@code [Info]} 包解析得到，用于历史记录与界面展示。</p>
 *
 * @author 真心
 * @since 2026-07-25
 */
public final class NowPlaying {
    private volatile String name = "";
    private volatile String singer = "";
    private volatile String platform = "";
    private volatile String source = "";
    private volatile String url = "";
    private volatile String currentLyric = "";
    private volatile long positionSec = 0;
    private volatile long durationSec = 0;
    private volatile long playStartTime = 0;

    public String getName() { return name; }
    public String getSinger() { return singer; }
    public String getPlatform() { return platform; }
    public String getSource() { return source; }
    public String getUrl() { return url; }
    public String getCurrentLyric() { return currentLyric; }
    public long getPositionSec() { return positionSec; }
    public long getDurationSec() { return durationSec; }
    public long getPlayStartTime() { return playStartTime; }

    public void setName(String name) { this.name = name == null ? "" : name; }
    public void setSinger(String singer) { this.singer = singer == null ? "" : singer; }
    public void setPlatform(String platform) { this.platform = platform == null ? "" : platform; }
    public void setSource(String source) { this.source = source == null ? "" : source; }
    public void setUrl(String url) { this.url = url == null ? "" : url; }
    public void setCurrentLyric(String currentLyric) { this.currentLyric = currentLyric == null ? "" : currentLyric; }
    public void setPositionSec(long positionSec) { this.positionSec = positionSec; }
    public void setDurationSec(long durationSec) { this.durationSec = durationSec; }
    public void setPlayStartTime(long playStartTime) { this.playStartTime = playStartTime; }

    /**
     * 判断是否为新歌曲（歌名非空且与当前不同）。
     *
     * @param newName 新歌名
     * @return true 表示是新歌
     */
    public boolean isNewSong(String newName) {
        if (newName == null || newName.isEmpty()) {
            return false;
        }
        return !newName.equals(this.name);
    }

    /**
     * 获取完整歌曲名（歌名 - 歌手）。
     *
     * @return 完整名
     */
    public String getFullName() {
        StringBuilder sb = new StringBuilder(name);
        if (!singer.isEmpty()) {
            sb.append(" - ").append(singer);
        }
        return sb.toString();
    }

    /**
     * 重置为空状态。
     */
    public void reset() {
        name = "";
        singer = "";
        platform = "";
        source = "";
        url = "";
        currentLyric = "";
        positionSec = 0;
        durationSec = 0;
        playStartTime = 0;
    }
}
