package me.ssbtt.amusic;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.config.AMusicConfig;
import me.ssbtt.amusic.client.AMusicKeys;
import me.ssbtt.amusic.client.CommandSender;
import me.ssbtt.amusic.client.gui.HistoryScreen;
import me.ssbtt.amusic.client.gui.PlaylistScreen;
import me.ssbtt.amusic.client.gui.SettingsScreen;
import me.ssbtt.amusic.client.gui.VolumeOverlay;
import me.ssbtt.amusic.client.gui.VolumeOverlayPositionScreen;
import me.ssbtt.amusic.event.ClientEvent;
import me.ssbtt.amusic.manager.SoundManagerImpl;
import me.ssbtt.amusic.playlist.PlaylistPlayer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import io.netty.buffer.Unpooled;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Fabric 客户端 Mod 主入口（1.17.1 旧版网络 API + MatrixStack GUI）。
 *
 * <p>负责注册：插件通道（S2C/C2S）、按键绑定、客户端 Tick、HUD 渲染等。</p>
 *
 * <p>注意：1.17.1 使用旧版 Fabric 网络 API（{@code Identifier} + {@code PacketByteBuf}），
 * 不支持 {@code ClientReceiveMessageEvents}（1.19+ 才有），GUI 渲染使用 {@code MatrixStack}，
 * 屏幕切换使用 {@code setScreen}（1.17+ 替代 {@code openScreen}）。</p>
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/28 13:01
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
@Log4j2
public class AMusicMod implements ClientModInitializer {

    /** 插件通道标识符 */
    public static final Identifier CHANNEL_ID = new Identifier("amusic", "channel");

    /** 音量条 overlay 实例（HUD 渲染回调中使用） */
    public static final VolumeOverlay volumeOverlay = new VolumeOverlay();

    @Override
    public void onInitializeClient() {
        // 设置配置目录（gameDir/config）
        File gameDir = MinecraftClient.getInstance().runDirectory;
        File configDir = new File(gameDir, "config");
        AMusic.setConfigDir(configDir);
        AMusic.setSoundManager(new SoundManagerImpl());

        // 注册插件通道接收器（旧版 API：Identifier + PacketByteBuf）
        ClientPlayNetworking.registerGlobalReceiver(CHANNEL_ID, (client, handler, buf, responseSender) -> {
            byte[] buffer = new byte[buf.readableBytes()];
            buf.readBytes(buffer);
            if (buffer.length == 0) return;
            // 兼容服务端首字节标记
            buffer[0] = 0;
            String message = new String(buffer, StandardCharsets.UTF_8).substring(1);
            ClientEvent.onPacket(message);
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

        // 注册客户端 Tick：按键处理 + 音量同步
        // 注意：1.17.1 没有 ClientReceiveMessageEvents，聊天链接捕获功能不可用
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) {
                return;
            }
            handleKey(AMusicKeys.OPEN_SETTINGS, () -> client.setScreen(SettingsScreen.build(client.currentScreen)));
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

        // 注册 HUD 渲染：音量条 overlay（1.15+ 使用 MatrixStack）
        HudRenderCallback.EVENT.register((MatrixStack matrices, float tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                volumeOverlay.render(matrices);
            }
        });
    }

    private void handleKey(KeyBinding binding, Runnable action) {
        while (binding.wasPressed()) {
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
     * 通过旧版网络 API 发送数据包到服务端。
     *
     * @param message 消息内容
     */
    public static void sendPacket(String message) {
        ClientPlayNetworking.send(CHANNEL_ID, toBuf(message));
    }

    /**
     * 将字符串消息转换为 PacketByteBuf（旧版 API）。
     */
    private static PacketByteBuf toBuf(String message) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeBytes(message.getBytes(StandardCharsets.UTF_8));
        return buf;
    }

    /**
     * Mod 菜单「配置」按钮入口（可选，由 modmenu 调用）。
     */
    public static Screen createConfigScreen(Screen parent) {
        return SettingsScreen.build(parent);
    }
}
