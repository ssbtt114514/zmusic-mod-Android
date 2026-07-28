package me.ssbtt.amusic.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史记录管理器。
 *
 * <p>持久化结构：</p>
 * <pre>
 * configDir/AMusic/
 *   ├── config.json          (由 AMusicConfig 管理)
 *   ├── name/
 *   │   └── name.json        (历史记录列表)
 *   └── lyric/
 *       └── 歌名.json        (每首歌的累积歌词)
 * </pre>
 *
 * <p>下载目录位于 {@code configDir/../Download}（与 config 同级）。</p>
 *
 * @author ssbtt
 * @since 2026-07-25
 */
@Log4j2
public class HistoryManager {
    private static final int MAX_HISTORY = 100;
    private static final String ROOT_DIR = "AMusic";
    private static final String NAME_DIR = "name";
    private static final String LYRIC_DIR = "lyric";
    private static final String NAME_FILE = "name.json";

    private final File rootDir;
    private final File nameDir;
    private final File lyricDir;
    private final File nameFile;
    private final File downloadDir;
    private final Gson gson;

    /** 内存中的历史记录，最新在前 */
    private final List<HistoryEntry> history = new ArrayList<>();
    /** 当前歌曲累积的歌词行（去重保序） */
    private final Map<String, LinkedHashMap<String, Boolean>> lyricAccumulator = new LinkedHashMap<>();
    /** 当前播放歌曲在历史中的索引 */
    private int currentIndex = -1;

    public HistoryManager(File configDir) {
        this.rootDir = new File(configDir, ROOT_DIR);
        this.nameDir = new File(rootDir, NAME_DIR);
        this.lyricDir = new File(rootDir, LYRIC_DIR);
        this.nameFile = new File(nameDir, NAME_FILE);
        // configDir 通常是 gameDir/config，下载目录放在 gameDir/Download
        this.downloadDir = new File(configDir.getParentFile(), "Download");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    /**
     * 添加一首歌到历史记录。若已存在则更新并移到最前。
     *
     * @param name     歌名
     * @param url      音频 URL
     * @param platform 平台
     */
    public synchronized void addSong(String name, String url, String platform) {
        if (name == null || name.isEmpty()) {
            return;
        }
        // 移除已存在的同名条目
        for (int i = 0; i < history.size(); i++) {
            if (name.equals(history.get(i).getName())) {
                history.remove(i);
                break;
            }
        }
        HistoryEntry entry = new HistoryEntry(name, url, platform, System.currentTimeMillis() / 1000);
        history.add(0, entry);
        // 限制最大数量
        while (history.size() > MAX_HISTORY) {
            history.remove(history.size() - 1);
        }
        currentIndex = 0;
        save();
    }

    /**
     * 累积一行歌词。
     *
     * @param songName 歌名
     * @param line     歌词行（已去除颜色码）
     */
    public synchronized void appendLyric(String songName, String line) {
        if (songName == null || songName.isEmpty() || line == null || line.isEmpty()) {
            return;
        }
        LinkedHashMap<String, Boolean> lines = lyricAccumulator.get(songName);
        if (lines == null) {
            lines = new LinkedHashMap<>();
            lyricAccumulator.put(songName, lines);
        }
        lines.put(line, Boolean.TRUE);
    }

    /**
     * 保存某首歌的累积歌词到文件。
     *
     * @param songName 歌名
     */
    public synchronized void saveLyrics(String songName) {
        if (songName == null || songName.isEmpty()) {
            return;
        }
        LinkedHashMap<String, Boolean> lines = lyricAccumulator.get(songName);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        try {
            if (!lyricDir.exists() && !lyricDir.mkdirs()) {
                log.warn("Failed to create lyric dir: {}", lyricDir);
                return;
            }
            File file = new File(lyricDir, sanitizeFileName(songName) + ".json");
            List<String> list = new ArrayList<>(lines.keySet());
            Files.write(file.toPath(), gson.toJson(list).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Failed to save lyrics for {}: {}", songName, e.getMessage());
        }
    }

    /**
     * 加载某首歌的歌词文件。
     *
     * @param songName 歌名
     * @return 歌词行列表，无则空
     */
    public synchronized List<String> loadLyrics(String songName) {
        if (songName == null || songName.isEmpty()) {
            return new ArrayList<>();
        }
        File file = new File(lyricDir, sanitizeFileName(songName) + ".json");
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Type type = new TypeToken<List<String>>() {}.getType();
            List<String> list = gson.fromJson(json, type);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            log.warn("Failed to load lyrics for {}: {}", songName, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取上一首（历史中当前索引的下一个，即更早的）。
     *
     * @return 上一首条目，无则 null
     */
    public synchronized HistoryEntry getPrevious() {
        if (currentIndex < 0 || currentIndex + 1 >= history.size()) {
            return null;
        }
        currentIndex++;
        return history.get(currentIndex);
    }

    /**
     * 获取下一首（历史中当前索引的上一个，即更新的）。
     *
     * @return 下一首条目，无则 null
     */
    public synchronized HistoryEntry getNext() {
        if (currentIndex <= 0) {
            return null;
        }
        currentIndex--;
        return history.get(currentIndex);
    }

    /**
     * 设置当前歌曲索引（用于点击历史列表播放）。
     *
     * @param name 歌名
     */
    public synchronized void setCurrentByName(String name) {
        for (int i = 0; i < history.size(); i++) {
            if (name.equals(history.get(i).getName())) {
                currentIndex = i;
                return;
            }
        }
        currentIndex = -1;
    }

    public synchronized List<HistoryEntry> getHistory() {
        return new ArrayList<>(history);
    }

    public synchronized void removeSong(String name) {
        for (int i = 0; i < history.size(); i++) {
            if (name.equals(history.get(i).getName())) {
                history.remove(i);
                if (i < currentIndex) {
                    currentIndex--;
                }
                break;
            }
        }
        save();
    }

    public synchronized void clear() {
        history.clear();
        currentIndex = -1;
        save();
    }

    public File getDownloadDir() {
        return downloadDir;
    }

    private void load() {
        try {
            if (!nameFile.exists()) {
                return;
            }
            String json = new String(Files.readAllBytes(nameFile.toPath()), StandardCharsets.UTF_8);
            Type type = new TypeToken<List<HistoryEntry>>() {}.getType();
            List<HistoryEntry> list = gson.fromJson(json, type);
            if (list != null) {
                history.addAll(list);
            }
        } catch (Exception e) {
            log.warn("Failed to load history: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            if (!nameDir.exists() && !nameDir.mkdirs()) {
                log.warn("Failed to create name dir: {}", nameDir);
                return;
            }
            Files.write(nameFile.toPath(), gson.toJson(history).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Failed to save history: {}", e.getMessage());
        }
    }

    /**
     * 清理文件名中的非法字符。
     *
     * @param name 原始名
     * @return 安全文件名
     */
    public static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
