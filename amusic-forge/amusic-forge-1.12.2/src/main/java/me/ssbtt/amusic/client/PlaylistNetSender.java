package me.ssbtt.amusic.client;

import me.ssbtt.amusic.playlist.Playlist;

// TODO: 1.12.2 网络系统不同，需要使用 FMLNetworkEvent 重写
public class PlaylistNetSender {
    private PlaylistNetSender() {}

    public static void uploadPlaylist(Playlist playlist) {}
    public static void requestPublicList() {}
    public static void requestMyList() {}
    public static void downloadPlaylist(String id) {}
}
