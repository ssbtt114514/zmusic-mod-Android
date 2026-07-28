package me.ssbtt.amusic.client.gui;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.history.HistoryEntry;
import me.ssbtt.amusic.playlist.Playlist;
import me.ssbtt.amusic.playlist.PlaylistManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 歌单选择界面。
 *
 * <p>从历史记录收藏歌曲时弹出，让玩家选择要添加到的目标歌单，
 * 也可以在此界面直接新建歌单。</p>
 *
 * @author ssbtt
 * @since 2026-07-27
 */
@Log4j2
public class SelectPlaylistScreen extends Screen {

    private static final int ROW_HEIGHT = 18;

    private final HistoryEntry entry;
    private final Screen parent;
    private PlaylistList list;
    private EditBox newPlaylistBox;
    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;

    /**
     * @param entry  要收藏的歌曲
     * @param parent 父界面（关闭后返回）
     */
    public SelectPlaylistScreen(HistoryEntry entry, Screen parent) {
        super(Component.literal("选择歌单"));
        this.entry = entry;
        this.parent = parent;
    }

    @Override
    protected void init() {
        list = new PlaylistList();
        addWidget(list);

        // 新建歌单输入框
        newPlaylistBox = new EditBox(this.font, 10, this.height - 50, 150, 18, Component.literal("新歌单名"));
        newPlaylistBox.setMaxLength(30);
        addRenderableWidget(newPlaylistBox);

        // 创建并添加按钮
        addRenderableWidget(Button.builder(Component.literal("新建并添加"), b -> createAndAdd())
                .bounds(165, this.height - 50, 90, 18).build());

        // 取消按钮
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 24, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        graphics.drawString(this.font, "选择要将「" + (entry != null ? entry.getName() : "") + "」添加到的歌单",
                10, 24, 0xAAAAFF);
        list.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (!statusMessage.isEmpty()) {
            graphics.drawCenteredString(this.font, statusMessage, this.width / 2, this.height - 62, statusColor);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return list.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /**
     * 新建歌单并添加当前歌曲。
     */
    private void createAndAdd() {
        String name = newPlaylistBox.getValue().trim();
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
        newPlaylistBox.setValue("");
    }

    /**
     * 将歌曲添加到指定歌单。
     *
     * @param playlistName 歌单名
     */
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
        // 去重检查
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

    // ---- 歌单列表 ----

    private class PlaylistList extends ObjectSelectionList<PlaylistList.Entry> {

        PlaylistList() {
            super(Minecraft.getInstance(), SelectPlaylistScreen.this.width - 20,
                    SelectPlaylistScreen.this.height - 100, 40, SelectPlaylistScreen.this.height - 70);
            try {
                java.lang.reflect.Field f = net.minecraft.client.gui.components.AbstractSelectionList.class
                        .getDeclaredField("itemHeight");
                f.setAccessible(true);
                f.setInt(this, ROW_HEIGHT);
            } catch (Exception e) {
                log.warn("Failed to set itemHeight", e);
            }
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

        private class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String name;
            private int addBtnX, addBtnW;

            Entry(String name) {
                this.name = name;
            }

            @Override
            public void extractRenderState(GuiGraphicsExtractor graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                Font font = Minecraft.getInstance().font;
                PlaylistManager pm = AMusic.getPlaylistManager();
                String countStr = "";
                if (pm != null) {
                    Playlist pl = pm.loadPlaylist(name);
                    if (pl != null) {
                        countStr = " (" + pl.size() + "首)";
                    }
                }
                graphics.drawString(font, name + countStr, left + 4, top + 4, 0xFFFFFF);

                // [添加] 按钮
                String label = "[添加]";
                addBtnW = font.width(label);
                addBtnX = left + getRowWidth() - addBtnW - 4;
                boolean hover = isInButton(mouseX, addBtnX, addBtnW) && mouseY >= top && mouseY <= top + height;
                graphics.drawString(font, label, addBtnX, top + 4, hover ? 0xFFFF55 : 0x55FF55);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) return true;
                if (isInButton((int) mouseX, addBtnX, addBtnW)) {
                    addSongToPlaylist(name);
                }
                return true;
            }

            @Override
            public Component getNarration() {
                return Component.literal(name);
            }
        }
    }

    private static boolean isInButton(double mouseX, int textStart, int textWidth) {
        return mouseX >= textStart && mouseX <= textStart + textWidth;
    }
}
