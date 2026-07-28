package me.ssbtt.amusic.event;

import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.AMusicPlayer;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Forge 事件
 *
 * @author 真心
 * @email qgzhenxin@qq.com
 * @since 2023/3/17 11:17
 */
public class ForgeEvent {

    @SubscribeEvent
    public void onSound(final PlaySoundEvent e) {
        if (AMusic.getPlayer().getState() != AMusicPlayer.STATE_PLAYING || e.getSound() == null) {
            return;
        }
        SoundCategory data = e.getSound().getCategory();
        switch (data) {
            case MUSIC:
            case RECORDS:
                e.setResultSound(null);
                break;
            default:
        }
    }

    @SubscribeEvent
    public void onServerQuit(final FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        AMusic.getPlayer().stopAsync();
    }


    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        AMusic.getPlayer().setVolume(AMusic.getSoundManager().volume());
    }
}
