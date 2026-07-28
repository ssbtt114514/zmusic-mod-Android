package me.ssbtt.amusic.client.gui;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.client.CommandSender;
import me.ssbtt.amusic.history.HistoryEntry;
import me.ssbtt.amusic.playback.NowPlaying;
import me.ssbtt.amusic.playlist.PlayOrder;
import me.ssbtt.amusic.playlist.Playlist;
import me.ssbtt.amusic.playlist.PlaylistManager;
import me.ssbtt.amusic.playlist.PlaylistPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 歌单界面。
 *
 * <p>左侧歌单列表，右侧选中歌单的歌曲列表，支持创建/删除/播放顺序切换/播放。</p>
 *
 * <p>每个条目使用按钮替代鼠标左右键：左键点击按钮执行操作（选择/播放/删除等）。</p>
 *
 * @author 真心
 * @since 2026-07-25
 */
@Log4j2
public class PlaylistScreen extends Screen {

    /** 行高：紧凑布局，刚好包裹两行文本 */
    private static final int ROW_HEIGHT = 22;

    private PlaylistList playlistList;
    private SongList songList;
    private EditBox newPlaylistBox;
    private String selectedPlaylist;

    public PlaylistScreen() {
        super(Component.translatable("amusic.playlist.title"));
    }

    @Override
    protected void init() {
        int halfW = (this.width - 30) / 2;
        playlistList = new PlaylistList(Minecraft.getInstance(), halfW);
        songList = new SongList(Minecraft.getInstance(), halfW);
        addWidget(playlistList);
        addWidget(songList);

        // 新建歌单输入框
        newPlaylistBox = new EditBox(this.font, 10, this.height - 50, halfW - 80, 18, Component.literal("新歌单名"));
        newPlaylistBox.setMaxLength(30);
        addRenderableWidget(newPlaylistBox);

        // 创建按钮
        addRenderableWidget(Button.builder(Component.literal("创建"), b -> createPlaylist())
                .bounds(10 + halfW - 70, this.height - 50, 60, 18).build());

        // 播放全部按钮（从第一首开始播放当前选中歌单）
        addRenderableWidget(Button.builder(Component.literal("播放全部"), b -> playAll())
                .bounds(20 + halfW, this.height - 50, 80, 18).build());

        // 添加当前播放歌曲到歌单
        addRenderableWidget(Button.builder(Component.literal("添加当前歌曲"), b -> addCurrentSong())
                .bounds(20 + halfW + 90, this.height - 50, 90, 18).build());

        // 停止歌单播放按钮
        addRenderableWidget(Button.builder(Component.literal("停止播放"), b -> stopPlaylist())
                .bounds(20 + halfW + 190, this.height - 50, 80, 18).build());

        // 在线歌单按钮
        addRenderableWidget(Button.builder(Component.literal("在线歌单"), b -> openOnlinePlaylists())
                .bounds(20 + halfW + 280, this.height - 50, 80, 18).build());

        // 关闭按钮
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 24, 100, 18).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        int halfW = (this.width - 30) / 2;
        graphics.drawString(this.font, "歌单列表", 10, 20, 0xFFFFFF);
        graphics.drawString(this.font, "歌曲列表", 20 + halfW, 20, 0xFFFFFF);
        playlistList.render(graphics, mouseX, mouseY, partialTick);
        songList.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int halfW = (this.width - 30) / 2;
        if (mouseX < 10 + halfW) {
            return playlistList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return songList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void createPlaylist() {
        String name = newPlaylistBox.getValue().trim();
        if (name.isEmpty()) return;
        PlaylistManager pm = AMusic.getPlaylistManager();
        if (pm != null && pm.createPlaylist(name)) {
            newPlaylistBox.setValue("");
            playlistList.refresh();
        }
    }

    private void selectPlaylist(String name) {
        selectedPlaylist = name;
        songList.refresh();
    }

    private void playSong(HistoryEntry entry) {
        // 从点击的歌曲开始播放整个歌单（按播放顺序自动推进）
        if (selectedPlaylist == null) {
            CommandSender.sendPlayCommand(entry.getPlatform(), entry.getName());
            return;
        }
        PlaylistManager pm = AMusic.getPlaylistManager();
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pm == null || pp == null) {
            CommandSender.sendPlayCommand(entry.getPlatform(), entry.getName());
            return;
        }
        Playlist pl = pm.loadPlaylist(selectedPlaylist);
        if (pl == null) {
            CommandSender.sendPlayCommand(entry.getPlatform(), entry.getName());
            return;
        }
        int idx = -1;
        List<HistoryEntry> songs = pl.getSongs();
        for (int i = 0; i < songs.size(); i++) {
            if (entry.getName() != null && entry.getName().equals(songs.get(i).getName())) {
                idx = i;
                break;
            }
        }
        pp.start(pl, idx);
        onClose();
    }

    private void playAll() {
        if (selectedPlaylist == null) return;
        PlaylistManager pm = AMusic.getPlaylistManager();
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pm == null || pp == null) return;
        Playlist pl = pm.loadPlaylist(selectedPlaylist);
        if (pl == null || pl.size() == 0) return;
        pp.start(pl, 0);
        onClose();
    }

    private void stopPlaylist() {
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pp != null) {
            pp.stop();
        }
    }

    private void addCurrentSong() {
        if (selectedPlaylist == null) return;
        NowPlaying np = AMusic.getNowPlaying();
        if (np == null || np.getName().isEmpty()) return;
        PlaylistManager pm = AMusic.getPlaylistManager();
        if (pm == null) return;
        HistoryEntry entry = new HistoryEntry(np.getName(), np.getUrl(), np.getPlatform(), System.currentTimeMillis());
        pm.addSong(selectedPlaylist, entry);
        songList.refresh();
    }

    /**
     * 打开在线歌单界面。
     */
    private void openOnlinePlaylists() {
        Minecraft.getInstance().setScreen(new OnlinePlaylistScreen());
    }

    private void cyclePlayOrder() {
        if (selectedPlaylist == null) return;
        PlaylistManager pm = AMusic.getPlaylistManager();
        if (pm == null) return;
        Playlist pl = pm.loadPlaylist(selectedPlaylist);
        if (pl == null) return;
        PlayOrder[] orders = PlayOrder.values();
        int idx = pl.getPlayOrder().ordinal();
        pl.setPlayOrder(orders[(idx + 1) % orders.length]);
        pm.savePlaylist(pl);
        // 同步更新活动歌单的播放顺序
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pp != null && pp.isActive() && pp.getActivePlaylist() != null
                && selectedPlaylist.equals(pp.getActivePlaylist().getName())) {
            pp.getActivePlaylist().setPlayOrder(pl.getPlayOrder());
        }
    }

    // ---- 工具方法 ----

    /**
     * 检查鼠标 X 坐标是否在指定文本区域内（用于按钮点击判定）。
     *
     * @param mouseX    鼠标 X
     * @param textStart 文本起始 X
     * @param textWidth 文本宽度
     * @return true 表示鼠标在文本区域内
     */
    private static boolean isInButton(double mouseX, int textStart, int textWidth) {
        return mouseX >= textStart && mouseX <= textStart + textWidth;
    }

    // ---- 歌单列表 ----

    private class PlaylistList extends ObjectSelectionList<PlaylistList.Entry> {
        private final int listWidth;

        PlaylistList(Minecraft mc, int width) {
            super(mc, width, PlaylistScreen.this.height - 80, 30, PlaylistScreen.this.height - 55);
            this.setX(10);
            this.listWidth = width;
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

        @Override
        public int getRowWidth() {
            return this.listWidth - 8;
        }

        void refresh() {
            clearEntries();
            PlaylistManager pm = AMusic.getPlaylistManager();
            if (pm == null) return;
            List<String> names = pm.listPlaylists();
            for (String n : names) {
                addEntry(new Entry(n));
            }
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String name;

            // 按钮区域缓存（每次 render 时更新）
            private int selectBtnX, selectBtnW;
            private int playBtnX, playBtnW;
            private int deleteBtnX, deleteBtnW;

            Entry(String name) {
                this.name = name;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                Font font = Minecraft.getInstance().font;
                String text = name;
                if (name.equals(selectedPlaylist)) {
                    text = "> " + name;
                }
                graphics.drawString(font, text, left + 2, top + 2, 0xFFFFFF);

                // 显示歌曲数量
                PlaylistManager pm = AMusic.getPlaylistManager();
                String countStr = "";
                if (pm != null) {
                    Playlist pl = pm.loadPlaylist(name);
                    if (pl != null) {
                        countStr = "(" + pl.size() + "首)";
                    }
                }
                graphics.drawString(font, countStr, left + 2 + font.width(text) + 4, top + 2, 0xAAAAFF);

                // 按钮区域：[选择] [播放] [删除]
                int btnY = top + 11;
                int x = left + 2;

                String selectLabel = "[选择]";
                selectBtnX = x;
                selectBtnW = font.width(selectLabel);
                boolean hoverSelect = isInButton(mouseX, selectBtnX, selectBtnW) && mouseY >= btnY - 2 && mouseY <= btnY + 10;
                graphics.drawString(font, selectLabel, x, btnY, hoverSelect ? 0xFFFF55 : 0xAAAAFF);
                x += selectBtnW + 4;

                String playLabel = "[播放]";
                playBtnX = x;
                playBtnW = font.width(playLabel);
                boolean hoverPlay = isInButton(mouseX, playBtnX, playBtnW) && mouseY >= btnY - 2 && mouseY <= btnY + 10;
                graphics.drawString(font, playLabel, x, btnY, hoverPlay ? 0xFFFF55 : 0x55FF55);
                x += playBtnW + 4;

                String deleteLabel = "[删除]";
                deleteBtnX = x;
                deleteBtnW = font.width(deleteLabel);
                boolean hoverDelete = isInButton(mouseX, deleteBtnX, deleteBtnW) && mouseY >= btnY - 2 && mouseY <= btnY + 10;
                graphics.drawString(font, deleteLabel, x, btnY, hoverDelete ? 0xFFFF55 : 0xFF5555);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                // 只响应左键
                if (button != 0) return true;

                if (isInButton(mouseX, selectBtnX, selectBtnW)) {
                    selectPlaylist(name);
                } else if (isInButton(mouseX, playBtnX, playBtnW)) {
                    // 播放整个歌单
                    PlaylistManager pm = AMusic.getPlaylistManager();
                    PlaylistPlayer pp = AMusic.getPlaylistPlayer();
                    if (pm != null && pp != null) {
                        Playlist pl = pm.loadPlaylist(name);
                        if (pl != null && pl.size() > 0) {
                            selectPlaylist(name);
                            pp.start(pl, 0);
                            onClose();
                        }
                    }
                } else if (isInButton(mouseX, deleteBtnX, deleteBtnW)) {
                    PlaylistManager pm = AMusic.getPlaylistManager();
                    if (pm != null) {
                        pm.deletePlaylist(name);
                    }
                    if (name.equals(selectedPlaylist)) {
                        selectedPlaylist = null;
                        songList.refresh();
                    }
                    playlistList.refresh();
                }
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(name);
            }
        }
    }

    // ---- 歌曲列表 ----

    private class SongList extends ObjectSelectionList<SongList.Entry> {
        private final int listWidth;

        SongList(Minecraft mc, int width) {
            super(mc, width, PlaylistScreen.this.height - 80, 30, PlaylistScreen.this.height - 55);
            int halfW = (PlaylistScreen.this.width - 30) / 2;
            this.setX(20 + halfW);
            this.listWidth = width;
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

        @Override
        public int getRowWidth() {
            return this.listWidth - 8;
        }

        void refresh() {
            clearEntries();
            if (selectedPlaylist == null) return;
            PlaylistManager pm = AMusic.getPlaylistManager();
            if (pm == null) return;
            Playlist pl = pm.loadPlaylist(selectedPlaylist);
            if (pl == null) return;
            // 第一项显示播放顺序
            addEntry(new Entry(null, "播放顺序: " + pl.getPlayOrder().getDisplayName(), true));
            for (HistoryEntry e : pl.getSongs()) {
                addEntry(new Entry(e, e.getName(), false));
            }
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {
            private final HistoryEntry data;
            private final String label;
            private final boolean isPlayOrderEntry;

            // 按钮区域缓存
            private int[] btnX = new int[2];
            private int[] btnW = new int[2];
            private int orderBtnX, orderBtnW;

            Entry(HistoryEntry data, String label, boolean isPlayOrderEntry) {
                this.data = data;
                this.label = label;
                this.isPlayOrderEntry = isPlayOrderEntry;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                Font font = Minecraft.getInstance().font;

                if (isPlayOrderEntry) {
                    // 播放顺序行
                    graphics.drawString(font, label, left + 2, top + 2, 0xFFAA00);
                    String switchLabel = "[切换]";
                    orderBtnX = left + 2 + font.width(label) + 6;
                    orderBtnW = font.width(switchLabel);
                    boolean hover = isInButton(mouseX, orderBtnX, orderBtnW) && mouseY >= top + 1 && mouseY <= top + 11;
                    graphics.drawString(font, switchLabel, orderBtnX, top + 2, hover ? 0xFFFF55 : 0x55FF55);
                    return;
                }

                // 高亮当前播放歌曲
                PlaylistPlayer pp = AMusic.getPlaylistPlayer();
                boolean isCurrent = false;
                if (pp != null && pp.isActive() && data != null && pp.getActivePlaylist() != null) {
                    Playlist active = pp.getActivePlaylist();
                    if (selectedPlaylist != null && selectedPlaylist.equals(active.getName())) {
                        // index - 1 因为第一项是播放顺序行
                        int songIndex = index - 1;
                        if (songIndex == pp.getCurrentIndex()) {
                            isCurrent = true;
                        }
                    }
                }
                int nameColor = isCurrent ? 0x55FF55 : 0xFFFFFF;
                graphics.drawString(font, label, left + 2, top + 2, nameColor);

                // 按钮行：[播放] [移除]
                int btnY = top + 11;
                int x = left + 2;

                // 平台标签
                if (data != null && data.getPlatform() != null && !data.getPlatform().isEmpty()) {
                    String platLabel = "[" + data.getPlatform() + "]";
                    graphics.drawString(font, platLabel, x, btnY, 0x888888);
                    x += font.width(platLabel) + 4;
                }

                String playLabel = isCurrent ? "[播放中]" : "[播放]";
                btnX[0] = x;
                btnW[0] = font.width(playLabel);
                boolean hoverPlay = isInButton(mouseX, btnX[0], btnW[0]) && mouseY >= btnY - 2 && mouseY <= btnY + 10;
                graphics.drawString(font, playLabel, x, btnY, isCurrent ? 0x55FF55 : (hoverPlay ? 0xFFFF55 : 0x55FF55));
                x += btnW[0] + 4;

                String removeLabel = "[移除]";
                btnX[1] = x;
                btnW[1] = font.width(removeLabel);
                boolean hoverRemove = isInButton(mouseX, btnX[1], btnW[1]) && mouseY >= btnY - 2 && mouseY <= btnY + 10;
                graphics.drawString(font, removeLabel, x, btnY, hoverRemove ? 0xFFFF55 : 0xFF5555);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) return true;

                if (isPlayOrderEntry) {
                    if (isInButton(mouseX, orderBtnX, orderBtnW)) {
                        cyclePlayOrder();
                        refresh();
                    }
                    return true;
                }

                if (data == null) return true;

                if (isInButton(mouseX, btnX[0], btnW[0])) {
                    // [播放]
                    playSong(data);
                } else if (isInButton(mouseX, btnX[1], btnW[1])) {
                    // [移除]
                    if (selectedPlaylist != null) {
                        PlaylistManager pm = AMusic.getPlaylistManager();
                        if (pm != null) {
                            pm.removeSong(selectedPlaylist, data.getName());
                        }
                        refresh();
                    }
                }
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(label);
            }
        }
    }
}
