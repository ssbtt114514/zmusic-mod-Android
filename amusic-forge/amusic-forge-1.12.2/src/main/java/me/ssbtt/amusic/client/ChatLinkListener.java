package me.ssbtt.amusic.client;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.event.ClientEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天链接自动点击播放监听器（Forge 1.19.2 版本）。
 *
 * <p>当玩家发送点歌命令后，服务端可能在聊天框返回包含音乐 URL 的可点击消息。
 * 本监听器在点歌后的一段时间窗口内捕获聊天消息中的 Component，使用
 * {@link Screen#handleComponentClicked(Style)} 完美模拟玩家点击。</p>
 *
 * @author ssbtt
 * @since 2026-07-25
 */
@Log4j2
public class ChatLinkListener {

    /** URL 正则：匹配 http(s):// 开头的链接 */
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);

    /** 点歌后等待聊天链接的时间窗口（毫秒） */
    private static final long WINDOW_MS = 10_000L;

    private static volatile long pendingSince = 0L;
    private static volatile boolean isModInitiated = false;
    private static volatile boolean processing = false;

    /** 单例实例（由 ForgeEvent 注册） */
    public static final ChatLinkListener INSTANCE = new ChatLinkListener();

    private ChatLinkListener() {
    }

    public static void onPlayCommandSent(boolean modInitiated) {
        pendingSince = System.currentTimeMillis();
        isModInitiated = modInitiated;
        processing = false;
        log.info("ChatLinkListener: play command sent (modInitiated={}), listening for chat links for {}ms",
                modInitiated, WINDOW_MS);
    }

    public static void onPlayCommandSent() {
        onPlayCommandSent(true);
    }

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

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!isWaiting() || !isModInitiated) return;
        if (processing) return;

        Component message = event.getMessage();
        if (message == null) return;

        Component clickable = findClickableComponent(message);
        if (clickable != null) {
            log.info("ChatLinkListener: found clickable component: {}", clickable.getString());
            pendingSince = 0L;
            isModInitiated = false;
            processing = true;
            final Component finalClickable = clickable;
            Minecraft mc = Minecraft.getInstance();
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
            Minecraft mc = Minecraft.getInstance();
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

    private void handleClick(Minecraft mc, Component component) {
        Screen screen = mc.screen;
        Style style = component.getStyle();
        if (screen != null) {
            if (style != null && style.getClickEvent() != null) {
                log.info("ChatLinkListener: using screen.handleComponentClicked()");
                screen.handleComponentClicked(style);
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
                        mc.player.connection.sendCommand(cmd);
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

    private Component findClickableComponent(Component component) {
        if (component == null) return null;
        Style style = component.getStyle();
        if (style != null && style.getClickEvent() != null) {
            return component;
        }
        for (Component sibling : component.getSiblings()) {
            Component found = findClickableComponent(sibling);
            if (found != null) return found;
        }
        return null;
    }

    private String extractUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = URL_PATTERN.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }
}
