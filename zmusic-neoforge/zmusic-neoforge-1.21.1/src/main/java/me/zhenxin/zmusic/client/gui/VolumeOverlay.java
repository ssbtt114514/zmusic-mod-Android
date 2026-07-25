package me.zhenxin.zmusic.client.gui;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.config.ZMusicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 音量调节 overlay。
 *
 * <p>调节音量时在玩家状态栏（hotbar）上方立即显示音量条，2 秒后消失，无淡出。
 * 实时显示当前音量值，调整时立即响应。</p>
 *
 * @author ssbtt
 * @since 2026-07-25
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
        ZMusicConfig config = ZMusic.getConfig();
        if (config != null) {
            displayedVolume = config.getVolume();
            displayedMuted = config.isMuted();
        }
        // 立即响应：重置显示时间戳
        showTime = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        render(event.getGuiGraphics());
    }

    public void render(GuiGraphics graphics) {
        if (showTime == 0) return;
        long now = System.currentTimeMillis();
        long elapsed = now - showTime;
        if (elapsed > DISPLAY_MS) {
            showTime = 0;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        // 实时更新音量值（用户连续调节时立即响应）
        ZMusicConfig config = ZMusic.getConfig();
        if (config != null) {
            displayedVolume = config.getVolume();
            displayedMuted = config.isMuted();
        }

        int barWidth = 182;
        int barHeight = 14;
        // 使用相对坐标计算实际位置（中心点对齐）
        float relX = config != null ? config.getVolumeOverlayX() : 0.5f;
        float relY = config != null ? config.getVolumeOverlayY() : 0.85f;
        int x = (int) (screenWidth * relX) - barWidth / 2;
        int y = (int) (screenHeight * relY) - barHeight / 2;

        // 背景（无淡出，全程不透明）
        graphics.fill(x - 4, y - 4, x + barWidth + 4, y + barHeight + 4, 0xB0000000);

        // 进度条
        int barY = y + 2;
        int barFilled = (int) ((barWidth - 4) * displayedVolume);
        // 进度条背景
        graphics.fill(x + 2, barY, x + barWidth - 2, barY + barHeight - 4, 0xFF333333);
        // 进度条填充
        if (displayedMuted) {
            graphics.fill(x + 2, barY, x + 2 + barFilled, barY + barHeight - 4, 0xFFFF5555);
        } else {
            graphics.fill(x + 2, barY, x + 2 + barFilled, barY + barHeight - 4, 0xFF55FF55);
        }

        // 音量百分比文本
        int pct = (int) (displayedVolume * 100);
        String text = displayedMuted ? "已静音" : "音量 " + pct + "%";
        graphics.drawCenteredString(mc.font, text, screenWidth / 2, y + barHeight - 12, 0xFFFFFFFF);
    }
}
