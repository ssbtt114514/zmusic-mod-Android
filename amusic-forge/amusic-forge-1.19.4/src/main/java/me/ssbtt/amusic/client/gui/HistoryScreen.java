package me.ssbtt.amusic.client.gui;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.client.CommandSender;
import me.ssbtt.amusic.history.HistoryEntry;
import me.ssbtt.amusic.history.HistoryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import com.mojang.blaze3d.vertex.PoseStack;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 历史记录界面（Forge 1.19.4 版本，使用 PoseStack 渲染）。
 *
 * @author ssbtt
 * @since 2026-07-25
 */
@Log4j2
public class HistoryScreen extends Screen {

    private HistoryList list;

    public HistoryScreen() {
        super(Component.translatable("amusic.history.title"));
    }

    @Override
    protected void init() {
        list = new HistoryList(Minecraft.getInstance());
        addWidget(list);

        addButton(new Button(10, 28, 90, 18, Component.literal("清除历史"), b -> {
            HistoryManager hm = AMusic.getHistoryManager();
            if (hm != null) {
                hm.clear();
            }
            list.refresh();
        }));

        addButton(new Button(this.width / 2 - 100, this.height - 24, 200, 20, Component.translatable("gui.done"), b -> onClose()));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        super.render(poseStack, mouseX, mouseY, partialTick);
        GuiComponent.drawCenteredString(poseStack, this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        list.render(poseStack, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return list.mouseScrolled(mouseX, mouseY, delta);
    }

    private void playEntry(HistoryEntry entry) {
        HistoryManager hm = AMusic.getHistoryManager();
        if (hm != null) {
            hm.setCurrentByName(entry.getName());
        }
        CommandSender.sendPlayCommand(entry.getPlatform(), entry.getName());
        onClose();
    }

    private void favoriteEntry(HistoryEntry entry) {
        Minecraft.getInstance().setScreen(new SelectPlaylistScreen(entry, this));
    }

    private void downloadEntry(HistoryEntry entry) {
        Thread thread = new Thread(() -> {
            try {
                HistoryManager hm = AMusic.getHistoryManager();
                if (hm == null) return;
                File dir = hm.getDownloadDir();
                if (!dir.exists() && !dir.mkdirs()) {
                    log.warn("Failed to create download dir: {}", dir);
                    return;
                }
                String safeName = HistoryManager.sanitizeFileName(entry.getName());
                File target = new File(dir, safeName + ".mp3");
                URL url = new URL(entry.getUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "AMusic/1.1");
                conn.connect();
                if (conn.getResponseCode() != 200) {
                    log.warn("Download failed, HTTP {}", conn.getResponseCode());
                    return;
                }
                try (InputStream in = conn.getInputStream()) {
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                log.info("Downloaded {} to {}", entry.getName(), target);
            } catch (Exception e) {
                log.error("Download failed for {}: {}", entry.getName(), e.getMessage(), e);
            }
        }, "amusic-download");
        thread.setDaemon(true);
        thread.start();
    }

    private class HistoryList extends ObjectSelectionList<HistoryList.Entry> {

        private static final int ROW_HEIGHT = 14;
        private static final int BTN_W = 44;
        private static final int BTN_GAP = 4;

        HistoryList(Minecraft mc) {
            super(mc, HistoryScreen.this.width - 20, HistoryScreen.this.height - 80, 50, HistoryScreen.this.height - 30, ROW_HEIGHT);
            refresh();
        }

        void refresh() {
            clearEntries();
            HistoryManager hm = AMusic.getHistoryManager();
            if (hm == null) return;
            List<HistoryEntry> entries = hm.getHistory();
            for (HistoryEntry e : entries) {
                addEntry(new Entry(e));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {
            private final HistoryEntry data;
            private int lastTop;
            private int lastLeft;

            Entry(HistoryEntry data) {
                this.data = data;
            }

            @Override
            public void render(PoseStack poseStack, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                this.lastTop = top;
                this.lastLeft = left;
                Minecraft mc = Minecraft.getInstance();
                String text = data.getName();
                if (data.getPlatform() != null && !data.getPlatform().isEmpty()) {
                    text = text + " [" + data.getPlatform() + "]";
                }
                mc.font.draw(poseStack, text, left + 4, top + 3, 0xFFFFFF);

                int btnW = BTN_W;
                int gap = BTN_GAP;
                int totalBtnW = btnW * 3 + gap * 2;
                int x3 = left + getRowWidth() - totalBtnW;
                int x2 = x3 - btnW - gap;
                int x1 = x2 - btnW - gap;
                int btnY = top + 3;
                drawTextButton(poseStack, mc, "▶ 播放", x1, btnY, btnW, mouseX, mouseY);
                drawTextButton(poseStack, mc, "↓ 下载", x2, btnY, btnW, mouseX, mouseY);
                drawTextButton(poseStack, mc, "★ 收藏", x3, btnY, btnW, mouseX, mouseY);
            }

            private void drawTextButton(PoseStack poseStack, Minecraft mc, String label, int x, int y, int w, int mouseX, int mouseY) {
                boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 10;
                int color = hover ? 0xFFFF55 : 0xAAAAFF;
                mc.font.draw(poseStack, label, x + 2, y + 1, color);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) return false;
                playClickSound();
                int top = this.lastTop;
                int left = this.lastLeft;
                int btnW = BTN_W;
                int gap = BTN_GAP;
                int totalBtnW = btnW * 3 + gap * 2;
                int x3 = left + getRowWidth() - totalBtnW;
                int x2 = x3 - btnW - gap;
                int x1 = x2 - btnW - gap;
                int btnY = top + 3;

                int x = (int) mouseX;
                if (mouseY >= btnY && mouseY <= btnY + 10) {
                    if (x >= x1 && x <= x1 + btnW) {
                        playEntry(data);
                        return true;
                    } else if (x >= x2 && x <= x2 + btnW) {
                        downloadEntry(data);
                        return true;
                    } else if (x >= x3 && x <= x3 + btnW) {
                        favoriteEntry(data);
                        return true;
                    }
                }
                playEntry(data);
                return true;
            }

            private void playClickSound() {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                }
            }

            @Override
            public Component getNarration() {
                return Component.literal(data.getName());
            }
        }
    }
}
