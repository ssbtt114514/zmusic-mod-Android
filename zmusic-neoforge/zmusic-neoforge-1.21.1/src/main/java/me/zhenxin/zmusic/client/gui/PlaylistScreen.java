package me.zhenxin.zmusic.client.gui;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.client.CommandSender;
import me.zhenxin.zmusic.history.HistoryEntry;
import me.zhenxin.zmusic.playback.NowPlaying;
import me.zhenxin.zmusic.playlist.PlayOrder;
import me.zhenxin.zmusic.playlist.Playlist;
import me.zhenxin.zmusic.playlist.PlaylistManager;
import me.zhenxin.zmusic.playlist.PlaylistPlayer;
import net.minecraft.client.Minecraft;
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
 * @author 真心
 * @since 2026-07-25
 */
@Log4j2
public class PlaylistScreen extends Screen {

    private PlaylistList playlistList;
    private SongList songList;
    private EditBox newPlaylistBox;
    private String selectedPlaylist;

    public PlaylistScreen() {
        super(Component.translatable("zmusic.playlist.title"));
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
        PlaylistManager pm = ZMusic.getPlaylistManager();
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
        PlaylistManager pm = ZMusic.getPlaylistManager();
        PlaylistPlayer pp = ZMusic.getPlaylistPlayer();
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
        PlaylistManager pm = ZMusic.getPlaylistManager();
        PlaylistPlayer pp = ZMusic.getPlaylistPlayer();
        if (pm == null || pp == null) return;
        Playlist pl = pm.loadPlaylist(selectedPlaylist);
        if (pl == null || pl.size() == 0) return;
        pp.start(pl, 0);
        onClose();
    }

    private void stopPlaylist() {
        PlaylistPlayer pp = ZMusic.getPlaylistPlayer();
        if (pp != null) {
            pp.stop();
        }
    }

    private void addCurrentSong() {
        if (selectedPlaylist == null) return;
        NowPlaying np = ZMusic.getNowPlaying();
        if (np == null || np.getName().isEmpty()) return;
        PlaylistManager pm = ZMusic.getPlaylistManager();
        if (pm == null) return;
        HistoryEntry entry = new HistoryEntry(np.getName(), np.getUrl(), np.getPlatform(), System.currentTimeMillis());
        pm.addSong(selectedPlaylist, entry);
        songList.refresh();
    }

    private void cyclePlayOrder() {
        if (selectedPlaylist == null) return;
        PlaylistManager pm = ZMusic.getPlaylistManager();
        if (pm == null) return;
        Playlist pl = pm.loadPlaylist(selectedPlaylist);
        if (pl == null) return;
        PlayOrder[] orders = PlayOrder.values();
        int idx = pl.getPlayOrder().ordinal();
        pl.setPlayOrder(orders[(idx + 1) % orders.length]);
        pm.savePlaylist(pl);
    }

    /**
     * 歌单列表组件。
     */
    private class PlaylistList extends ObjectSelectionList<PlaylistList.Entry> {
        private final int width;

        PlaylistList(Minecraft mc, int width) {
            super(mc, width, PlaylistScreen.this.height - 80, 30, PlaylistScreen.this.height - 55);
            this.setX(10);
            this.width = width;
            refresh();
        }

        void refresh() {
            clearEntries();
            PlaylistManager pm = ZMusic.getPlaylistManager();
            if (pm == null) return;
            List<String> names = pm.listPlaylists();
            for (String n : names) {
                addEntry(new Entry(n));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 8;
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String name;

            Entry(String name) {
                this.name = name;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                String text = name;
                if (name.equals(selectedPlaylist)) {
                    text = "> " + name;
                }
                graphics.drawString(Minecraft.getInstance().font, text, left + 2, top + 4, 0xFFFFFF);
                // 显示歌曲数量
                PlaylistManager pm = ZMusic.getPlaylistManager();
                String countStr = "";
                if (pm != null) {
                    Playlist pl = pm.loadPlaylist(name);
                    if (pl != null) {
                        countStr = " (" + pl.size() + "首)";
                    }
                }
                graphics.drawString(Minecraft.getInstance().font, "[左键选择] [右键删除]" + countStr, left + 2, top + 14, 0xAAAAFF);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 1) {
                    // 右键删除歌单
                    PlaylistManager pm = ZMusic.getPlaylistManager();
                    if (pm != null) {
                        pm.deletePlaylist(name);
                    }
                    if (name.equals(selectedPlaylist)) {
                        selectedPlaylist = null;
                        songList.refresh();
                    }
                    playlistList.refresh();
                } else {
                    selectPlaylist(name);
                }
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(name);
            }
        }
    }

    /**
     * 歌曲列表组件。
     */
    private class SongList extends ObjectSelectionList<SongList.Entry> {
        private final int width;

        SongList(Minecraft mc, int width) {
            super(mc, width, PlaylistScreen.this.height - 80, 30, PlaylistScreen.this.height - 55);
            int halfW = (PlaylistScreen.this.width - 30) / 2;
            this.setX(20 + halfW);
            this.width = width;
            refresh();
        }

        void refresh() {
            clearEntries();
            if (selectedPlaylist == null) return;
            PlaylistManager pm = ZMusic.getPlaylistManager();
            if (pm == null) return;
            Playlist pl = pm.loadPlaylist(selectedPlaylist);
            if (pl == null) return;
            // 第一项显示播放顺序
            addEntry(new Entry(null, "播放顺序: " + pl.getPlayOrder().getDisplayName() + " (点击切换)"));
            for (HistoryEntry e : pl.getSongs()) {
                addEntry(new Entry(e, e.getName()));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 8;
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {
            private final HistoryEntry data;
            private final String label;

            Entry(HistoryEntry data, String label) {
                this.data = data;
                this.label = label;
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                // 高亮当前播放歌曲
                PlaylistPlayer pp = ZMusic.getPlaylistPlayer();
                boolean isCurrent = false;
                if (pp != null && pp.isActive() && data != null && pp.getActivePlaylist() != null) {
                    Playlist active = pp.getActivePlaylist();
                    if (selectedPlaylist != null && selectedPlaylist.equals(active.getName())
                            && index - 1 == pp.getCurrentIndex()) {
                        isCurrent = true;
                    }
                }
                int nameColor = isCurrent ? 0x55FF55 : 0xFFFFFF;
                graphics.drawString(Minecraft.getInstance().font, label, left + 2, top + 4, nameColor);
                if (data != null) {
                    String sub = "[左键播放] [右键移除]";
                    if (data.getPlatform() != null && !data.getPlatform().isEmpty()) {
                        sub = "[" + data.getPlatform() + "] " + sub;
                    }
                    if (isCurrent) {
                        sub = sub + " (播放中)";
                    }
                    graphics.drawString(Minecraft.getInstance().font, sub, left + 2, top + 14, 0xAAAAFF);
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (data == null) {
                    cyclePlayOrder();
                    refresh();
                } else if (button == 1) {
                    // 右键移除歌曲
                    if (selectedPlaylist != null) {
                        PlaylistManager pm = ZMusic.getPlaylistManager();
                        if (pm != null) {
                            pm.removeSong(selectedPlaylist, data.getName());
                        }
                        refresh();
                    }
                } else {
                    playSong(data);
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
