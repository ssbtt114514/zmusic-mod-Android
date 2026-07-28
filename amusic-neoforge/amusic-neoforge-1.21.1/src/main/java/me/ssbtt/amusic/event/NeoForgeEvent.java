package me.ssbtt.amusic.event;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.AMusicPlayer;
import me.ssbtt.amusic.client.CommandSender;
import me.ssbtt.amusic.client.AMusicKeys;
import me.ssbtt.amusic.client.ChatLinkListener;
import me.ssbtt.amusic.client.gui.HistoryScreen;
import me.ssbtt.amusic.client.gui.PlaylistScreen;
import me.ssbtt.amusic.client.gui.SettingsScreen;
import me.ssbtt.amusic.client.gui.VolumeOverlay;
import me.ssbtt.amusic.client.gui.VolumeOverlayPositionScreen;
import me.ssbtt.amusic.config.AMusicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * NeoForge 事件
 *
 * @author 真心
 * @since 2026-04-24 18:00
 */
@Log4j2
public class NeoForgeEvent {

    /** 音量条 overlay 实例 */
    public static final VolumeOverlay volumeOverlay = new VolumeOverlay();

    public NeoForgeEvent() {
        // 注册 overlay 与聊天监听器的事件处理
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(volumeOverlay);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(ChatLinkListener.INSTANCE);
    }

    @SubscribeEvent
    public void onSound(final PlaySoundEvent event) {
        if (AMusic.getPlayer() == null || AMusic.getPlayer().getState() != AMusicPlayer.STATE_PLAYING || event.getSound() == null) {
            return;
        }

        SoundSource data = event.getSound().getSource();
        switch (data) {
            case MUSIC:
            case RECORDS:
                event.setSound(null);
                break;
            default:
        }
    }

    @SubscribeEvent
    public void onServerQuit(final ClientPlayerNetworkEvent.LoggingOut event) {
        try {
            AMusic.getPlayer().stopAsync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 按键检测（仅在游戏内消费，避免在 GUI 内触发）
        handleKeyWhile(AMusicKeys.OPEN_SETTINGS, () -> mc.setScreen(SettingsScreen.build(mc.screen)));
        handleKeyWhile(AMusicKeys.HISTORY, () -> mc.setScreen(new HistoryScreen()));
        handleKeyWhile(AMusicKeys.PLAYLIST, () -> mc.setScreen(new PlaylistScreen()));
        handleKeyWhile(AMusicKeys.PAUSE, CommandSender::sendPause);
        handleKeyWhile(AMusicKeys.PREVIOUS, CommandSender::sendPrevious);
        handleKeyWhile(AMusicKeys.NEXT, CommandSender::sendNext);
        handleKeyWhile(AMusicKeys.VOLUME_UP, () -> adjustVolume(0.1f));
        handleKeyWhile(AMusicKeys.VOLUME_DOWN, () -> adjustVolume(-0.1f));
        handleKeyWhile(AMusicKeys.MUTE_TOGGLE, this::toggleMute);
        handleKeyWhile(AMusicKeys.VOLUME_POSITION, () -> mc.setScreen(new VolumeOverlayPositionScreen()));

        // 音量同步到播放器（静音时为 0，否则取配置音量）
        AMusicConfig config = AMusic.getConfig();
        AMusicPlayer player = AMusic.getPlayer();
        if (config != null && player != null) {
            float v = config.isMuted() ? 0f : config.getVolume();
            player.setVolume(v);
        }
    }

    /**
     * 当按键被按下且当前无屏幕时执行动作。
     *
     * @param mapping 按键映射
     * @param action  动作
     */
    private void handleKeyWhile(net.minecraft.client.KeyMapping mapping, Runnable action) {
        if (mapping.consumeClick()) {
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
}

