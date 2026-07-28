package me.ssbtt.amusic.playlist;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 歌单网络客户端。
 *
 * <p>处理服务端发送的歌单相关数据包，提供异步回调机制。</p>
 *
 * <p>数据包格式（服务端→客户端）：</p>
 * <ul>
 *   <li>{@code [PLListResp]<json>} - 歌单列表响应，JSON 为数组 {@code [{"id":"...","name":"...","author":"...","songCount":N},...]}</li>
 *   <li>{@code [PLData]<json>} - 歌单完整数据响应，JSON 为完整歌单</li>
 *   <li>{@code [PLResult]<success|error>:<message>} - 操作结果</li>
 * </ul>
 *
 * <p>数据包格式（客户端→服务端）由 {@link PlaylistNetSender} 发送。</p>
 *
 * @author ssbtt
 * @since 2026-07-27
 */
@Log4j2
public class PlaylistNetClient {

    /** 单例实例 */
    private static final PlaylistNetClient INSTANCE = new PlaylistNetClient();

    /** 歌单列表条目（简化信息） */
    public static class PlaylistInfo {
        public String id;
        public String name;
        public String author;
        public int songCount;
        public boolean isPublic;

        public PlaylistInfo() {}
    }

    /** 歌单列表响应回调 */
    private volatile Consumer<List<PlaylistInfo>> listCallback;
    /** 歌单数据响应回调 */
    private volatile Consumer<Playlist> dataCallback;
    /** 操作结果回调 */
    private volatile Consumer<String> resultCallback;

    private PlaylistNetClient() {}

    public static PlaylistNetClient getInstance() {
        return INSTANCE;
    }

    /**
     * 设置歌单列表响应回调。
     *
     * @param callback 回调函数
     */
    public void setListCallback(Consumer<List<PlaylistInfo>> callback) {
        this.listCallback = callback;
    }

    /**
     * 设置歌单数据响应回调。
     *
     * @param callback 回调函数
     */
    public void setDataCallback(Consumer<Playlist> callback) {
        this.dataCallback = callback;
    }

    /**
     * 设置操作结果回调。
     *
     * @param callback 回调函数
     */
    public void setResultCallback(Consumer<String> callback) {
        this.resultCallback = callback;
    }

    /**
     * 处理服务端数据包。
     *
     * <p>由 {@link me.ssbtt.amusic.event.ClientEvent} 调用。</p>
     *
     * @param message 数据包消息
     */
    @SuppressWarnings("unchecked")
    public void onPacket(String message) {
        if (message == null) return;

        if (message.startsWith("[PLListResp]")) {
            String json = message.substring("[PLListResp]".length());
            handleListResponse(json);
        } else if (message.startsWith("[PLData]")) {
            String json = message.substring("[PLData]".length());
            handleDataResponse(json);
        } else if (message.startsWith("[PLResult]")) {
            String data = message.substring("[PLResult]".length());
            handleResult(data);
        }
    }

    /**
     * 处理歌单列表响应。
     */
    private void handleListResponse(String json) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.List<PlaylistInfo> list = gson.fromJson(json,
                    new com.google.gson.reflect.TypeToken<java.util.List<PlaylistInfo>>() {}.getType());
            if (list == null) list = new ArrayList<>();
            log.info("Received playlist list response: {} playlists", list.size());
            Consumer<List<PlaylistInfo>> cb = listCallback;
            if (cb != null) {
                cb.accept(list);
            }
        } catch (Exception e) {
            log.warn("Failed to parse playlist list response: {}", e.getMessage());
        }
    }

    /**
     * 处理歌单数据响应。
     */
    private void handleDataResponse(String json) {
        Playlist playlist = PlaylistShare.decode(json);
        if (playlist == null) {
            log.warn("Failed to decode playlist data response");
            return;
        }
        log.info("Received playlist data: '{}' ({} songs)", playlist.getName(), playlist.size());
        Consumer<Playlist> cb = dataCallback;
        if (cb != null) {
            cb.accept(playlist);
        }
    }

    /**
     * 处理操作结果。
     */
    private void handleResult(String data) {
        log.info("Playlist operation result: {}", data);
        Consumer<String> cb = resultCallback;
        if (cb != null) {
            cb.accept(data);
        }
    }
}
