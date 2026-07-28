package me.ssbtt.amusic.client;

import me.ssbtt.amusic.playlist.Playlist;

// TODO: AMusicMod.CHANNEL 未定义，需要将 SimpleChannel 提取为 public static 字段后才能使用
public class PlaylistNetSender {
    private PlaylistNetSender() {}

    public static void uploadPlaylist(Playlist playlist) {}
    public static void requestPublicList() {}
    public static void requestMyList() {}
    public static void downloadPlaylist(String id) {}
}
