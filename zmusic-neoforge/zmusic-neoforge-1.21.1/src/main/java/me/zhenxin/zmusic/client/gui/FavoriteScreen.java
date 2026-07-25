package me.zhenxin.zmusic.client.gui;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.client.CommandSender;
import me.zhenxin.zmusic.favorite.FavoriteManager;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 收藏记录界面。
 *
 * <p>展示收藏歌曲列表，支持批量选择后批量下载或删除。
 * 单击歌曲名播放；左键勾选/取消勾选；顶部「全选」「批量下载」「批量删除」按钮。</p>
 *
 * @author ssbtt
 * @since 2026-07-25
 */
@Log4j2
public class FavoriteScreen extends Screen {

    private FavoriteList list;
    /** 选中的歌曲名集合 */
    private final Set<String> selected = new HashSet<>();

    public FavoriteScreen() {
        super(Component.translatable("zmusic.favorite.title"));
    }

    @Override
    protected void init() {
        list = new FavoriteList(Minecraft.getInstance());
        addWidget(list);

        int y = 28;
        int btnH = 18;
        // 全选 / 取消全选
        addRenderableWidget(Button.builder(Component.literal("全选/取消"), b -> {
            toggleSelectAll();
            list.refresh();
        }).bounds(10, y, 100, btnH).build());

        // 批量下载
        addRenderableWidget(Button.builder(Component.literal("批量下载"), b -> {
            for (String name : selected) {
                HistoryEntry e = findByName(name);
                if (e != null) downloadEntry(e);
            }
        }).bounds(120, y, 100, btnH).build());

        // 批量删除
        addRenderableWidget(Button.builder(Component.literal("批量删除"), b -> {
            FavoriteManager fm = ZMusic.getFavoriteManager();
            if (fm == null) return;
            for (String name : selected) {
                fm.remove(name);
            }
            selected.clear();
            list.refresh();
        }).bounds(230, y, 100, btnH).build());

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

    private void toggleSelectAll() {
        FavoriteManager fm = ZMusic.getFavoriteManager();
        if (fm == null) return;
        List<HistoryEntry> all = fm.getAll();
        if (selected.size() == all.size()) {
            selected.clear();
        } else {
            selected.clear();
            for (HistoryEntry e : all) {
                selected.add(e.getName());
            }
        }
    }

    private HistoryEntry findByName(String name) {
        FavoriteManager fm = ZMusic.getFavoriteManager();
        if (fm == null) return null;
        for (HistoryEntry e : fm.getAll()) {
            if (name.equals(e.getName())) return e;
        }
        return null;
    }

    private void playEntry(HistoryEntry entry) {
        // CommandSender 通过 player.connection.sendCommand 直接发送，不再切换 Screen
        CommandSender.sendPlayCommand(entry.getPlatform(), entry.getName());
        onClose();
    }

    private void downloadEntry(HistoryEntry entry) {
        Thread thread = new Thread(() -> {
            try {
                HistoryManager hm = ZMusic.getHistoryManager();
                if (hm == null) return;
                File dir = hm.getDownloadDir();
                if (!dir.exists() && !dir.mkdirs()) return;
                String safeName = HistoryManager.sanitizeFileName(entry.getName());
                File target = new File(dir, safeName + ".mp3");
                URL url = new URL(entry.getUrl());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);
                conn.setRequestProperty("User-Agent", "AMusic/1.1");
                conn.connect();
                if (conn.getResponseCode() != 200) return;
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
     * 收藏列表组件。
     *
     * <p>每行高度 24 像素（包裹内容）。单击歌名区域播放；单击复选框区域勾选/取消。</p>
     */
    private class FavoriteList extends ObjectSelectionList<FavoriteList.Entry> {

        private static final int ROW_HEIGHT = 14;
        private static final int BTN_W = 44;
        private static final int BTN_GAP = 4;

        FavoriteList(Minecraft mc) {
            super(mc, FavoriteScreen.this.width - 20, FavoriteScreen.this.height - 80, 50, FavoriteScreen.this.height - 30);
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
            FavoriteManager fm = ZMusic.getFavoriteManager();
            if (fm == null) return;
            List<HistoryEntry> entries = fm.getAll();
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
                // 单行布局：左侧 [x]/[ ] 复选框 + 歌名 [平台]
                boolean checked = selected.contains(data.getName());
                String check = checked ? "[x] " : "[ ] ";
                String text = check + data.getName();
                if (data.getPlatform() != null && !data.getPlatform().isEmpty()) {
                    text = text + " [" + data.getPlatform() + "]";
                }
                graphics.drawString(mc.font, text, left + 4, top + 3, 0xFFFFFF);

                // 右侧按钮：▶ 播放  ✓ 勾选
                int btnW = BTN_W;
                int gap = BTN_GAP;
                int totalBtnW = btnW * 2 + gap;
                int x2 = left + getRowWidth() - totalBtnW;
                int x1 = x2 - btnW - gap;
                int btnY = top + 3;
                drawTextButton(graphics, mc, "▶ 播放", x1, btnY, btnW, mouseX, mouseY);
                drawTextButton(graphics, mc, "✓ 勾选", x2, btnY, btnW, mouseX, mouseY);
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
                int totalBtnW = btnW * 2 + gap;
                int x2 = left + getRowWidth() - totalBtnW;
                int x1 = x2 - btnW - gap;
                int btnY = top + 3;
                int cbRight = left + 4 + 24;

                int x = (int) mouseX;
                if (mouseY >= btnY && mouseY <= btnY + 10) {
                    if (x >= x1 && x <= x1 + btnW) {
                        playEntry(data);
                        return true;
                    } else if (x >= x2 && x <= x2 + btnW) {
                        toggleSelect();
                        return true;
                    }
                }
                // 左侧复选框区域勾选，其他位置播放
                if (x <= cbRight) {
                    toggleSelect();
                } else {
                    playEntry(data);
                }
                return true;
            }

            private void toggleSelect() {
                if (selected.contains(data.getName())) {
                    selected.remove(data.getName());
                } else {
                    selected.add(data.getName());
                }
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
