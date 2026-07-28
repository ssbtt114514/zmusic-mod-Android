package me.ssbtt.amusic;

import me.ssbtt.amusic.client.AMusicKeys;
import me.ssbtt.amusic.client.CommandSender;
import me.ssbtt.amusic.client.gui.SettingsScreen;
import me.ssbtt.amusic.event.ClientEvent;
import me.ssbtt.amusic.event.ForgeEvent;
import me.ssbtt.amusic.manager.SoundManagerImpl;
import me.ssbtt.amusic.playlist.PlaylistPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.SimpleChannel;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * Mod 主入口
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/28 13:01
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
@Mod("amusic")
public class AMusicMod {

    /** 网络通道（PlaylistNetSender 通过此字段发送歌单数据包到服务端） */
    public static SimpleChannel CHANNEL;

    public AMusicMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::registerKeys);
        MinecraftForge.EVENT_BUS.register(new ForgeEvent());
    }

    private void setup(FMLClientSetupEvent event) {
        // 设置配置目录（gameDir/config）
        File gameDir = Minecraft.getInstance().gameDirectory;
        File configDir = new File(gameDir, "config");
        AMusic.setConfigDir(configDir);
        AMusic.setSoundManager(new SoundManagerImpl());
        // 1.20.4+ 使用 ChannelBuilder 新网络 API
        CHANNEL = ChannelBuilder.named(new ResourceLocation("amusic", "channel"))
                .networkProtocolVersion(1)
                .optional()
                .simpleChannel();
        CHANNEL.messageBuilder(String.class, 666, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(this::enc)
                .decoder(this::dec)
                .consumerMainThread(this::proc)
                .add();
        AMusic.onEnable();
        // 设置歌单播放回调：通过 CommandSender 发送点歌命令到服务器
        PlaylistPlayer pp = AMusic.getPlaylistPlayer();
        if (pp != null) {
            pp.setCallback(entry ->
                    CommandSender.sendPlayCommand(entry.getPlatform(), entry.getName()));
        }
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        for (net.minecraft.client.KeyMapping mapping : AMusicKeys.ALL) {
            event.register(mapping);
        }
    }

    private void enc(String str, FriendlyByteBuf buffer) {
        buffer.writeBytes(str.getBytes(StandardCharsets.UTF_8));
    }


    private String dec(FriendlyByteBuf buffer) {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private void proc(String message, CustomPayloadEvent.Context context) {
        ClientEvent.onPacket(message);
        context.setPacketHandled(true);
    }

    /**
     * Mod 菜单「配置」按钮入口。
     *
     * @param mc     Minecraft 实例
     * @param parent 父界面
     * @return 设置界面
     */
    public static net.minecraft.client.gui.screens.Screen createConfigScreen(Minecraft mc, net.minecraft.client.gui.screens.Screen parent) {
        return SettingsScreen.build(parent);
    }
}
