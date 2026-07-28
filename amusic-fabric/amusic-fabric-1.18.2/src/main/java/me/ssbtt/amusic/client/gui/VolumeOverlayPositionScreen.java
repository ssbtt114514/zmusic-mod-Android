package me.ssbtt.amusic.client.gui;

// TODO: 1.17-1.19 可能需要将 GuiGraphics 替换为 PoseStack

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.config.AMusicConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 音量条位置调整界面（Fabric 版本）。
 *
 * <p>全屏 GUI，显示一个模拟音量条，玩家可以鼠标拖动到屏幕任意位置。
 * 点击「保存」按钮后，相对坐标（0.0~1.0）写入配置。</p>
 *
 * @author ssbtt
 * @since 2026-07-28
 */
@Log4j2
public class VolumeOverlayPositionScreen extends Screen {

    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 14;

    private boolean dragging;
    private int barX;
    private int barY;

    public VolumeOverlayPositionScreen() {
        super(Text.literal("拖动音量条到目标位置"));
    }

    @Override
    protected void init() {
        AMusicConfig config = AMusic.getConfig();
        if (config != null) {
            int cx = (int) (this.width * config.getVolumeOverlayX());
            int cy = (int) (this.height * config.getVolumeOverlayY());
            barX = cx - BAR_WIDTH / 2;
            barY = cy - BAR_HEIGHT / 2;
        } else {
            barX = (this.width - BAR_WIDTH) / 2;
            barY = this.height - 40;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("重置到默认"), b -> {
            barX = (this.width - BAR_WIDTH) / 2;
            barY = this.height - 40;
        }).dimensions(10, 10, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), b -> save())
                .dimensions(this.width / 2 - 110, this.height - 28, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> onClose())
                .dimensions(this.width / 2 + 10, this.height - 28, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "拖动绿色条到目标位置，点击「保存」", this.width / 2, 26, 0xAAAAFF);

        int barY2 = barY + 2;
        int barFilled = (int) ((BAR_WIDTH - 4) * 0.7);
        context.fill(barX - 4, barY - 4, barX + BAR_WIDTH + 4, barY + BAR_HEIGHT + 4, 0xB0000000);
        context.fill(barX + 2, barY2, barX + BAR_WIDTH - 2, barY2 + BAR_HEIGHT - 4, 0xFF333333);
        context.fill(barX + 2, barY2, barX + 2 + barFilled, barY2 + BAR_HEIGHT - 4, 0xFF55FF55);
        context.drawCenteredTextWithShadow(this.textRenderer, "音量 70% (可拖动)", this.width / 2, barY + BAR_HEIGHT - 12, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= barX - 4 && mouseX <= barX + BAR_WIDTH + 4
                    && mouseY >= barY - 4 && mouseY <= barY + BAR_HEIGHT + 4) {
                dragging = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            barX = (int) mouseX - BAR_WIDTH / 2;
            barY = (int) mouseY - BAR_HEIGHT / 2;
            if (barX < 0) barX = 0;
            if (barX + BAR_WIDTH > this.width) barX = this.width - BAR_WIDTH;
            if (barY < 30) barY = 30;
            if (barY + BAR_HEIGHT > this.height - 35) barY = this.height - 35 - BAR_HEIGHT;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void save() {
        AMusicConfig config = AMusic.getConfig();
        if (config != null) {
            float cx = (barX + BAR_WIDTH / 2.0f) / this.width;
            float cy = (barY + BAR_HEIGHT / 2.0f) / this.height;
            config.setVolumeOverlayX(cx);
            config.setVolumeOverlayY(cy);
            config.save();
            log.info("Volume overlay position saved: ({}, {})", cx, cy);
        }
        onClose();
    }
}
