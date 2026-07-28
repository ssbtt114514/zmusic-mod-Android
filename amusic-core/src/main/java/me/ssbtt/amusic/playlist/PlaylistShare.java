package me.ssbtt.amusic.playlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.history.HistoryEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 歌单序列化工具。
 *
 * <p>将歌单序列化为 v2 格式的 JSON 字符串，包含以下字段：</p>
 * <pre>{@code
 * {
 *   "format": 2,
 *   "id": "a1b2c3",
 *   "name": "歌单名",
 *   "author": "提供者",
 *   "public": true,
 *   "playOrder": "SEQUENCE",
 *   "songs": [...]
 * }
 * }</pre>
 *
 * <p>通过服务器插件通道转发，不再使用聊天框分片协议。</p>
 *
 * @author ssbtt
 * @since 2026-07-27
 */
@Log4j2
public final class PlaylistShare {

    private static final Gson GSON = new GsonBuilder().create();

    private PlaylistShare() {
    }

    /**
     * 将歌单序列化为 JSON 字符串（v2 格式）。
     *
     * @param playlist 歌单
     * @return JSON 字符串
     */
    public static String encode(Playlist playlist) {
        if (playlist == null) return "";
        Map<String, Object> map = new HashMap<>();
        map.put("format", 2);
        map.put("id", playlist.getId() != null ? playlist.getId() : Playlist.generateId());
        map.put("name", playlist.getName());
        map.put("author", playlist.getAuthor());
        map.put("public", playlist.isPublic());
        map.put("playOrder", playlist.getPlayOrder() != null ? playlist.getPlayOrder().name() : PlayOrder.SEQUENCE.name());
        map.put("songs", playlist.getSongs() != null ? playlist.getSongs() : new ArrayList<>());
        return GSON.toJson(map);
    }

    /**
     * 从 JSON 字符串反序列化歌单。
     *
     * <p>支持 v1（无 format 字段）和 v2 格式，自动转换。</p>
     *
     * @param json JSON 字符串
     * @return 歌单对象，失败返回 null
     */
    public static Playlist decode(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            Map<String, Object> map = GSON.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
            if (map == null) return null;

            Playlist pl = new Playlist();
            // 解析格式版本
            Object formatObj = map.get("format");
            boolean isV2 = formatObj instanceof Number && ((Number) formatObj).intValue() >= 2;

            // 名称
            Object nameObj = map.get("name");
            pl.setName(nameObj instanceof String ? (String) nameObj : "导入的歌单");

            if (isV2) {
                Object idObj = map.get("id");
                if (idObj instanceof String) pl.setId((String) idObj);
                Object authorObj = map.get("author");
                if (authorObj instanceof String) pl.setAuthor((String) authorObj);
                Object publicObj = map.get("public");
                if (publicObj instanceof Boolean) pl.setPublic((Boolean) publicObj);
            }

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
                String songsJson = GSON.toJson(songsObj);
                List<HistoryEntry> songs = GSON.fromJson(songsJson, new TypeToken<List<HistoryEntry>>() {}.getType());
                if (songs != null) pl.setSongs(songs);
            }
            return pl;
        } catch (Exception e) {
            log.warn("Failed to decode playlist JSON: {}", e.getMessage());
            return null;
        }
    }
}
