package me.zhenxin.zmusic.client;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.config.ZMusicConfig;
import me.zhenxin.zmusic.history.HistoryEntry;
import me.zhenxin.zmusic.history.HistoryManager;
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
     * 发送上一首命令（历史记录中更早的一首）。
     */
    public static void sendPrevious() {
        HistoryManager hm = ZMusic.getHistoryManager();
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
     * 发送下一首命令（历史记录中更新的一首）。
     */
    public static void sendNext() {
        HistoryManager hm = ZMusic.getHistoryManager();
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
     * 发送点歌命令。
     *
     * @param platform 平台
     * @param songName 歌名
     */
    public static void sendPlayCommand(String platform, String songName) {
        ZMusicConfig config = ZMusic.getConfig();
        String plat = (platform == null || platform.isEmpty()) ? "163" : platform;
        // 平台中文别名映射到命令参数
        String platArg = normalizePlatform(plat);
        String cmd;
        if (config != null && config.isPublicPlay()) {
            cmd = "zm music " + platArg + " " + songName;
        } else {
            cmd = "zm " + platArg + " " + songName;
        }
        sendCommand(cmd);
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

