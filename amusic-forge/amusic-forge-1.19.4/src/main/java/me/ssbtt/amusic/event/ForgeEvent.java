package me.ssbtt.amusic.event;

import lombok.extern.log4j.Log4j2;
import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.AMusicPlayer;
import me.ssbtt.amusic.client.AMusicKeys;
import me.ssbtt.amusic.client.ChatLinkListener;
import me.ssbtt.amusic.client.CommandSender;
import me.ssbtt.amusic.client.gui.HistoryScreen;
import me.ssbtt.amusic.client.gui.PlaylistScreen;
import me.ssbtt.amusic.client.gui.SettingsScreen;
import me.ssbtt.amusic.client.gui.VolumeOverlay;
import me.ssbtt.amusic.client.gui.VolumeOverlayPositionScreen;
import me.ssbtt.amusic.config.AMusicConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.sound.SoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Forge 事件
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/3/17 11:17
 */
@Log4j2
public class ForgeEvent {

    /** 音量条 overlay 实例 */
    public static final VolumeOverlay volumeOverlay = new VolumeOverlay();

    private boolean tickSyncDisabled;
    private boolean soundEventDisabled;

    private static volatile boolean soundSourceResolved;
    private static Method getSoundSource;

    public ForgeEvent() {
        // 注册 overlay 与聊天监听器的事件处理
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(volumeOverlay);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ChatLinkListener.INSTANCE);
    }

    /**
     * 通过反射按方法签名（而非名称）定位 {@link SoundInstance#getSource()}。
     */
    private static synchronized void resolveSoundSource() {
        if (soundSourceResolved) {
            return;
        }
        soundSourceResolved = true;
        try {
            for (Method m : SoundInstance.class.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0
                        && m.getReturnType() == SoundSource.class) {
                    m.setAccessible(true);
                    getSoundSource = m;
                    break;
                }
            }
            if (getSoundSource == null) {
                log.warn("AMusic could not resolve SoundInstance.getSource() via reflection");
            }
        } catch (Throwable t) {
            log.warn("AMusic failed to resolve SoundInstance.getSource() via reflection", t);
        }
    }

    @SubscribeEvent
    public void onSound(final SoundEvent.SoundSourceEvent e) {
        if (soundEventDisabled || AMusic.getPlayer().getState() != AMusicPlayer.STATE_PLAYING || e.getSound() == null) {
            return;
        }
        try {
            resolveSoundSource();
            if (getSoundSource == null) {
                soundEventDisabled = true;
                return;
            }
            SoundSource data = (SoundSource) getSoundSource.invoke(e.getSound());
            //noinspection EnhancedSwitchMigration
            switch (data) {
                case MUSIC:
                case RECORDS:
                    e.getChannel().stop();
                    break;
                default:
            }
        } catch (Throwable t) {
            soundEventDisabled = true;
            log.error("AMusic failed to handle vanilla sound event, disabling further attempts.", t);
        }
    }

    @SubscribeEvent
    public void onServerQuit(final ClientPlayerNetworkEvent.LoggingOut e) {
        try {
            AMusic.getPlayer().stopAsync();
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
