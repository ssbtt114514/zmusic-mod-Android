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
 * 客户端命令发送工具（Forge 版本，与 NeoForge 实现一致）。
 *
 * @author ssbtt
 * @since 2026-07-25
 */
@Log4j2
public final class CommandSender {

    private CommandSender() {
    }

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

    public static void sendPause() {
        sendCommand("zm stop");
    }

    public static void sendPlayCommand(String platform, String songName) {
        sendPlayCommand(platform, songName, true);
    }

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
        connection.sendCommand(commandWithoutSlash);
        log.info("Sent command (simulated Enter): /{}", commandWithoutSlash);
    }
}
