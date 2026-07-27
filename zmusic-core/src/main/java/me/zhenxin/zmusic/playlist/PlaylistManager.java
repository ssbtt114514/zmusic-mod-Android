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
 * <p>歌单存储在 {@code config/AMusic/list/<id>.json}，其中 {@code <id>} 是 6 位字母数字标识符。
 * 歌单名称存储在 JSON 文件内部，不再作为文件名。</p>
 *
 * <p>支持老版本歌单（以歌单名为文件名，无 format 字段）自动转换：
 * 加载时检测 format 字段，不存在则视为 v1 格式，补充默认值后以新格式重新保存。</p>
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
     * <p>同时兼容新格式（以 id 为文件名）和老格式（以歌单名为文件名）。
     * 老格式文件会在首次加载时自动迁移为新格式。</p>
     *
     * @return 歌单名列表
     */
    public List<String> listPlaylists() {
        List<String> names = new ArrayList<>();
        File[] files = listDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return names;
        for (File f : files) {
            String fileName = f.getName();
            String baseName = fileName.substring(0, fileName.length() - 5);
            // 尝试读取歌单名称
            try {
                String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> map = gson.fromJson(json, type);
                if (map != null && map.get("name") instanceof String) {
                    names.add((String) map.get("name"));
                } else {
                    // 老格式无 name 字段，用文件名
                    names.add(baseName);
                }
            } catch (Exception e) {
                names.add(baseName);
            }
        }
        return names;
    }

    /**
     * 加载歌单。
     *
     * <p>支持新老两种格式：
     * - 新格式：文件名为 6 位 id，JSON 中含 format/name/id/author/public 字段
     * - 老格式：文件名为歌单名，JSON 中无 format 字段</p>
     *
     * <p>加载老格式后会自动保存为新格式（迁移）。</p>
     *
     * @param name 歌单名
     * @return 歌单对象，不存在则返回 null
     */
    public Playlist loadPlaylist(String name) {
        if (name == null) return null;
        // 先尝试按歌单名查找文件（老格式兼容）
        File file = findPlaylistFile(name);
        if (file == null || !file.exists()) return null;
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> map = gson.fromJson(json, type);
            if (map == null) return null;

            Playlist pl = new Playlist();
            // 解析格式版本
            Object formatObj = map.get("format");
            boolean isV2 = formatObj instanceof Number && ((Number) formatObj).intValue() >= 2;

            // 基本信息
            pl.setName(name);
            Object nameObj = map.get("name");
            if (nameObj instanceof String) {
                pl.setName((String) nameObj);
            }

            if (isV2) {
                // v2 格式：读取新字段
                Object idObj = map.get("id");
                if (idObj instanceof String) {
                    pl.setId((String) idObj);
                }
                Object authorObj = map.get("author");
                if (authorObj instanceof String) {
                    pl.setAuthor((String) authorObj);
                }
                Object publicObj = map.get("public");
                if (publicObj instanceof Boolean) {
                    pl.setPublic((Boolean) publicObj);
                }
            }
            // 老格式：保持默认值（id 已在构造函数生成，author=null，isPublic=true）

            // 播放顺序
            Object orderObj = map.get("playOrder");
            if (orderObj instanceof String) {
                try {
                    pl.setPlayOrder(PlayOrder.valueOf((String) orderObj));
                } catch (IllegalArgumentException ignored) {
                }
            }

            // 歌曲列表
            Object songsObj = map.get("songs");
            if (songsObj instanceof List) {
                Type entryListType = new TypeToken<List<HistoryEntry>>() {}.getType();
                String songsJson = gson.toJson(songsObj);
                List<HistoryEntry> songs = gson.fromJson(songsJson, entryListType);
                if (songs != null) {
                    pl.setSongs(songs);
                }
            }

            // 如果是老格式，迁移为新格式保存
            if (!isV2) {
                log.info("Migrating playlist '{}' from v1 to v2 format", name);
                savePlaylist(pl);
                // 删除老文件（如果文件名是歌单名而非 id）
                String oldFileName = HistoryManager.sanitizeFileName(name) + ".json";
                if (file.getName().equals(oldFileName) && !file.getName().equals(pl.getId() + ".json")) {
                    file.delete();
                }
            }

            return pl;
        } catch (Exception e) {
            log.warn("Failed to load playlist {}: {}", name, e.getMessage());
            return null;
        }
    }

    /**
     * 查找歌单文件（兼容新老格式）。
     *
     * @param name 歌单名
     * @return 歌单文件，不存在返回 null
     */
    private File findPlaylistFile(String name) {
        // 新格式：按 id 查找（遍历所有文件，匹配 JSON 中的 name 字段）
        File[] files = listDir.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try {
                    String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                    Type type = new TypeToken<Map<String, Object>>() {}.getType();
                    Map<String, Object> map = gson.fromJson(json, type);
                    if (map != null) {
                        Object nameObj = map.get("name");
                        if (nameObj instanceof String && name.equals(nameObj)) {
                            return f;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        // 老格式：按歌单名查找文件
        File oldFile = new File(listDir, HistoryManager.sanitizeFileName(name) + ".json");
        if (oldFile.exists()) return oldFile;
        return null;
    }

    /**
     * 保存歌单（v2 格式）。
     *
     * <p>文件名为 6 位 id，JSON 中包含所有字段。</p>
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
            // 确保 id 存在
            if (playlist.getId() == null || playlist.getId().isEmpty()) {
                playlist.setId(Playlist.generateId());
            }
            Map<String, Object> map = new HashMap<>();
            map.put("format", 2);
            map.put("id", playlist.getId());
            map.put("name", playlist.getName());
            map.put("author", playlist.getAuthor());
            map.put("public", playlist.isPublic());
            map.put("playOrder", playlist.getPlayOrder() != null ? playlist.getPlayOrder().name() : PlayOrder.SEQUENCE.name());
            map.put("songs", playlist.getSongs());

            // 以 id 作为文件名
            File file = new File(listDir, playlist.getId() + ".json");
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
        if (loadPlaylist(name) != null) return false;
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
        File file = findPlaylistFile(name);
        return file != null && file.exists() && file.delete();
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
