package me.ssbtt.amusic.client.gui;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.config.AMusicConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * 音量调节 overlay（Fabric 版本）。
 *
 * <p>调节音量时在屏幕上立即显示音量条，2 秒后消失，无淡出。
 * 由 {@code HudRenderCallback} 在 HUD 渲染时调用 {@link #render(DrawContext)}。</p>
 *
 * @author ssbtt
 * @since 2026-07-28
 */
@Log4j2
public class VolumeOverlay {

    private static final long DISPLAY_MS = 2000L;

    private long showTime = 0;
    private float displayedVolume = 1.0f;
    private boolean displayedMuted = false;

    /**
     * 触发显示音量条。
     */
    public void show() {
        AMusicConfig config = AMusic.getConfig();
        if (config != null) {
            displayedVolume = config.getVolume();
            displayedMuted = config.isMuted();
        }
        showTime = System.currentTimeMillis();
    }

    /**
     * 渲染音量条（由 HUD 回调每帧调用）。
     */
    public void render(DrawContext context) {
        if (showTime == 0) return;
        long now = System.currentTimeMillis();
        long elapsed = now - showTime;
        if (elapsed > DISPLAY_MS) {
            showTime = 0;
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();

        AMusicConfig config = AMusic.getConfig();
        if (config != null) {
            displayedVolume = config.getVolume();
            displayedMuted = config.isMuted();
        }

        int barWidth = 182;
        int barHeight = 14;
        float relX = config != null ? config.getVolumeOverlayX() : 0.5f;
        float relY = config != null ? config.getVolumeOverlayY() : 0.85f;
        int x = (int) (screenWidth * relX) - barWidth / 2;
        int y = (int) (screenHeight * relY) - barHeight / 2;

        context.fill(x - 4, y - 4, x + barWidth + 4, y + barHeight + 4, 0xB0000000);

        int barY = y + 2;
        int barFilled = (int) ((barWidth - 4) * displayedVolume);
        context.fill(x + 2, barY, x + barWidth - 2, barY + barHeight - 4, 0xFF333333);
        if (displayedMuted) {
            context.fill(x + 2, barY, x + 2 + barFilled, barY + barHeight - 4, 0xFFFF5555);
        } else {
            context.fill(x + 2, barY, x + 2 + barFilled, barY + barHeight - 4, 0xFF55FF55);
        }

        int pct = (int) (displayedVolume * 100);
        String text = displayedMuted ? "已静音" : "音量 " + pct + "%";
        context.drawCenteredTextWithShadow(mc.textRenderer, text, screenWidth / 2, y + barHeight - 12, 0xFFFFFFFF);
    }
}
