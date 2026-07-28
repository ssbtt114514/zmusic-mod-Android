// TODO: 1.12.2 GUI系统完全不同，需要使用 GuiScreen/GuiButton 重写
package me.ssbtt.amusic.client.gui;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.config.AMusicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 音量调节 overlay（Forge 1.19.2 版本，使用 PoseStack 渲染）。
 *
 * <p>调节音量时在玩家状态栏上方立即显示音量条，2 秒后消失，无淡出。</p>
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

    public void show() {
        AMusicConfig config = AMusic.getConfig();
        if (config != null) {
            displayedVolume = config.getVolume();
            displayedMuted = config.isMuted();
        }
        showTime = System.currentTimeMillis();
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        render(event.getPoseStack());
    }

    public void render(com.mojang.blaze3d.vertex.PoseStack poseStack) {
        if (showTime == 0) return;
        long now = System.currentTimeMillis();
        long elapsed = now - showTime;
        if (elapsed > DISPLAY_MS) {
            showTime = 0;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

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

        GuiComponent.fill(poseStack, x - 4, y - 4, x + barWidth + 4, y + barHeight + 4, 0xB0000000);

        int barY = y + 2;
        int barFilled = (int) ((barWidth - 4) * displayedVolume);
        GuiComponent.fill(poseStack, x + 2, barY, x + barWidth - 2, barY + barHeight - 4, 0xFF333333);
        if (displayedMuted) {
            GuiComponent.fill(poseStack, x + 2, barY, x + 2 + barFilled, barY + barHeight - 4, 0xFFFF5555);
        } else {
            GuiComponent.fill(poseStack, x + 2, barY, x + 2 + barFilled, barY + barHeight - 4, 0xFF55FF55);
        }

        int pct = (int) (displayedVolume * 100);
        String text = displayedMuted ? "已静音" : "音量 " + pct + "%";
        GuiComponent.drawCenteredString(poseStack, mc.font, text, screenWidth / 2, y + barHeight - 12, 0xFFFFFFFF);
    }
}
