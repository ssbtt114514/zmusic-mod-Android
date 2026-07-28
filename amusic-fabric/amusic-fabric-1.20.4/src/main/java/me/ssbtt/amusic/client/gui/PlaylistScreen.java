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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * 歌单界面（Fabric 版本）。
 *
 * <p>左侧歌单列表，右侧选中歌单的歌曲列表，支持创建/删除/播放顺序切换/播放。</p>
 *
 * @author 真心
 * @since 2026-07-28
 */
@Log4j2
public class PlaylistScreen extends Screen {

    private static final int ROW_HEIGHT = 22;

    private PlaylistList playlistList;
    private SongList songList;
    private TextFieldWidget newPlaylistBox;
    private String selectedPlaylist;

    public PlaylistScreen() {
        super(Text.translatable("amusic.playlist.title"));
    }

    @Override
    protected void init() {
        int halfW = (this.width - 30) / 2;
        playlistList = new PlaylistList(MinecraftClient.getInstance(), halfW);
        playlistList.setX(10);
        songList = new SongList(MinecraftClient.getInstance(), halfW);
        songList.setX(20 + halfW);
        addSelectableChild(playlistList);
        addSelectableChild(songList);

        newPlaylistBox = new TextFieldWidget(this.textRenderer, 10, this.height - 50, halfW - 80, 18, Text.literal("新歌单名"));
        newPlaylistBox.setMaxLength(30);
        addDrawableChild(newPlaylistBox);

        addDrawableChild(ButtonWidget.builder(Text.literal("创建"), b -> createPlaylist())
                .dimensions(10 + halfW - 70, this.height - 50, 60, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("播放全部"), b -> playAll())
                .dimensions(20 + halfW, this.height - 50, 80, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("添加当前歌曲"), b -> addCurrentSong())
                .dimensions(20 + halfW + 90, this.height - 50, 90, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("停止播放"), b -> stopPlaylist())
                .dimensions(20 + halfW + 190, this.height - 50, 80, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("在线歌单"), b -> openOnlinePlaylists())
                .dimensions(20 + halfW + 280, this.height - 50, 80, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(this.width / 2 - 50, this.height - 24, 100, 18).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        int halfW = (this.width - 30) / 2;
        context.drawTextWithShadow(this.textRenderer, "歌单列表", 10, 20, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "歌曲列表", 20 + halfW, 20, 0xFFFFFF);
        playlistList.render(context, mouseX, mouseY, delta);
        songList.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int halfW = (this.width - 30) / 2;
        if (mouseX < 10 + halfW) {
            return playlistList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return songList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void createPlaylist() {
        String name = newPlaylistBox.getText().trim();
        if (name.isEmpty()) return;
        PlaylistManager pm = AMusic.getPlaylistManager();
        if (pm != null && pm.createPlaylist(name)) {
            newPlaylistBox.setText("");
            playlistList.refresh();
        }
    }

    private void selectPlaylist(String name) {
        selectedPlaylist = name;
        songList.refresh();
    }

    private void playSong(HistoryEntry entry) {
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
        close();
    }

    private void playAll() {
        if (selectedPlaylist == null) return;
        PlaylistManager pm = AMusic.getPlaylistManager();
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pm == null || pp == null) return;
        Playlist pl = pm.loadPlaylist(selectedPlaylist);
        if (pl == null || pl.size() == 0) return;
        pp.start(pl, 0);
        close();
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

    private void openOnlinePlaylists() {
        MinecraftClient.getInstance().setScreen(new OnlinePlaylistScreen());
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
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pp != null && pp.isActive() && pp.getActivePlaylist() != null
                && selectedPlaylist.equals(pp.getActivePlaylist().getName())) {
            pp.getActivePlaylist().setPlayOrder(pl.getPlayOrder());
        }
    }

    private static boolean isInButton(double mouseX, int textStart, int textWidth) {
        return mouseX >= textStart && mouseX <= textStart + textWidth;
    }

    // ---- 歌单列表 ----

    private class PlaylistList extends AlwaysSelectedEntryListWidget<PlaylistList.Entry> {
        private final int listWidth;

        PlaylistList(MinecraftClient mc, int width) {
            super(mc, width, PlaylistScreen.this.height - 80, 30, PlaylistScreen.this.height - 55);
            this.listWidth = width;
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

        private class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private final String name;
            private int selectBtnX, selectBtnW;
            private int playBtnX, playBtnW;
            private int deleteBtnX, deleteBtnW;

            Entry(String name) {
                this.name = name;
            }

            @Override
            public void render(DrawContext context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float delta) {
                MinecraftClient mc = MinecraftClient.getInstance();
                String text = name;
                if (name.equals(selectedPlaylist)) {
                    text = "> " + name;
                }
                context.drawTextWithShadow(mc.textRenderer, text, left + 2, top + 2, 0xFFFFFF);

                PlaylistManager pm = AMusic.getPlaylistManager();
                String countStr = "";
                if (pm != null) {
                    Playlist pl = pm.loadPlaylist(name);
                    if (pl != null) {
                        countStr = "(" + pl.size() + "首)";
                    }
                }
                context.drawTextWithShadow(mc.textRenderer, countStr, left + 2 + mc.textRenderer.getWidth(text) + 4, top + 2, 0xAAAAFF);

                int btnY = top + 11;
                int x = left + 2;

                String selectLabel = "[选择]";
                selectBtnX = x;
                selectBtnW = mc.textRenderer.getWidth(selectLabel);
                boolean hoverSelect = isInButton(mouseX, selectBtnX, selectBtnW) && mouseY >= btnY - 2 && mouseY <= btnY + 10;
                context.drawTextWithShadow(mc.textRenderer, selectLabel, x, btnY, hoverSelect ? 0xFFFF55 : 0xAAAAFF);
                x += selectBtnW + 4;

                String playLabel = "[播放]";
                playBtnX = x;
                playBtnW = mc.textRenderer.getWidth(playLabel);
                boolean hoverPlay = isInButton(mouseX, playBtnX, playBtnW) && mouseY >= btnY - 2 && mouseY <= btnY + 10;
                context.drawTextWithShadow(mc.textRenderer, playLabel, x, btnY, hoverPlay ? 0xFFFF55 : 0x55FF55);
                x += playBtnW + 4;

                String deleteLabel = "[删除]";
                deleteBtnX = x;
                deleteBtnW = mc.textRenderer.getWidth(deleteLabel);
                boolean hoverDelete = isInButton(mouseX, deleteBtnX, deleteBtnW) && mouseY >= btnY - 2 && mouseY <= btnY + 10;
                context.drawTextWithShadow(mc.textRenderer, deleteLabel, x, btnY, hoverDelete ? 0xFFFF55 : 0xFF5555);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) return true;

                if (isInButton(mouseX, selectBtnX, selectBtnW)) {
                    selectPlaylist(name);
                } else if (isInButton(mouseX, playBtnX, playBtnW)) {
                    PlaylistManager pm = AMusic.getPlaylistManager();
                    PlaylistPlayer pp = AMusic.getPlaylistPlayer();
                    if (pm != null && pp != null) {
                        Playlist pl = pm.loadPlaylist(name);
                        if (pl != null && pl.size() > 0) {
                            selectPlaylist(name);
                            pp.start(pl, 0);
                            close();
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
            public Text getNarration() {
                return Text.literal(name);
            }
        }
    }

    // ---- 歌曲列表 ----

    private class SongList extends AlwaysSelectedEntryListWidget<SongList.Entry> {
        private final int listWidth;

        SongList(MinecraftClient mc, int width) {
            super(mc, width, PlaylistScreen.this.height - 80, 30, PlaylistScreen.this.height - 55);
            this.listWidth = width;
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
            addEntry(new Entry(null, "播放顺序: " + pl.getPlayOrder().getDisplayName(), true));
            for (HistoryEntry e : pl.getSongs()) {
                addEntry(new Entry(e, e.getName(), false));
            }
        }

        private class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private final HistoryEntry data;
            private final String label;
            private final boolean isPlayOrderEntry;
            private int[] btnX = new int[2];
            private int[] btnW = new int[2];
            private int orderBtnX, orderBtnW;

            Entry(HistoryEntry data, String label, boolean isPlayOrderEntry) {
                this.data = data;
                this.label = label;
                this.isPlayOrderEntry = isPlayOrderEntry;
            }

            @Override
            public void render(DrawContext context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float delta) {
                MinecraftClient mc = MinecraftClient.getInstance();

                if (isPlayOrderEntry) {
                    context.drawTextWithShadow(mc.textRenderer, label, left + 2, top + 2, 0xFFAA00);
                    String switchLabel = "[切换]";
                    orderBtnX = left + 2 + mc.textRenderer.getWidth(label) + 6;
                    orderBtnW = mc.textRenderer.getWidth(switchLabel);
                    boolean hover = isInButton(mouseX, orderBtnX, orderBtnW) && mouseY >= top + 1 && mouseY <= top + 11;
                    context.drawTextWithShadow(mc.textRenderer, switchLabel, orderBtnX, top + 2, hover ? 0xFFFF55 : 0x55FF55);
                    return;
                }

                PlaylistPlayer pp = AMusic.getPlaylistPlayer();
                boolean isCurrent = false;
                if (pp != null && pp.isActive() && data != null && pp.getActivePlaylist() != null) {
                    Playlist active = pp.getActivePlaylist();
                    if (selectedPlaylist != null && selectedPlaylist.equals(active.getName())) {
                        int songIndex = index - 1;
                        if (songIndex == pp.getCurrentIndex()) {
                            isCurrent = true;
                        }
                    }
                }
                int nameColor = isCurrent ? 0x55FF55 : 0xFFFFFF;
                context.drawTextWithShadow(mc.textRenderer, label, left + 2, top + 2, nameColor);

                int btnY = top + 11;
                int x = left + 2;

                if (data != null && data.getPlatform() != null && !data.getPlatform().isEmpty()) {
                    String platLabel = "[" + data.getPlatform() + "]";
                    context.drawTextWithShadow(mc.textRenderer, platLabel, x, btnY, 0x888888);
                    x += mc.textRenderer.getWidth(platLabel) + 4;
                }

                String playLabel = isCurrent ? "[播放中]" : "[播放]";
                btnX[0] = x;
                btnW[0] = mc.textRenderer.getWidth(playLabel);
                boolean hoverPlay = isInButton(mouseX, btnX[0], btnW[0]) && mouseY >= btnY - 2 && mouseY <= btnY + 10;
                context.drawTextWithShadow(mc.textRenderer, playLabel, x, btnY, isCurrent ? 0x55FF55 : (hoverPlay ? 0xFFFF55 : 0x55FF55));
                x += btnW[0] + 4;

                String removeLabel = "[移除]";
                btnX[1] = x;
                btnW[1] = mc.textRenderer.getWidth(removeLabel);
                boolean hoverRemove = isInButton(mouseX, btnX[1], btnW[1]) && mouseY >= btnY - 2 && mouseY <= btnY + 10;
                context.drawTextWithShadow(mc.textRenderer, removeLabel, x, btnY, hoverRemove ? 0xFFFF55 : 0xFF5555);
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
                    playSong(data);
                } else if (isInButton(mouseX, btnX[1], btnW[1])) {
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
            public Text getNarration() {
                return Text.literal(label);
            }
        }
    }
}
