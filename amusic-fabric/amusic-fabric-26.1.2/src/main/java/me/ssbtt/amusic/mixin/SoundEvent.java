package me.ssbtt.amusic.mixin;

import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.AMusicPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEvent {
    @Inject(method = "play*", at = @At("HEAD"), cancellable = true)
    public void play(SoundInstance soundInstance, CallbackInfoReturnable<SoundEngine.PlayResult> info) {
        if (AMusic.getPlayer().getState() != AMusicPlayer.STATE_PLAYING || soundInstance == null) {
            return;
        }
        SoundSource data = soundInstance.getSource();
        if (data == SoundSource.RECORDS || data == SoundSource.MUSIC) {
            info.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}
