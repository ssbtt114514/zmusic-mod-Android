package me.ssbtt.amusic;

import me.ssbtt.amusic.client.AMusicKeys;
import me.ssbtt.amusic.client.gui.SettingsScreen;
import me.ssbtt.amusic.event.ClientEvent;
import me.ssbtt.amusic.event.NeoForgeEvent;
import me.ssbtt.amusic.manager.SoundManagerImpl;
import me.ssbtt.amusic.network.AMusicPayload;
import me.ssbtt.amusic.playlist.PlaylistPlayer;
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
@Mod(value = "amusic", dist = Dist.CLIENT)
public class AMusicNeoForgeMod {

    public AMusicNeoForgeMod(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.register(new NeoForgeEvent());
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        // 设置配置目录（gameDir/config）
        File gameDir = Minecraft.getInstance().gameDirectory;
        File configDir = new File(gameDir, "config");
        AMusic.setConfigDir(configDir);
        AMusic.setSoundManager(new SoundManagerImpl());
        AMusic.onEnable();
        // 设置歌单播放回调：通过 CommandSender 发送点歌命令到服务器
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pp != null) {
            pp.setCallback(entry ->
                    me.ssbtt.amusic.client.CommandSender.sendPlayCommand(
                            entry.getPlatform(), entry.getName()));
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // 使用 playBidirectional 注册双向通道，避免同一 channel 注册两次
        event.registrar("1")
                .optional()
                .playBidirectional(AMusicPayload.TYPE, AMusicPayload.STREAM_CODEC, this::handlePayload);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        for (net.minecraft.client.KeyMapping mapping : AMusicKeys.ALL) {
            event.register(mapping);
        }
    }

    private void handlePayload(AMusicPayload payload, IPayloadContext context) {
        ClientEvent.onPacket(payload.message());
    }

    /**
     * Mod 菜单「配置」按钮入口。
     *
     * @param mc Minecraft 实例
     * @param parent 父界面
     * @return 设置界面
     */
    public static net.minecraft.client.gui.screens.Screen createConfigScreen(Minecraft mc, net.minecraft.client.gui.screens.Screen parent) {
        return SettingsScreen.build(parent);
    }
}
