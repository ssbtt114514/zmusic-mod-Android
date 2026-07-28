package me.ssbtt.amusic.client;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.event.ClientEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天链接自动点击播放监听器（Fabric 版本）。
 *
 * <p>当玩家发送点歌命令后，服务端可能在聊天框返回包含音乐 URL 的可点击消息。
 * 本监听器在点歌后的一段时间窗口内捕获聊天消息中的 Text，使用
 * {@link Screen#handleTextClick(Style)} 完美模拟玩家点击。</p>
 *
 * <p>由于 Fabric 的聊天消息 API 通过 {@code ClientReceiveMessageEvents.GAME} 触发，
 * 本类提供静态 {@link #processMessage(Text)} 方法供 AMusicMod 注册回调时调用。</p>
 *
 * @author ssbtt
 * @since 2026-07-28
 */
@Log4j2
public class ChatLinkListener {

    /** URL 正则：匹配 http(s):// 开头的链接 */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);

    /** 点歌后等待聊天链接的时间窗口（毫秒） */
    private static final long WINDOW_MS = 10_000L;

    /** 上次发送点歌命令的时间戳，0 表示未在等待 */
    private static volatile long pendingSince = 0L;

    /** 是否由模组发起的点歌（而非玩家手动输入命令）。 */
    private static volatile boolean isModInitiated = false;

    /** 是否正在处理一条链接（防止重复点击同一条消息） */
    private static volatile boolean processing = false;

    private ChatLinkListener() {
    }

    /**
     * 标记刚发送了点歌命令，开启聊天链接捕获窗口。
     *
     * @param modInitiated 是否由模组发起（true）或玩家手动输入（false）
     */
    public static void onPlayCommandSent(boolean modInitiated) {
        pendingSince = System.currentTimeMillis();
        isModInitiated = modInitiated;
        processing = false;
        log.info("ChatLinkListener: play command sent (modInitiated={}), listening for chat links for {}ms",
                modInitiated, WINDOW_MS);
    }

    /**
     * 兼容重载：默认由模组发起。
     */
    public static void onPlayCommandSent() {
        onPlayCommandSent(true);
    }

    /**
     * 判断当前是否在捕获窗口内。
     */
    private static boolean isWaiting() {
        if (pendingSince == 0L) return false;
        if (System.currentTimeMillis() - pendingSince > WINDOW_MS) {
            pendingSince = 0L;
            isModInitiated = false;
            log.info("ChatLinkListener: capture window expired without matching link");
            return false;
        }
        return true;
    }

    /**
     * 处理收到的聊天消息，提取并自动点击播放链接。
     *
     * <p>由 AMusicMod 在 {@code ClientReceiveMessageEvents.GAME} 回调中调用。</p>
     *
     * @param message 聊天消息 Text
     */
    public static void processMessage(Text message) {
        if (!isWaiting() || !isModInitiated) return;
        if (processing) return;
        if (message == null) return;

        Text clickable = findClickableComponent(message);
        if (clickable != null) {
            log.info("ChatLinkListener: found clickable component: {}", clickable.getString());
            pendingSince = 0L;
            isModInitiated = false;
            processing = true;
            final Text finalClickable = clickable;
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                try {
                    handleClick(mc, finalClickable);
                } finally {
                    processing = false;
                }
            });
            return;
        }

        String plain = message.getString();
        String url = extractUrl(plain);
        if (url != null) {
            log.info("ChatLinkListener: captured url from plain text: {}", url);
            pendingSince = 0L;
            isModInitiated = false;
            processing = true;
            final String finalUrl = url;
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                try {
                    ClientEvent.onPacket("[Play]" + finalUrl);
                    log.info("ChatLinkListener: auto-play triggered for {}", finalUrl);
                } catch (Throwable t) {
                    log.error("ChatLinkListener: failed to auto-play {}: {}", finalUrl, t.getMessage(), t);
                } finally {
                    processing = false;
                }
            });
        }
    }

    /**
     * 使用 handleTextClick 处理点击事件。
     */
    private static void handleClick(MinecraftClient mc, Text component) {
        Screen screen = mc.currentScreen;
        Style style = component.getStyle();
        if (screen != null) {
            if (style != null && style.getClickEvent() != null) {
                log.info("ChatLinkListener: using screen.handleTextClick()");
                screen.handleTextClick(style);
            }
        } else if (style != null) {
            log.info("ChatLinkListener: no screen, manually handling clickEvent");
            ClickEvent click = style.getClickEvent();
            if (click == null) return;

            switch (click.getAction()) {
                case OPEN_URL:
                    String url = click.getValue();
                    if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                        try {
                            ClientEvent.onPacket("[Play]" + url);
                            log.info("ChatLinkListener: OPEN_URL auto-play: {}", url);
                        } catch (Throwable t) {
                            log.error("ChatLinkListener: OPEN_URL play failed: {}", t.getMessage(), t);
                        }
                    }
                    break;
                case RUN_COMMAND:
                    String cmd = click.getValue();
                    if (cmd != null && mc.player != null) {
                        if (cmd.startsWith("/")) cmd = cmd.substring(1);
                        mc.player.networkHandler.sendCommand(cmd);
                        log.info("ChatLinkListener: RUN_COMMAND: /{}", cmd);
                    }
                    break;
                case SUGGEST_COMMAND:
                    log.info("ChatLinkListener: SUGGEST_COMMAND ignored (no screen): {}", click.getValue());
                    break;
                default:
                    log.info("ChatLinkListener: unhandled click action: {}", click.getAction());
                    break;
            }
        }
    }

    /**
     * 递归搜索聊天组件树，查找第一个带 ClickEvent 的组件。
     */
    private static Text findClickableComponent(Text component) {
        if (component == null) return null;
        Style style = component.getStyle();
        if (style != null && style.getClickEvent() != null) {
            return component;
        }
        for (Text sibling : component.getSiblings()) {
            Text found = findClickableComponent(sibling);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * 从纯文本中提取第一个 HTTP(S) URL。
     */
    private static String extractUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = URL_PATTERN.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }
}
