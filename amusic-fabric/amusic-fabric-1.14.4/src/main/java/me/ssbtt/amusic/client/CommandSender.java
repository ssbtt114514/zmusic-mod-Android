package me.ssbtt.amusic.client;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.config.AMusicConfig;
import me.ssbtt.amusic.history.HistoryEntry;
import me.ssbtt.amusic.history.HistoryManager;
import me.ssbtt.amusic.playlist.PlaylistPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

/**
 * 客户端命令发送工具（Fabric 版本）。
 *
 * <p>负责发送上一首/下一首/暂停命令，命令格式：</p>
 * <ul>
 *   <li>公开点歌：{@code /zm music 平台 歌名}</li>
 *   <li>非公开：{@code /zm search 平台 歌名}</li>
 *   <li>暂停：{@code /zm stop}</li>
 * </ul>
 *
 * <p>实现方式：通过 {@code player.networkHandler.sendCommand} 直接发送命令到服务器，
 * 模拟玩家在聊天框输入命令并按下回车键的效果，无需打开聊天框界面。</p>
 *
 * @author ssbtt
 * @since 2026-07-28
 */
@Log4j2
public final class CommandSender {

    private CommandSender() {
    }

    /**
     * 发送上一首命令。
     */
    public static void sendPrevious() {
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pp != null && pp.isActive()) {
            log.info("Playlist active, using playlist previous");
            pp.previous();
            return;
        }
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
     */
    public static void sendNext() {
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pp != null && pp.isActive()) {
            log.info("Playlist active, using playlist next");
            pp.next();
            return;
        }
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
     * @param platform 平台
     * @param songName 歌名
     */
    public static void sendPlayCommand(String platform, String songName) {
        sendPlayCommand(platform, songName, true);
    }

    /**
     * 发送点歌命令。
     *
     * @param platform     平台
     * @param songName     歌名
     * @param modInitiated 是否由模组发起（true：开启自动点击播放；
     *                     false：玩家手动触发，不开启自动点击）
     */
    public static void sendPlayCommand(String platform, String songName, boolean modInitiated) {
        AMusicConfig config = AMusic.getConfig();
        String plat = (platform == null || platform.isEmpty()) ? "163" : platform;
        String platArg = normalizePlatform(plat);
        String cmd;
        if (config != null && config.isPublicPlay()) {
            cmd = "zm music " + platArg + " " + songName;
        } else {
            cmd = "zm search " + platArg + " " + songName;
        }
        sendCommand(cmd);
        ChatLinkListener.onPlayCommandSent(modInitiated);
    }

    /**
     * 平台名称归一化（服务端接受 163/bilibili/kuwo/netease/qq）。
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
     * @param commandWithoutSlash 命令字符串（不带 / 前缀）
     */
    private static void sendCommand(String commandWithoutSlash) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            log.warn("Player is null, cannot send command: /{}", commandWithoutSlash);
            return;
        }
        ClientPlayNetworkHandler connection = mc.player.networkHandler;
        if (connection == null) {
            log.warn("Connection is null, cannot send command: /{}", commandWithoutSlash);
            return;
        }
        connection.sendCommand(commandWithoutSlash);
        log.info("Sent command (simulated Enter): /{}", commandWithoutSlash);
    }
}
