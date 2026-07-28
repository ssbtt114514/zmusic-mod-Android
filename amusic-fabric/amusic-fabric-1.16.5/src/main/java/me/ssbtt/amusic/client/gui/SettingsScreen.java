package me.ssbtt.amusic.client.gui;

// TODO: 1.14-1.16 渲染API不同，需要适配 FontRenderer/RenderSystem

import me.ssbtt.amusic.AMusic;
import me.ssbtt.amusic.client.AMusicKeys;
import me.ssbtt.amusic.config.AMusicConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

/**
 * 基于 Cloth Config API 的 AMusic 设置界面（Fabric 版本）。
 *
 * @author ssbtt
 * @since 2026-07-28
 */
public class SettingsScreen {

    public static Screen build(Screen parent) {
        AMusicConfig config = AMusic.getConfig();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("amusic.settings.title"));

        builder.setSavingRunnable(() -> {
            if (config != null) {
                config.save();
            }
        });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory audioCat = builder.getOrCreateCategory(Text.translatable("amusic.settings.tab.audio"));
        if (config != null) {
            int volumePct = (int) (config.getVolume() * 100);
            audioCat.addEntry(entryBuilder.startIntSlider(Text.literal("音量"), volumePct, 0, 100)
                    .setDefaultValue(100)
                    .setTooltip(Text.literal("播放器音量 (0-100%)"))
                    .setSaveConsumer(v -> {
                        config.setVolume(v / 100.0f);
                        config.setMuted(false);
                    })
                    .build());

            audioCat.addEntry(entryBuilder.startBooleanToggle(Text.literal("静音"), config.isMuted())
                    .setDefaultValue(false)
                    .setSaveConsumer(config::setMuted)
                    .build());

            audioCat.addEntry(entryBuilder.startBooleanToggle(Text.literal("公开点歌 (/zm music)"), config.isPublicPlay())
                    .setDefaultValue(true)
                    .setTooltip(Text.literal("开启时使用 /zm music 平台 歌名（全服公开），关闭时使用 /zm search 平台 歌名（仅自己播放）"))
                    .setSaveConsumer(config::setPublicPlay)
                    .build());

            List<String> platforms = Arrays.asList("163", "bilibili", "kuwo", "netease", "qq");
            audioCat.addEntry(entryBuilder.startStringDropdownMenu(Text.literal("音乐平台"), config.getPlatform())
                    .setDefaultValue("163")
                    .setSelections(platforms)
                    .setSaveConsumer(config::setPlatform)
                    .build());

            audioCat.addEntry(entryBuilder.startBooleanToggle(Text.literal("流式传输（边下边播）"), config.isStreamingMode())
                    .setDefaultValue(false)
                    .setTooltip(Text.literal("开启时直接流式播放 MP3（启动快，但网络波动可能卡顿）；关闭时完整下载后播放（稳定，但需等待下载）"))
                    .setSaveConsumer(config::setStreamingMode)
                    .build());

            String posText = String.format("当前: (%.0f%%, %.0f%%)",
                    config.getVolumeOverlayX() * 100, config.getVolumeOverlayY() * 100);
            audioCat.addEntry(entryBuilder.startTextDescription(Text.literal("音量条位置 - " + posText)).build());
            audioCat.addEntry(entryBuilder.startTextDescription(Text.literal("按「调整音量条位置」快捷键在屏幕中拖动设置")).build());
        }

        ConfigCategory cmdCat = builder.getOrCreateCategory(Text.translatable("amusic.settings.tab.commands"));
        cmdCat.addEntry(entryBuilder.startTextDescription(Text.literal("以下按键用于发送命令：")).build());
        cmdCat.addEntry(entryBuilder.startTextDescription(Text.literal("上一首: /zm music 平台 歌名（历史记录中上一首）")).build());
        cmdCat.addEntry(entryBuilder.startTextDescription(Text.literal("下一首: /zm music 平台 歌名（历史记录中下一首）")).build());
        cmdCat.addEntry(entryBuilder.startTextDescription(Text.literal("暂停: /zm stop")).build());
        cmdCat.addEntry(entryBuilder.startTextDescription(Text.literal("在「选项 → 控制 → 按键绑定 → AMusic」分类中调整对应按键")).build());

        ConfigCategory hotkeyCat = builder.getOrCreateCategory(Text.translatable("amusic.settings.tab.hotkeys"));
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Text.literal("所有按键均可在原版「按键绑定」中调整，分类为 AMusic")).build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Text.literal("打开设置: " + keyName(AMusicKeys.OPEN_SETTINGS))).build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Text.literal("音量增大: " + keyName(AMusicKeys.VOLUME_UP))).build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Text.literal("音量减小: " + keyName(AMusicKeys.VOLUME_DOWN))).build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Text.literal("静音切换: " + keyName(AMusicKeys.MUTE_TOGGLE))).build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Text.literal("上一首: " + keyName(AMusicKeys.PREVIOUS))).build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Text.literal("下一首: " + keyName(AMusicKeys.NEXT))).build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Text.literal("暂停: " + keyName(AMusicKeys.PAUSE))).build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Text.literal("查看历史记录: " + keyName(AMusicKeys.HISTORY))).build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Text.literal("查看歌单: " + keyName(AMusicKeys.PLAYLIST))).build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Text.literal("调整音量条位置: " + keyName(AMusicKeys.VOLUME_POSITION))).build());

        return builder.build();
    }

    private static String keyName(net.minecraft.client.option.KeyBinding binding) {
        try {
            return binding.getBoundKeyLocalizedText().getString();
        } catch (Throwable t) {
            return binding.getTranslationKey();
        }
    }
}
