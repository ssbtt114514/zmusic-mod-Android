package me.ssbtt.amusic.client.gui;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.client.PlaylistNetSender;
import me.ssbtt.amusic.playlist.Playlist;
import me.ssbtt.amusic.playlist.PlaylistManager;
import me.ssbtt.amusic.playlist.PlaylistNetClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * 在线歌单界面（Fabric 版本）。
 *
 * <p>查看服务器公开/自己的歌单，下载服务器歌单，上传本地歌单。</p>
 *
 * @author ssbtt
 * @since 2026-07-28
 */
@Log4j2
public class OnlinePlaylistScreen extends Screen {

    private static final int ROW_HEIGHT = 22;

    private enum Mode { PUBLIC, MINE }

    private Mode currentMode = Mode.PUBLIC;
    private OnlineList list;
    private String statusMessage = "";
    private int statusColor = 0xFFFFFF;
    private boolean firstLoad = true;

    public OnlinePlaylistScreen() {
        super(Text.literal("在线歌单"));
    }

    @Override
    protected void init() {
        PlaylistNetClient netClient = PlaylistNetClient.getInstance();
        netClient.setListCallback(this::onListReceived);
        netClient.setDataCallback(this::onDataReceived);
        netClient.setResultCallback(this::onResultReceived);

        list = new OnlineList();
        addSelectableChild(list);

        addDrawableChild(ButtonWidget.builder(Text.literal("公开歌单"), b -> switchMode(Mode.PUBLIC))
                .dimensions(width / 2 - 155, 5, 70, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("我的歌单"), b -> switchMode(Mode.MINE))
                .dimensions(width / 2 - 80, 5, 70, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("上传歌单"), b -> openUploadDialog())
                .dimensions(width / 2 - 5, 5, 70, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("刷新"), b -> refreshList())
                .dimensions(width / 2 + 70, 5, 70, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close())
                .dimensions(width / 2 - 40, height - 25, 80, 18).build());

        if (firstLoad) {
            firstLoad = false;
            refreshList();
        }
    }

    private void switchMode(Mode mode) {
        currentMode = mode;
        refreshList();
    }

    private void refreshList() {
        statusMessage = "加载中...";
        statusColor = 0xFFFF55;
        list.clear();
        if (currentMode == Mode.PUBLIC) {
            PlaylistNetSender.requestPublicList();
        } else {
            PlaylistNetSender.requestMyList();
        }
    }

    private void onListReceived(List<PlaylistNetClient.PlaylistInfo> playlists) {
        MinecraftClient.getInstance().execute(() -> {
            list.clear();
            for (PlaylistNetClient.PlaylistInfo info : playlists) {
                list.addEntry(info);
            }
            statusMessage = "共 " + playlists.size() + " 个歌单";
            statusColor = 0x55FF55;
        });
    }

    private void onDataReceived(Playlist playlist) {
        MinecraftClient.getInstance().execute(() -> {
            PlaylistManager pm = AMusic.getPlaylistManager();
            if (pm != null && playlist != null) {
                if (pm.loadPlaylist(playlist.getName()) != null) {
                    statusMessage = "歌单「" + playlist.getName() + "」已存在，请改名后再下载";
                    statusColor = 0xFF5555;
                    return;
                }
                pm.savePlaylist(playlist);
                statusMessage = "下载成功: " + playlist.getName() + " (" + playlist.size() + "首)";
                statusColor = 0x55FF55;
                log.info("Downloaded playlist '{}' from server ({} songs)",
                        playlist.getName(), playlist.size());
            }
        });
    }

    private void onResultReceived(String result) {
        MinecraftClient.getInstance().execute(() -> {
            statusMessage = result;
            statusColor = result.startsWith("success") ? 0x55FF55 : 0xFF5555;
        });
    }

    private void openUploadDialog() {
        PlaylistManager pm = AMusic.getPlaylistManager();
        if (pm == null) return;
        List<String> localPlaylists = pm.listPlaylists();
        if (localPlaylists.isEmpty()) {
            statusMessage = "没有本地歌单可上传";
            statusColor = 0xFF5555;
            return;
        }
        MinecraftClient.getInstance().setScreen(new UploadPlaylistScreen(localPlaylists, this));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 27, 0xFFFFFF);
        list.render(context, mouseX, mouseY, delta);
        if (!statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, statusMessage, this.width / 2, height - 45, statusColor);
        }
    }

    private class OnlineList extends AlwaysSelectedEntryListWidget<OnlineList.Entry> {

        OnlineList() {
            super(MinecraftClient.getInstance(), OnlinePlaylistScreen.this.width,
                    OnlinePlaylistScreen.this.height - 100, 30, OnlinePlaylistScreen.this.height - 70, ROW_HEIGHT);
        }

        public void addEntry(PlaylistNetClient.PlaylistInfo info) {
            addEntry(new Entry(info));
        }

        public void clear() {
            clearEntries();
        }

        @Override
        public int getRowWidth() {
            return width - 20;
        }

        private class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
            private final PlaylistNetClient.PlaylistInfo info;
            private int downloadBtnX, downloadBtnW;

            Entry(PlaylistNetClient.PlaylistInfo info) {
                this.info = info;
            }

            @Override
            public void render(DrawContext context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float delta) {
                MinecraftClient mc = MinecraftClient.getInstance();
                context.drawTextWithShadow(mc.textRenderer, info.name, left + 2, top + 2, 0xFFFFFF);
                String meta = "作者: " + (info.author != null ? info.author : "?") + " | " + info.songCount + "首";
                context.drawTextWithShadow(mc.textRenderer, meta, left + 2, top + 11, 0xAAAAFF);

                String dlLabel = "[下载]";
                downloadBtnX = left + width - mc.textRenderer.getWidth(dlLabel) - 4;
                downloadBtnW = mc.textRenderer.getWidth(dlLabel);
                boolean hoverDl = mouseX >= downloadBtnX && mouseX <= downloadBtnX + downloadBtnW && mouseY >= top && mouseY <= top + height;
                context.drawTextWithShadow(mc.textRenderer, dlLabel, downloadBtnX, top + 7, hoverDl ? 0xFFFF55 : 0x55FF55);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) return true;
                if (mouseX >= downloadBtnX && mouseX <= downloadBtnX + downloadBtnW) {
                    statusMessage = "下载中: " + info.name + "...";
                    statusColor = 0xFFFF55;
                    PlaylistNetSender.downloadPlaylist(info.id);
                    log.info("Requesting download for playlist '{}' (id={})", info.name, info.id);
                }
                return true;
            }

            @Override
            public Text getNarration() {
                return Text.literal(info != null ? info.name : "");
            }
        }
    }

    /**
     * 上传歌单选择界面。
     */
    public static class UploadPlaylistScreen extends Screen {
        private final List<String> localPlaylists;
        private final Screen parent;
        private UploadList list;

        public UploadPlaylistScreen(List<String> localPlaylists, Screen parent) {
            super(Text.literal("上传歌单"));
            this.localPlaylists = localPlaylists;
            this.parent = parent;
        }

        @Override
        protected void init() {
            list = new UploadList();
            for (String name : localPlaylists) {
                list.addEntry(name);
            }
            addSelectableChild(list);

            addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> {
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(width / 2 - 40, height - 25, 80, 18).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context);
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, "选择要上传的歌单（点击歌单名上传）", this.width / 2, 10, 0xFFFFFF);
            list.render(context, mouseX, mouseY, delta);
        }

        private class UploadList extends AlwaysSelectedEntryListWidget<UploadList.Entry> {

            UploadList() {
                super(MinecraftClient.getInstance(), UploadPlaylistScreen.this.width,
                        UploadPlaylistScreen.this.height - 60, 30, UploadPlaylistScreen.this.height - 30, ROW_HEIGHT);
            }

            public void addEntry(String name) {
                addEntry(new Entry(name));
            }

            @Override
            public int getRowWidth() {
                return width - 20;
            }

            private class Entry extends AlwaysSelectedEntryListWidget.Entry<Entry> {
                private final String name;
                private int uploadBtnX, uploadBtnW;
                private int uploadPrivateBtnX, uploadPrivateBtnW;

                Entry(String name) {
                    this.name = name;
                }

                @Override
                public void render(DrawContext context, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float delta) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    PlaylistManager pm = AMusic.getPlaylistManager();
                    Playlist pl = pm != null ? pm.loadPlaylist(name) : null;
                    int count = pl != null ? pl.size() : 0;
                    context.drawTextWithShadow(mc.textRenderer, name + " (" + count + "首)", left + 2, top + 2, 0xFFFFFF);

                    String pubLabel = "[公开上传]";
                    uploadBtnX = left + 2;
                    uploadBtnW = mc.textRenderer.getWidth(pubLabel);
                    boolean hoverPub = mouseX >= uploadBtnX && mouseX <= uploadBtnX + uploadBtnW && mouseY >= top + 10 && mouseY <= top + 22;
                    context.drawTextWithShadow(mc.textRenderer, pubLabel, uploadBtnX, top + 11, hoverPub ? 0xFFFF55 : 0x55FF55);

                    String privLabel = "[私有上传]";
                    uploadPrivateBtnX = uploadBtnX + uploadBtnW + 8;
                    uploadPrivateBtnW = mc.textRenderer.getWidth(privLabel);
                    boolean hoverPriv = mouseX >= uploadPrivateBtnX && mouseX <= uploadPrivateBtnX + uploadPrivateBtnW && mouseY >= top + 10 && mouseY <= top + 22;
                    context.drawTextWithShadow(mc.textRenderer, privLabel, uploadPrivateBtnX, top + 11, hoverPriv ? 0xFFFF55 : 0xFFAA00);
                }

                @Override
                public boolean mouseClicked(double mouseX, double mouseY, int button) {
                    if (button != 0) return true;
                    PlaylistManager pm = AMusic.getPlaylistManager();
                    if (pm == null) return true;
                    Playlist pl = pm.loadPlaylist(name);
                    if (pl == null) return true;

                    if (mouseX >= uploadBtnX && mouseX <= uploadBtnX + uploadBtnW) {
                        pl.setPublic(true);
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player != null) {
                            pl.setAuthor(mc.player.getName().getString());
                        }
                        PlaylistNetSender.uploadPlaylist(pl);
                        log.info("Uploading playlist '{}' as public", name);
                        MinecraftClient.getInstance().setScreen(parent);
                    } else if (mouseX >= uploadPrivateBtnX && mouseX <= uploadPrivateBtnX + uploadPrivateBtnW) {
                        pl.setPublic(false);
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player != null) {
                            pl.setAuthor(mc.player.getName().getString());
                        }
                        PlaylistNetSender.uploadPlaylist(pl);
                        log.info("Uploading playlist '{}' as private", name);
                        MinecraftClient.getInstance().setScreen(parent);
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
}
