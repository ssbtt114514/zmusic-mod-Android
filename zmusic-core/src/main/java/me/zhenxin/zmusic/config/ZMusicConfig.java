package me.zhenxin.zmusic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * ZMusic Android 客户端配置。
 *
 * <p>持久化到 {@code config/AMusic/config.json}。</p>
 *
 * @author ssbtt
 * @since 2026-07-25
 */
@Log4j2
public class ZMusicConfig {
    private static final String ROOT_DIR = "AMusic";
    private static final String CONFIG_FILE = "config.json";

    private final File configFile;
    private final Gson gson;
    private ConfigData data;

    public ZMusicConfig(File configDir) {
        File rootDir = new File(configDir, ROOT_DIR);
        this.configFile = new File(rootDir, CONFIG_FILE);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    public float getVolume() { return data.volume; }
    public void setVolume(float volume) { data.volume = clampVolume(volume); }

    public boolean isMuted() { return data.muted; }
    public void setMuted(boolean muted) { data.muted = muted; }

    public boolean isPublicPlay() { return data.publicPlay; }
    public void setPublicPlay(boolean publicPlay) { data.publicPlay = publicPlay; }

    public String getPlatform() { return data.platform; }
    public void setPlatform(String platform) { data.platform = platform; }

    public boolean isShowLyricTranslation() { return data.showLyricTranslation; }
    public void setShowLyricTranslation(boolean show) { data.showLyricTranslation = show; }

    /**
     * 音量条显示位置（相对坐标 0.0~1.0）。
     *
     * <p>x 为水平相对位置（0.0=最左，1.0=最右），
     * y 为垂直相对位置（0.0=最上，1.0=最下）。
     * 默认 (0.5, 0.85) 约为状态栏上方。</p>
     *
     * @return x 相对坐标
     */
    public float getVolumeOverlayX() { return data.volumeOverlayX; }
    public void setVolumeOverlayX(float x) {
        if (x < 0) x = 0;
        if (x > 1) x = 1;
        data.volumeOverlayX = x;
    }

    public float getVolumeOverlayY() { return data.volumeOverlayY; }
    public void setVolumeOverlayY(float y) {
        if (y < 0) y = 0;
        if (y > 1) y = 1;
        data.volumeOverlayY = y;
    }

    public void load() {
        try {
            if (configFile.exists()) {
                String json = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
                ConfigData loaded = gson.fromJson(json, ConfigData.class);
                data = loaded != null ? loaded : new ConfigData();
            } else {
                data = new ConfigData();
            }
        } catch (Exception e) {
            log.warn("Failed to load ZMusic config, using defaults: {}", e.getMessage());
            data = new ConfigData();
        }
    }

    public void save() {
        try {
            File parent = configFile.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                log.warn("Failed to create config dir: {}", parent);
                return;
            }
            Files.write(configFile.toPath(), gson.toJson(data).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Failed to save ZMusic config: {}", e.getMessage());
        }
    }

    private static float clampVolume(float v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    /**
     * 配置数据。
     */
    private static class ConfigData {
        /** 音量 0~1 */
        float volume = 1.0f;
        /** 是否静音 */
        boolean muted = false;
        /** 是否公开点歌（/zm 后是否加 music） */
        boolean publicPlay = true;
        /** 音乐平台：163 / bilibili / kuwo / netease / qq */
        String platform = "163";
        /** 是否显示歌词翻译 */
        boolean showLyricTranslation = true;
        /** 音量条水平相对位置 0.0~1.0 */
        float volumeOverlayX = 0.5f;
        /** 音量条垂直相对位置 0.0~1.0 */
        float volumeOverlayY = 0.85f;
    }
}
