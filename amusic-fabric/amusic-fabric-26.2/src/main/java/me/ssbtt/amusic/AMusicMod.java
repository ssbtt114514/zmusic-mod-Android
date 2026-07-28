package me.ssbtt.amusic;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.client.AMusicKeys;
import me.ssbtt.amusic.client.ChatLinkListener;
import me.ssbtt.amusic.client.CommandSender;
import me.ssbtt.amusic.client.gui.HistoryScreen;
import me.ssbtt.amusic.client.gui.PlaylistScreen;
import me.ssbtt.amusic.client.gui.SettingsScreen;
import me.ssbtt.amusic.client.gui.VolumeOverlay;
import me.ssbtt.amusic.client.gui.VolumeOverlayPositionScreen;
import me.ssbtt.amusic.event.ClientEvent;
import me.ssbtt.amusic.manager.SoundManagerImpl;
import me.ssbtt.amusic.network.AMusicPayload;
import me.ssbtt.amusic.playlist.PlaylistPlayer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.VanillaHudElements;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.io.File;

/**
 * Fabric 客户端 Mod 主入口。
 *
 * <p>负责注册：插件通道（S2C/C2S）、按键绑定、聊天事件、客户端 Tick、HUD 渲染等。</p>
 *
 * @author 真心
 * @since 2023/1/28 13:01
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
@Log4j2
public class AMusicMod implements ClientModInitializer {

    /** 音量条 overlay 实例（HUD 渲染回调中使用） */
    public static final VolumeOverlay volumeOverlay = new VolumeOverlay();

    @Override
    public void onInitializeClient() {
        // 设置配置目录（gameDir/config）
        File gameDir = Minecraft.getInstance().gameDirectory;
        File configDir = new File(gameDir, "config");
        AMusic.setConfigDir(configDir);
        AMusic.setSoundManager(new SoundManagerImpl());

        // 注册插件通道：S2C（服务端→客户端）与 C2S（客户端→服务端，歌单上传等）
        PayloadTypeRegistry.playS2C().register(AMusicPayload.TYPE, AMusicPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AMusicPayload.TYPE, AMusicPayload.STREAM_CODEC);

        // 接收服务端数据包
        ClientPlayNetworking.registerGlobalReceiver(AMusicPayload.TYPE, (payload, context) -> {
            ClientEvent.onPacket(payload.message());
        });

        // 断开连接时停止播放
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEvent.onDisconnect());

        // 初始化核心模块
        AMusic.onEnable();

        // 设置歌单播放回调：通过 CommandSender 发送点歌命令到服务器
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pp != null) {
            pp.setCallback(entry ->
                    CommandSender.sendPlayCommand(entry.getPlatform(), entry.getName()));
        }

        // 注册按键绑定
        AMusicKeys.register();

        // 注册聊天消息事件：捕获点歌后服务端返回的可点击链接
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            ChatLinkListener.processMessage(message);
        });

        // 注册客户端 Tick：按键处理 + 音量同步
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                return;
            }
            handleKey(AMusicKeys.OPEN_SETTINGS, () -> client.setScreen(SettingsScreen.build(client.screen)));
            handleKey(AMusicKeys.HISTORY, () -> client.setScreen(new HistoryScreen()));
            handleKey(AMusicKeys.PLAYLIST, () -> client.setScreen(new PlaylistScreen()));
            handleKey(AMusicKeys.PAUSE, CommandSender::sendPause);
            handleKey(AMusicKeys.PREVIOUS, CommandSender::sendPrevious);
            handleKey(AMusicKeys.NEXT, CommandSender::sendNext);
            handleKey(AMusicKeys.VOLUME_UP, () -> adjustVolume(0.1f));
            handleKey(AMusicKeys.VOLUME_DOWN, () -> adjustVolume(-0.1f));
            handleKey(AMusicKeys.MUTE_TOGGLE, this::toggleMute);
            handleKey(AMusicKeys.VOLUME_POSITION, () -> client.setScreen(new VolumeOverlayPositionScreen()));

            // 音量同步到播放器（静音时为 0，否则取配置音量）
            AMusicConfig config = AMusic.getConfig();
            AMusicPlayer player = AMusic.getPlayer();
            if (config != null && player != null) {
                float v = config.isMuted() ? 0f : config.getVolume();
                player.setVolume(v);
            }
        });

        // 注册 HUD 渲染：音量条 overlay（Fabric 26.1 使用 HudElementRegistry 替代已移除的 HudRenderCallback）
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("amusic", "volume_overlay"),
                (graphics, tickCounter) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        volumeOverlay.render(graphics);
                    }
                });
    }

    private void handleKey(KeyMapping binding, Runnable action) {
        while (binding.consumeClick()) {
            action.run();
        }
    }

    private void adjustVolume(float delta) {
        AMusicConfig config = AMusic.getConfig();
        if (config == null) return;
        config.setVolume(config.getVolume() + delta);
        config.setMuted(false);
        config.save();
        volumeOverlay.show();
    }

    private void toggleMute() {
        AMusicConfig config = AMusic.getConfig();
        if (config == null) return;
        config.setMuted(!config.isMuted());
        config.save();
        volumeOverlay.show();
    }

    /**
     * Mod 菜单「配置」按钮入口（可选，由 modmenu 调用）。
     */
    public static Screen createConfigScreen(Screen parent) {
        return SettingsScreen.build(parent);
    }
}
