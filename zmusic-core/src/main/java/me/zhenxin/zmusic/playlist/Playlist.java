package me.zhenxin.zmusic.playlist;

import me.zhenxin.zmusic.history.HistoryEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌单。
 *
 * @author 真心
 * @since 2026-07-25
 */
public class Playlist {
    private String name;
    private List<HistoryEntry> songs;
    private PlayOrder playOrder;
    /** 当前播放索引（-1 表示未开始） */
    private int currentIndex;

    public Playlist() {
        this.songs = new ArrayList<>();
        this.playOrder = PlayOrder.SEQUENCE;
        this.currentIndex = -1;
    }

    public Playlist(String name) {
        this();
        this.name = name;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<HistoryEntry> getSongs() {
        return songs;
    }

    public void setSongs(List<HistoryEntry> songs) {
        this.songs = songs == null ? new ArrayList<>() : songs;
    }

    public PlayOrder getPlayOrder() { return playOrder; }
    public void setPlayOrder(PlayOrder playOrder) { this.playOrder = playOrder; }

    public int getCurrentIndex() { return currentIndex; }
    public void setCurrentIndex(int currentIndex) { this.currentIndex = currentIndex; }

    /**
     * 添加歌曲（去重：同名不重复添加）。
     *
     * @param entry 歌曲条目
     */
    public void addSong(HistoryEntry entry) {
        if (entry == null || entry.getName() == null) return;
        for (HistoryEntry e : songs) {
            if (entry.getName().equals(e.getName())) {
                return;
            }
        }
        songs.add(entry);
    }

    public boolean removeSong(String name) {
        return songs.removeIf(e -> name.equals(e.getName()));
    }

    public int size() {
        return songs.size();
    }
}
