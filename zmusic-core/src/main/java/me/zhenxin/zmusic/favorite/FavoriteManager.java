package me.zhenxin.zmusic.favorite;

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
import java.util.List;

/**
 * 收藏管理器。
 *
 * <p>收藏存储在 {@code config/AMusic/favorite.json}，与 {@code name} 文件夹同级。</p>
 *
 * @author ssbtt
 * @since 2026-07-25
 */
@Log4j2
public class FavoriteManager {
    private static final String ROOT_DIR = "AMusic";
    private static final String FAVORITE_FILE = "favorite.json";

    private final File favoriteFile;
    private final Gson gson;
    private final List<HistoryEntry> favorites;

    public FavoriteManager(File configDir) {
        File rootDir = new File(configDir, ROOT_DIR);
        this.favoriteFile = new File(rootDir, FAVORITE_FILE);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.favorites = new ArrayList<>();
        load();
    }

    /**
     * 添加收藏（去重）。
     *
     * @param entry 歌曲
     * @return true 表示新增成功，false 表示已存在
     */
    public synchronized boolean add(HistoryEntry entry) {
        if (entry == null || entry.getName() == null) return false;
        for (HistoryEntry e : favorites) {
            if (entry.getName().equals(e.getName())) {
                return false;
            }
        }
        favorites.add(entry);
        save();
        return true;
    }

    /**
     * 移除收藏。
     *
     * @param name 歌名
     * @return true 表示移除成功
     */
    public synchronized boolean remove(String name) {
        boolean removed = favorites.removeIf(e -> name.equals(e.getName()));
        if (removed) {
            save();
        }
        return removed;
    }

    /**
     * 判断是否已收藏。
     *
     * @param name 歌名
     * @return true 表示已收藏
     */
    public synchronized boolean isFavorite(String name) {
        if (name == null) return false;
        for (HistoryEntry e : favorites) {
            if (name.equals(e.getName())) {
                return true;
            }
        }
        return false;
    }

    public synchronized List<HistoryEntry> getAll() {
        return new ArrayList<>(favorites);
    }

    private void load() {
        try {
            if (!favoriteFile.exists()) return;
            String json = new String(Files.readAllBytes(favoriteFile.toPath()), StandardCharsets.UTF_8);
            Type type = new TypeToken<List<HistoryEntry>>() {}.getType();
            List<HistoryEntry> list = gson.fromJson(json, type);
            if (list != null) {
                favorites.addAll(list);
            }
        } catch (Exception e) {
            log.warn("Failed to load favorites: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            File parent = favoriteFile.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                log.warn("Failed to create favorite dir: {}", parent);
                return;
            }
            Files.write(favoriteFile.toPath(), gson.toJson(favorites).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Failed to save favorites: {}", e.getMessage());
        }
    }
}
