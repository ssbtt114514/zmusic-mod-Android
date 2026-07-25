package me.zhenxin.zmusic.playlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.history.HistoryEntry;
import me.zhenxin.zmusic.history.HistoryManager;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 歌单管理器。
 *
 * <p>歌单存储在 {@code config/AMusic/list/歌单名.json}，与 {@code name} 文件夹同级。</p>
 *
 * @author ssbtt
 * @since 2026-07-25
 */
@Log4j2
public class PlaylistManager {
    private static final String ROOT_DIR = "AMusic";
    private static final String LIST_DIR = "list";

    private final File listDir;
    private final Gson gson;

    public PlaylistManager(File configDir) {
        this.listDir = new File(new File(configDir, ROOT_DIR), LIST_DIR);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * 列出所有歌单名称。
     *
     * @return 歌单名列表
     */
    public List<String> listPlaylists() {
        List<String> names = new ArrayList<>();
        File[] files = listDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return names;
        for (File f : files) {
            String n = f.getName();
            names.add(n.substring(0, n.length() - 5));
        }
        return names;
    }

    /**
     * 加载歌单。
     *
     * @param name 歌单名
     * @return 歌单对象，不存在则返回 null
     */
    public Playlist loadPlaylist(String name) {
        File file = new File(listDir, HistoryManager.sanitizeFileName(name) + ".json");
        if (!file.exists()) return null;
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> map = gson.fromJson(json, type);
            if (map == null) return null;
            Playlist pl = new Playlist(name);
            Object orderObj = map.get("playOrder");
            if (orderObj instanceof String) {
                try {
                    pl.setPlayOrder(PlayOrder.valueOf((String) orderObj));
                } catch (IllegalArgumentException ignored) {
                }
            }
            Object songsObj = map.get("songs");
            if (songsObj instanceof List) {
                Type entryListType = new TypeToken<List<HistoryEntry>>() {}.getType();
                String songsJson = gson.toJson(songsObj);
                List<HistoryEntry> songs = gson.fromJson(songsJson, entryListType);
                if (songs != null) {
                    pl.setSongs(songs);
                }
            }
            return pl;
        } catch (Exception e) {
            log.warn("Failed to load playlist {}: {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * 保存歌单。
     *
     * @param playlist 歌单
     */
    public void savePlaylist(Playlist playlist) {
        if (playlist == null || playlist.getName() == null) return;
        try {
            if (!listDir.exists() && !listDir.mkdirs()) {
                log.warn("Failed to create list dir: {}", listDir);
                return;
            }
            Map<String, Object> map = new HashMap<>();
            map.put("name", playlist.getName());
            map.put("playOrder", playlist.getPlayOrder().name());
            map.put("songs", playlist.getSongs());
            File file = new File(listDir, HistoryManager.sanitizeFileName(playlist.getName()) + ".json");
            Files.write(file.toPath(), gson.toJson(map).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Failed to save playlist {}: {}", playlist.getName(), e.getMessage());
        }
    }

    /**
     * 创建新歌单。
     *
     * @param name 歌单名
     * @return true 表示创建成功，false 表示已存在
     */
    public boolean createPlaylist(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        File file = new File(listDir, HistoryManager.sanitizeFileName(name) + ".json");
        if (file.exists()) return false;
        Playlist pl = new Playlist(name);
        savePlaylist(pl);
        return true;
    }

    /**
     * 删除歌单。
     *
     * @param name 歌单名
     * @return true 表示删除成功
     */
    public boolean deletePlaylist(String name) {
        File file = new File(listDir, HistoryManager.sanitizeFileName(name) + ".json");
        return file.exists() && file.delete();
    }

    /**
     * 添加歌曲到歌单。
     *
     * @param playlistName 歌单名
     * @param entry        歌曲
     * @return true 表示添加成功
     */
    public boolean addSong(String playlistName, HistoryEntry entry) {
        Playlist pl = loadPlaylist(playlistName);
        if (pl == null) return false;
        pl.addSong(entry);
        savePlaylist(pl);
        return true;
    }

    /**
     * 从歌单移除歌曲。
     *
     * @param playlistName 歌单名
     * @param songName     歌名
     * @return true 表示移除成功
     */
    public boolean removeSong(String playlistName, String songName) {
        Playlist pl = loadPlaylist(playlistName);
        if (pl == null) return false;
        boolean removed = pl.removeSong(songName);
        if (removed) {
            savePlaylist(pl);
        }
        return removed;
    }
}
