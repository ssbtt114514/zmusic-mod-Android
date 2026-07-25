package me.zhenxin.zmusic.history;

/**
 * 历史记录条目。
 *
 * @author 真心
 * @since 2026-07-25
 */
public class HistoryEntry {
    private String name;
    private String url;
    private String platform;
    private long time;

    public HistoryEntry() {
    }

    public HistoryEntry(String name, String url, String platform, long time) {
        this.name = name;
        this.url = url;
        this.platform = platform;
        this.time = time;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public long getTime() { return time; }
    public void setTime(long time) { this.time = time; }
}
