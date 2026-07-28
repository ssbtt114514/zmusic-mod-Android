package me.ssbtt.amusic.client;

// TODO: 1.14.4 的 ClientPacketListener 没有 sendCommand 方法（1.16+ 才有），需要使用 sendChatMessage 重写
public class ChatLinkListener {
    public static void onPlayCommandSent() {}
    public static void onPlayCommandSent(boolean modInitiated) {}
}
