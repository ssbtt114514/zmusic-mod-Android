package me.ssbtt.amusic.playlist;

import lombok.Getter;
import lombok.Setter;
import me.ssbtt.amusic.history.HistoryEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌单。
 *
 * <p>v2 格式新增字段：</p>
 * <ul>
 *   <li>{@link #id} - 6位字母数字唯一标识符（作为文件名）</li>
 *   <li>{@link #author} - 歌单提供者（玩家名）</li>
 *   <li>{@link #isPublic} - 是否公开（false 则不向其他玩家展示）</li>
 *   <li>{@link #format} - 格式版本（当前为 2）</li>
 * </ul>
 *
 * <p>加载老版本歌单（无 format 字段）时会自动转换为新格式。</p>
 *
 * @author 真心
 * @since 2026-07-25
 */
@Getter
@Setter
public class Playlist {
    /** 格式版本 */
    private int format = 2;
    /** 6位字母数字唯一标识符（作为文件名） */
    private String id;
    /** 歌单名称 */
    private String name;
    /** 歌单提供者（玩家名） */
    private String author;
    /** 是否公开（false 则不向其他玩家展示） */
    private boolean isPublic = true;
    /** 歌曲列表 */
    private List<HistoryEntry> songs;
    /** 播放顺序 */
    private PlayOrder playOrder;
    /** 当前播放索引（-1 表示未开始） */
    private int currentIndex;

    public Playlist() {
        this.songs = new ArrayList<>();
        this.playOrder = PlayOrder.SEQUENCE;
        this.currentIndex = -1;
        this.format = 2;
        this.isPublic = true;
        this.id = generateId();
    }

    public Playlist(String name) {
        this();
        this.name = name;
    }

    /**
     * 生成 6 位字母数字唯一标识符。
     *
     * @return 6 位标识符（小写字母+数字）
     */
    public static String generateId() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
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
