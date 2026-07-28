package me.ssbtt.amusic.client.gui;

// TODO: 1.14-1.16 渲染API不同，需要适配 FontRenderer/RenderSystem

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.history.HistoryEntry;
import me.ssbtt.amusic.playlist.Playlist;
import me.ssbtt.amusic.playlist.PlaylistManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * 歌单选择界面（Fabric 版本）。
 *
 * <p>从历史记录收藏歌曲时弹出，让玩家选择要添加到的目标歌单，
 * 也可以在此界面直接新建歌单。</p>
 *
 * @author ssbtt
 * @since 2026-07-28
 */
@Log4j2
public class SelectPlaylistScreen extends Screen {

    private static final int ROW_HEIGHT = 18;

    private final HistoryEntry entry;
    private final Screen parent;
    private PlaylistList list;
    private TextFieldWidget newPlaylistBox;
    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;

    public SelectPlaylistScreen(HistoryEntry entry, Screen parent) {
        super(Text.literal("选择歌单"));
        this.entry = entry;
        this.parent = parent;
    }

    @Override
    protected void init() {
        list = new PlaylistList();
        addSelectableChild(list);

        newPlaylistBox = new TextFieldWidget(this.textRenderer, 10, this.height - 50, 150, 18, Text.literal("新歌单名"));
        newPlaylistBox.setMaxLength(30);
        addDrawableChild(newPlaylistBox);

        addDrawableChild(ButtonWidget.builder(Text.literal("新建并添加"), b -> createAndAdd())
                .dimensions(165, this.height - 50, 90, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> onClose())
                .dimensions(this.width / 2 - 100, this.height - 24, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "选择要将「" + (entry != null ? entry.getName() : "") + "」添加到的歌单",
                10, 24, 0xAAAAFF);
        list.render(context, mouseX, mouseY, delta);

        if (!statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, statusMessage, this.width / 2, this.height - 62, statusColor);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return list.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    private void createAndAdd() {
        String name = newPlaylistBox.getText().trim();
        if (name.isEmpty()) {
            statusMessage = "请输入歌单名";
            statusColor = 0xFF5555;
            return;
        }
        PlaylistManager pm = AMusic.getPlaylistManager();
        if (pm == null) return;
        if (pm.loadPlaylist(name) != null) {
            statusMessage = "歌单已存在";
            statusColor = 0xFF5555;
            return;
        }
        if (!pm.createPlaylist(name)) {
            statusMessage = "创建失败";
            statusColor = 0xFF5555;
            return;
        }
        addSongToPlaylist(name);
        newPlaylistBox.setText("");
    }

    private void addSongToPlaylist(String playlistName) {
        if (entry == null) return;
        PlaylistManager pm = AMusic.getPlaylistManager();
        if (pm == null) return;
        Playlist pl = pm.loadPlaylist(playlistName);
        if (pl == null) {
            statusMessage = "歌单不存在";
            statusColor = 0xFF5555;
            return;
        }
        for (HistoryEntry e : pl.getSongs()) {
            if (entry.getName() != null && entry.getName().equals(e.getName())) {
                statusMessage = "歌曲已存在于「" + playlistName + "」";
                statusColor = 0xFFAA00;
                return;
            }
        }
        pl.addSong(new HistoryEntry(entry.getName(), entry.getUrl(), entry.getPlatform(),
                System.currentTimeMillis() / 1000));
        pm.savePlaylist(pl);
        statusMessage = "已添加到「" + playlistName + "」";
        statusColor = 0x55FF55;
        log.info("Added song '{}' to playlist '{}'", entry.getName(), playlistName);
        list.refresh();
    }

    private class PlaylistList extends ElementListWidget<PlaylistList.Entry> {

        PlaylistList() {
            super(MinecraftClient.getInstance(), SelectPlaylistScreen.this.width - 20,
                    SelectPlaylistScreen.this.height - 100, 40, SelectPlaylistScreen.this.height - 70, ROW_HEIGHT);
            refresh();
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
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

        private class Entry extends ElementListWidget.Entry<Entry> {
            private final String name;
            private int addBtnX, addBtnW;

            Entry(String name) {
                this.name = name;
            }

            @Override
            public void render(DrawContext context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float delta) {
                MinecraftClient mc = MinecraftClient.getInstance();
                PlaylistManager pm = AMusic.getPlaylistManager();
                String countStr = "";
                if (pm != null) {
                    Playlist pl = pm.loadPlaylist(name);
                    if (pl != null) {
                        countStr = " (" + pl.size() + "首)";
                    }
                }
                context.drawTextWithShadow(mc.textRenderer, name + countStr, left + 4, top + 4, 0xFFFFFF);

                String label = "[添加]";
                addBtnW = mc.textRenderer.getWidth(label);
                addBtnX = left + getRowWidth() - addBtnW - 4;
                boolean hover = mouseX >= addBtnX && mouseX <= addBtnX + addBtnW && mouseY >= top && mouseY <= top + height;
                context.drawTextWithShadow(mc.textRenderer, label, addBtnX, top + 4, hover ? 0xFFFF55 : 0x55FF55);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) return true;
                if (mouseX >= addBtnX && mouseX <= addBtnX + addBtnW) {
                    addSongToPlaylist(name);
                }
                return true;
            }

            @Override
            public Text getNarration() {
                return Text.literal(name);
            }
        }
    }
}
