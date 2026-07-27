package me.zhenxin.zmusic.client.gui;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.client.CommandSender;
import me.zhenxin.zmusic.history.HistoryEntry;
import me.zhenxin.zmusic.history.HistoryManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * 历史记录界面。
 *
 * <p>每行展示歌名/平台，并提供「播放」「下载」「收藏」三个按钮。
 * 顶部提供「清除历史」按钮。所有按钮使用原版 {@link Button}，自带按压反馈音。</p>
 *
 * @author ssbtt
 * @since 2026-07-25
 */
@Log4j2
public class HistoryScreen extends Screen {

    private HistoryList list;

    public HistoryScreen() {
        super(Component.translatable("zmusic.history.title"));
    }

    @Override
    protected void init() {
        list = new HistoryList(Minecraft.getInstance());
        addWidget(list);

        // 顶部「清除历史」按钮
        addRenderableWidget(Button.builder(Component.literal("清除历史"), b -> {
            HistoryManager hm = ZMusic.getHistoryManager();
            if (hm != null) {
                hm.clear();
            }
            list.refresh();
        }).bounds(10, 28, 90, 18).build());

        // 底部「完成」按钮
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 24, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        list.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return list.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * 播放选中的歌曲。
     *
     * <p>CommandSender 通过 player.connection.sendCommand 直接发送命令，
     * 不再切换 Screen，因此需要手动调用 onClose 关闭当前界面。</p>
     *
     * @param entry 历史条目
     */
    private void playEntry(HistoryEntry entry) {
        HistoryManager hm = ZMusic.getHistoryManager();
        if (hm != null) {
            hm.setCurrentByName(entry.getName());
        }
        CommandSender.sendPlayCommand(entry.getPlatform(), entry.getName());
        onClose();
    }

    /**
     * 打开歌单选择界面，将歌曲添加到玩家选择的歌单。
     *
     * @param entry 历史条目
     */
    private void favoriteEntry(HistoryEntry entry) {
        Minecraft.getInstance().setScreen(new SelectPlaylistScreen(entry, this));
    }

    /**
     * 下载选中的歌曲到 Download 文件夹。
     *
     * @param entry 历史条目
     */
    private void downloadEntry(HistoryEntry entry) {
        Thread thread = new Thread(() -> {
            try {
                HistoryManager hm = ZMusic.getHistoryManager();
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

    /**
     * 历史记录列表组件。
     *
     * <p>单行紧凑布局：左侧歌名 [平台]，右侧三个文字按钮。条目高度 14 像素。</p>
     */
    private class HistoryList extends ObjectSelectionList<HistoryList.Entry> {

        private static final int ROW_HEIGHT = 14;
        private static final int BTN_W = 44;
        private static final int BTN_GAP = 4;

        HistoryList(Minecraft mc) {
            super(mc, HistoryScreen.this.width - 20, HistoryScreen.this.height - 80, 50, HistoryScreen.this.height - 30);
            // 1.21.1 中 itemHeight 为 protected 字段，直接赋值以紧凑显示
            try {
                java.lang.reflect.Field f = net.minecraft.client.gui.components.AbstractSelectionList.class
                        .getDeclaredField("itemHeight");
                f.setAccessible(true);
                f.setInt(this, ROW_HEIGHT);
            } catch (Exception e) {
                log.warn("Failed to set itemHeight, using default", e);
            }
            refresh();
        }

        void refresh() {
            clearEntries();
            HistoryManager hm = ZMusic.getHistoryManager();
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
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                this.lastTop = top;
                this.lastLeft = left;
                Minecraft mc = Minecraft.getInstance();
                // 单行布局：左侧歌名 [平台]
                String text = data.getName();
                if (data.getPlatform() != null && !data.getPlatform().isEmpty()) {
                    text = text + " [" + data.getPlatform() + "]";
                }
                graphics.drawString(mc.font, text, left + 4, top + 3, 0xFFFFFF);

                // 右侧三个按钮：▶ 播放  ↓ 下载  ★ 收藏
                int btnW = BTN_W;
                int gap = BTN_GAP;
                int totalBtnW = btnW * 3 + gap * 2;
                int x3 = left + getRowWidth() - totalBtnW;
                int x2 = x3 - btnW - gap;
                int x1 = x2 - btnW - gap;
                int btnY = top + 3;
                drawTextButton(graphics, mc, "▶ 播放", x1, btnY, btnW, mouseX, mouseY);
                drawTextButton(graphics, mc, "↓ 下载", x2, btnY, btnW, mouseX, mouseY);
                drawTextButton(graphics, mc, "★ 收藏", x3, btnY, btnW, mouseX, mouseY);
            }

            private void drawTextButton(GuiGraphics graphics, Minecraft mc, String label, int x, int y, int w, int mouseX, int mouseY) {
                boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 10;
                int color = hover ? 0xFFFF55 : 0xAAAAFF;
                graphics.drawString(mc.font, label, x + 2, y + 1, color);
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
                // 点击歌名区域默认播放
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
