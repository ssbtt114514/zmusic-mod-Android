package me.ssbtt.amusic.client;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.event.ClientEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天链接自动点击播放监听器。
 *
 * <p>当玩家发送点歌命令后，服务端可能在聊天框返回包含音乐 URL 的可点击消息。
 * 本监听器在点歌后的一段时间窗口内捕获聊天消息中的 Component，使用
 * {@link Screen#handleComponentClicked(Component)} 完美模拟玩家点击。</p>
 *
 * <p>核心流程：</p>
 * <ol>
 *   <li>获取聊天组件 Component</li>
 *   <li>检查 {@code component.getStyle().getClickEvent()} 是否为空</li>
 *   <li>触发事件：调用 {@code minecraft.screen.handleComponentClicked(component)}，
 *       自动处理 OPEN_URL、RUN_COMMAND、SUGGEST_COMMAND 等所有动作</li>
 * </ol>
 *
 * <p>当无 Screen 打开时（游戏内），回退到直接处理 clickEvent：
 * OPEN_URL 作为音乐 URL 走 {@link ClientEvent#onPacket(String)} 播放。</p>
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

    /** 上次发送点歌命令的时间戳，0 表示未在等待 */
    private static volatile long pendingSince = 0L;

    /** 是否由模组发起的点歌（而非玩家手动输入命令）。
     *  仅模组发起时才开启自动点击；玩家手动输入命令不自动点击。 */
    private static volatile boolean isModInitiated = false;

    /** 是否正在处理一条链接（防止重复点击同一条消息） */
    private static volatile boolean processing = false;

    /** 单例实例（由 NeoForgeEvent 注册） */
    public static final ChatLinkListener INSTANCE = new ChatLinkListener();

    private ChatLinkListener() {
    }

    /**
     * 标记刚发送了点歌命令，开启聊天链接捕获窗口。
     *
     * <p>仅当由模组发起（HUD 按钮、歌单、历史记录等）时才开启自动点击；
     * 玩家手动在聊天框输入命令点歌时不应调用此方法。</p>
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
     * <p>仅当由模组发起的点歌（{@code isModInitiated=true}）时才处理，
     * 玩家手动输入命令点歌不触发自动点击，避免干扰玩家自主操作。</p>
     */
    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        // 仅在捕获窗口内且由模组发起时处理
        if (!isWaiting() || !isModInitiated) return;
        // 防止重复处理同一条链接
        if (processing) return;

        Component message = event.getMessage();
        if (message == null) return;

        // 查找包含 clickEvent 的子组件
        Component clickable = findClickableComponent(message);
        if (clickable != null) {
            log.info("ChatLinkListener: found clickable component: {}", clickable.getString());
            // 消费窗口并标记处理中
            pendingSince = 0L;
            isModInitiated = false;
            processing = true;
            // 在主线程触发点击
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

        // 回退：从纯文本中提取 URL
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

    /**
     * 使用 handleComponentClicked 处理点击事件。
     *
     * <p>优先使用 {@link Screen#handleComponentClicked(Component)} 完美模拟玩家点击，
     * 自动处理 OPEN_URL、RUN_COMMAND、SUGGEST_COMMAND 等所有动作。
     * 当无 Screen 打开时，回退到直接处理 clickEvent。</p>
     */
    private void handleClick(Minecraft mc, Component component) {
        Screen screen = mc.gui.screen;
        Style style = component.getStyle();
        if (screen != null) {
            // 有 Screen 打开：使用 handleComponentClicked 完美复刻玩家点击
            // 注意：1.21.1 中 handleComponentClicked 接受 Style 参数
            if (style != null && style.getClickEvent() != null) {
                log.info("ChatLinkListener: using screen.handleComponentClicked()");
                screen.handleComponentClicked(style);
            }
        } else if (style != null) {
            // 无 Screen：手动处理 clickEvent
            log.info("ChatLinkListener: no screen, manually handling clickEvent");
            ClickEvent click = style.getClickEvent();
            if (click == null) return;

            switch (click.getAction()) {
                case OPEN_URL:
                    // URL 作为音乐链接直接播放
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
                    // 模拟发送命令
                    String cmd = click.getValue();
                    if (cmd != null && mc.player != null) {
                        if (cmd.startsWith("/")) cmd = cmd.substring(1);
                        mc.player.connection.sendCommand(cmd);
                        log.info("ChatLinkListener: RUN_COMMAND: /{}", cmd);
                    }
                    break;
                case SUGGEST_COMMAND:
                    // 填充聊天框（需打开聊天界面）
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

    /**
     * 从纯文本中提取第一个 HTTP(S) URL。
     */
    private String extractUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = URL_PATTERN.matcher(text);
        if (m.find()) {
            return m.group();
        }
        return null;
    }
}
