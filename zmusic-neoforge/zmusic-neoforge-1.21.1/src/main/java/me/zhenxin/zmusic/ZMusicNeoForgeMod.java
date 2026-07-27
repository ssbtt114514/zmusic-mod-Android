package me.zhenxin.zmusic;

import me.zhenxin.zmusic.client.ZMusicKeys;
import me.zhenxin.zmusic.client.gui.SettingsScreen;
import me.zhenxin.zmusic.event.ClientEvent;
import me.zhenxin.zmusic.event.NeoForgeEvent;
import me.zhenxin.zmusic.manager.SoundManagerImpl;
import me.zhenxin.zmusic.network.ZMusicPayload;
import me.zhenxin.zmusic.playlist.PlaylistPlayer;
import me.zhenxin.zmusic.history.HistoryEntry;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.io.File;

/**
 * NeoForge Mod 主入口
 *
 * @author 真心
 * @since 2026-04-24 18:00
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
@Mod(value = "zmusic", dist = Dist.CLIENT)
public class ZMusicNeoForgeMod {

    public ZMusicNeoForgeMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.register(new NeoForgeEvent());
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // 设置配置目录（gameDir/config）
        File gameDir = Minecraft.getInstance().gameDirectory;
        File configDir = new File(gameDir, "config");
        ZMusic.setConfigDir(configDir);
        ZMusic.setSoundManager(new SoundManagerImpl());
        ZMusic.onEnable();
        // 设置歌单播放回调：通过 CommandSender 发送点歌命令到服务器
        PlaylistPlayer pp = ZMusic.getPlaylistPlayer();
        if (pp != null) {
            pp.setCallback(entry ->
                    me.zhenxin.zmusic.client.CommandSender.sendPlayCommand(
                            entry.getPlatform(), entry.getName()));
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // 使用 playBidirectional 注册双向通道，避免同一 channel 注册两次
        event.registrar("1")
                .optional()
                .playBidirectional(ZMusicPayload.TYPE, ZMusicPayload.STREAM_CODEC, this::handlePayload);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        for (net.minecraft.client.KeyMapping mapping : ZMusicKeys.ALL) {
            event.register(mapping);
        }
    }

    private void handlePayload(ZMusicPayload payload, IPayloadContext context) {
        ClientEvent.onPacket(payload.message());
    }

    /**
     * Mod 菜单「配置」按钮入口。
     *
     * <p>NeoForge 的 ModList 界面会寻找以 {@code ConfigScreen} 为名的可运行工厂，
     * 这里通过主类静态方法提供。</p>
     *
     * @param mc Minecraft 实例
     * @param parent 父界面
     * @return 设置界面
     */
    public static net.minecraft.client.gui.screens.Screen createConfigScreen(Minecraft mc, net.minecraft.client.gui.screens.Screen parent) {
        return SettingsScreen.build(parent);
    }
}

