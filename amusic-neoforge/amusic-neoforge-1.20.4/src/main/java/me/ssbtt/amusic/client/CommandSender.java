package me.ssbtt.amusic.client;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.config.AMusicConfig;
import me.ssbtt.amusic.history.HistoryEntry;
import me.ssbtt.amusic.history.HistoryManager;
import me.ssbtt.amusic.playlist.PlaylistPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * 客户端命令发送工具。
 *
 * <p>负责发送上一首/下一首/暂停命令，命令格式：</p>
 * <ul>
 *   <li>公开点歌：{@code /zm music 平台 歌名}</li>
 *   <li>非公开：{@code /zm 平台 歌名}</li>
 *   <li>暂停：{@code /zm stop}</li>
 * </ul>
 *
 * <p>实现方式：通过 {@code player.connection.sendCommand} 直接发送命令到服务器，
 * 模拟玩家在聊天框输入命令并按下回车键的效果，无需打开聊天框界面。</p>
 *
 * @author ssbtt
 * @since 2026-07-25
 */
@Log4j2
public final class CommandSender {

    private CommandSender() {
    }

    /**
     * 发送上一首命令。
     *
     * <p>优先级：</p>
     * <ol>
     *   <li>歌单模式：若 {@link PlaylistPlayer} 处于活动状态，调用 {@link PlaylistPlayer#previous()}</li>
     *   <li>历史模式：从历史记录中取上一首</li>
     * </ol>
     */
    public static void sendPrevious() {
        // 歌单模式：歌单活动时由 PlaylistPlayer 处理上一首
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pp != null && pp.isActive()) {
            log.info("Playlist active, using playlist previous");
            pp.previous();
            return;
        }
        // 历史模式：从历史记录中取上一首
        HistoryManager hm = AMusic.getHistoryManager();
        if (hm == null) {
            log.warn("History manager not initialized, cannot go previous");
            return;
        }
        HistoryEntry entry = hm.getPrevious();
        if (entry == null) {
            log.info("No previous song in history");
            return;
        }
        sendPlayCommand(entry.getPlatform(), entry.getName());
    }

    /**
     * 发送下一首命令。
     *
     * <p>优先级：</p>
     * <ol>
     *   <li>歌单模式：若 {@link PlaylistPlayer} 处于活动状态，调用 {@link PlaylistPlayer#next()}</li>
     *   <li>历史模式：从历史记录中取下一首</li>
     * </ol>
     */
    public static void sendNext() {
        // 歌单模式：歌单活动时由 PlaylistPlayer 处理下一首
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pp != null && pp.isActive()) {
            log.info("Playlist active, using playlist next");
            pp.next();
            return;
        }
        // 历史模式：从历史记录中取下一首
        HistoryManager hm = AMusic.getHistoryManager();
        if (hm == null) {
            log.warn("History manager not initialized, cannot go next");
            return;
        }
        HistoryEntry entry = hm.getNext();
        if (entry == null) {
            log.info("No next song in history");
            return;
        }
        sendPlayCommand(entry.getPlatform(), entry.getName());
    }

    /**
     * 发送暂停命令（/zm stop）。
     */
    public static void sendPause() {
        sendCommand("zm stop");
    }

    /**
     * 发送点歌命令（默认由模组发起，开启自动点击播放）。
     *
     * <p>命令格式：</p>
     * <ul>
     *   <li>公开点歌：{@code /zm music 平台 歌名}（全服公开）</li>
     *   <li>非公开点歌：{@code /zm search 平台 歌名}（仅自己搜索播放，不公开到全服）</li>
     * </ul>
     *
     * @param platform 平台
     * @param songName 歌名
     */
    public static void sendPlayCommand(String platform, String songName) {
        sendPlayCommand(platform, songName, true);
    }

    /**
     * 发送点歌命令。
     *
     * @param platform 平台
     * @param songName 歌名
     * @param modInitiated 是否由模组发起（true：开启自动点击播放；
     *                     false：玩家手动触发，不开启自动点击）
     */
    public static void sendPlayCommand(String platform, String songName, boolean modInitiated) {
        AMusicConfig config = AMusic.getConfig();
        String plat = (platform == null || platform.isEmpty()) ? "163" : platform;
        // 平台中文别名映射到命令参数
        String platArg = normalizePlatform(plat);
        String cmd;
        if (config != null && config.isPublicPlay()) {
            cmd = "zm music " + platArg + " " + songName;
        } else {
            // 非公开：使用 search 子命令（仅自己播放）
            cmd = "zm search " + platArg + " " + songName;
        }
        sendCommand(cmd);
        // 通知聊天链接监听器：仅模组发起时才开启自动点击窗口
        me.ssbtt.amusic.client.ChatLinkListener.onPlayCommandSent(modInitiated);
    }

    /**
     * 平台名称归一化（服务端接受 163/bilibili/kuwo/netease/qq）。
     *
     * @param platform 原始平台名（可能是中文）
     * @return 命令参数
     */
    private static String normalizePlatform(String platform) {
        if (platform == null) return "163";
        switch (platform.toLowerCase()) {
            case "163":
            case "网易云音乐":
            case "netease":
                return "163";
            case "bilibili":
            case "哔哩哔哩视频":
            case "哔哩哔哩":
                return "bilibili";
            case "kuwo":
            case "酷我音乐":
                return "kuwo";
            case "qq":
            case "qq音乐":
                return "qq";
            default:
                return platform;
        }
    }

    /**
     * 直接发送命令到服务器，模拟玩家在聊天框输入命令并按回车。
     *
     * <p>使用 {@code ClientPacketListener.sendCommand} 直接发送命令，
     * 不需要打开聊天框界面，避免界面切换的副作用。</p>
     *
     * @param commandWithoutSlash 命令字符串（不带 / 前缀）
     */
    private static void sendCommand(String commandWithoutSlash) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            log.warn("Player is null, cannot send command: /{}", commandWithoutSlash);
            return;
        }
        ClientPacketListener connection = mc.player.connection;
        if (connection == null) {
            log.warn("Connection is null, cannot send command: /{}", commandWithoutSlash);
            return;
        }
        // 直接发送命令到服务器，等同于玩家在聊天框输入并按回车
        connection.sendCommand(commandWithoutSlash);
        log.info("Sent command (simulated Enter): /{}", commandWithoutSlash);
    }
}

