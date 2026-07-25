package me.zhenxin.zmusic.playback;

import lombok.extern.log4j.Log4j2;

/**
 * 服务端推送的 {@code [Info]} / {@code [Lyric]} 包解析器。
 *
 * <p>{@code [Info]} 多行格式（来自服务端 LyricSender.updateHudTime）：</p>
 * <pre>
 * 歌名: xxx
 * 歌手: yyy
 * 平台: zzz
 * 来源: src
 * 进度: 00:12/03:45 ■■■□□□
 * 下一首: xxx (可选)
 * </pre>
 *
 * @author 真心
 * @since 2026-07-25
 */
@Log4j2
public final class InfoParser {

    private InfoParser() {
    }

    /**
     * 解析 [Info] 文本并更新 NowPlaying。
     *
     * @param text      [Info] 后的文本
     * @param nowPlaying 当前播放状态
     * @return true 表示检测到新歌曲（歌名变化）
     */
    public static boolean parseInfo(String text, NowPlaying nowPlaying) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        boolean isNewSong = false;
        String[] lines = text.split("\n");
        for (String raw : lines) {
            String line = stripColor(raw);
            int idx = indexOfColon(line);
            if (idx < 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                continue;
            }
            switch (key) {
                case "歌名":
                    if (nowPlaying.isNewSong(value)) {
                        isNewSong = true;
                    }
                    nowPlaying.setName(value);
                    break;
                case "歌手":
                    nowPlaying.setSinger(value);
                    break;
                case "平台":
                    nowPlaying.setPlatform(value);
                    break;
                case "来源":
                    nowPlaying.setSource(value);
                    break;
                case "进度":
                    parseProgress(value, nowPlaying);
                    break;
                default:
                    // 忽略"下一首"等
            }
        }
        return isNewSong;
    }

    /**
     * 解析 [Lyric] 文本，返回去除颜色码的第一行歌词。
     *
     * @param text [Lyric] 后的文本
     * @return 当前歌词行
     */
    public static String parseLyric(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String cleaned = stripColor(text);
        int nl = cleaned.indexOf('\n');
        if (nl > 0) {
            return cleaned.substring(0, nl).trim();
        }
        return cleaned.trim();
    }

    /**
     * 解析进度文本 "mm:ss/mm:ss" 或 "hh:mm:ss/hh:mm:ss"。
     *
     * @param value 进度文本
     * @param nowPlaying 当前播放状态
     */
    private static void parseProgress(String value, NowPlaying nowPlaying) {
        // 去掉进度条字符（■□ 等非数字部分）
        String progressPart = value;
        int barIdx = indexOfBar(value);
        if (barIdx > 0) {
            progressPart = value.substring(0, barIdx).trim();
        }
        int slash = progressPart.indexOf('/');
        if (slash < 0) {
            return;
        }
        String posStr = progressPart.substring(0, slash).trim();
        String durStr = progressPart.substring(slash + 1).trim();
        try {
            nowPlaying.setPositionSec(parseTimeToSec(posStr));
            nowPlaying.setDurationSec(parseTimeToSec(durStr));
        } catch (Exception e) {
            log.debug("Failed to parse progress: {}", value);
        }
    }

    private static long parseTimeToSec(String time) {
        if (time == null || time.isEmpty()) {
            return 0;
        }
        String[] parts = time.split(":");
        long total = 0;
        for (String p : parts) {
            total = total * 60 + Long.parseLong(p.trim());
        }
        return total;
    }

    /**
     * 去除 Minecraft 颜色码（§x）。
     *
     * @param s 原始文本
     * @return 纯文本
     */
    public static String stripColor(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                i++; // 跳过颜色码字符
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static int indexOfColon(String line) {
        // 冒号可能是全角或半角
        int idx = line.indexOf(':');
        int idx2 = line.indexOf('：');
        if (idx < 0) return idx2;
        if (idx2 < 0) return idx;
        return Math.min(idx, idx2);
    }

    private static int indexOfBar(String value) {
        // 进度条以 ■ 或 □ 开头
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '■' || c == '□') {
                // 回退到前面的空格
                int j = i;
                while (j > 0 && value.charAt(j - 1) == ' ') {
                    j--;
                }
                return j;
            }
        }
        return -1;
    }
}
