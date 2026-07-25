package me.zhenxin.zmusic.event;

import lombok.extern.log4j.Log4j2;
import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.ZMusicPlayer;
import me.zhenxin.zmusic.client.CommandSender;
import me.zhenxin.zmusic.client.ZMusicKeys;
import me.zhenxin.zmusic.client.gui.FavoriteScreen;
import me.zhenxin.zmusic.client.gui.HistoryScreen;
import me.zhenxin.zmusic.client.gui.PlaylistScreen;
import me.zhenxin.zmusic.client.gui.SettingsScreen;
import me.zhenxin.zmusic.client.gui.VolumeOverlay;
import me.zhenxin.zmusic.client.gui.VolumeOverlayPositionScreen;
import me.zhenxin.zmusic.config.ZMusicConfig;
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
        // 注册音量条 overlay 的事件处理
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(volumeOverlay);
    }

    @SubscribeEvent
    public void onSound(final PlaySoundEvent event) {
        if (ZMusic.getPlayer() == null || ZMusic.getPlayer().getState() != ZMusicPlayer.STATE_PLAYING || event.getSound() == null) {
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
            ZMusic.getPlayer().stopAsync();
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
        handleKeyWhile(ZMusicKeys.OPEN_SETTINGS, () -> mc.setScreen(SettingsScreen.build(mc.screen)));
        handleKeyWhile(ZMusicKeys.HISTORY, () -> mc.setScreen(new HistoryScreen()));
        handleKeyWhile(ZMusicKeys.FAVORITE, () -> mc.setScreen(new FavoriteScreen()));
        handleKeyWhile(ZMusicKeys.PLAYLIST, () -> mc.setScreen(new PlaylistScreen()));
        handleKeyWhile(ZMusicKeys.PAUSE, CommandSender::sendPause);
        handleKeyWhile(ZMusicKeys.PREVIOUS, CommandSender::sendPrevious);
        handleKeyWhile(ZMusicKeys.NEXT, CommandSender::sendNext);
        handleKeyWhile(ZMusicKeys.VOLUME_UP, () -> adjustVolume(0.1f));
        handleKeyWhile(ZMusicKeys.VOLUME_DOWN, () -> adjustVolume(-0.1f));
        handleKeyWhile(ZMusicKeys.MUTE_TOGGLE, this::toggleMute);
        handleKeyWhile(ZMusicKeys.VOLUME_POSITION, () -> mc.setScreen(new VolumeOverlayPositionScreen()));

        // 音量同步到播放器（静音时为 0，否则取配置音量）
        ZMusicConfig config = ZMusic.getConfig();
        if (config != null && ZMusic.getPlayer() != null) {
            float v = config.isMuted() ? 0f : config.getVolume();
            ZMusic.getPlayer().setVolume(v);
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
        ZMusicConfig config = ZMusic.getConfig();
        if (config == null) return;
        config.setVolume(config.getVolume() + delta);
        config.setMuted(false);
        config.save();
        volumeOverlay.show();
    }

    private void toggleMute() {
        ZMusicConfig config = ZMusic.getConfig();
        if (config == null) return;
        config.setMuted(!config.isMuted());
        config.save();
        volumeOverlay.show();
    }
}

