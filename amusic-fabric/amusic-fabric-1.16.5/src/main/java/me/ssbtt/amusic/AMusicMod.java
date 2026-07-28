package me.ssbtt.amusic;

import me.ssbtt.amusic.event.ClientEvent;
import me.ssbtt.amusic.manager.SoundManagerImpl;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * Mod 主入口
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/1/28 13:01
 */
@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public class AMusicMod implements ModInitializer {

    @Override
    public void onInitialize() {
        AMusic.setSoundManager(new SoundManagerImpl());
        Identifier identifier = new Identifier("amusic", "channel");
        ClientPlayNetworking.registerGlobalReceiver(identifier, (client, handler, buf, responseSender) -> {
            byte[] buffer = new byte[buf.readableBytes()];
            buf.readBytes(buffer);
            buffer[0] = 0;
            String message = new String(buffer, StandardCharsets.UTF_8).substring(1);
            ClientEvent.onPacket(message);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEvent.onDisconnect());
        AMusic.onEnable();
    }
}