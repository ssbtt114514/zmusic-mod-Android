package me.ssbtt.amusic.mixin;

import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.AMusicPlayer;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(SoundSystem.class)
public class SoundEvent {
    @Inject(method = "play*", at = @At("HEAD"), cancellable = true)
    public void play(SoundInstance soundInstance, CallbackInfoReturnable<SoundSystem.PlayResult> info) {
        if (AMusic.getPlayer().getState() != AMusicPlayer.STATE_PLAYING || soundInstance == null) {
            return;
        }
        SoundCategory data = soundInstance.getCategory();
        if (data == SoundCategory.RECORDS || data == SoundCategory.MUSIC) {
            info.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
        }
    }
}
