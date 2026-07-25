package me.zhenxin.zmusic.client.gui;

import me.zhenxin.zmusic.ZMusic;
import me.zhenxin.zmusic.client.ZMusicKeys;
import me.zhenxin.zmusic.config.ZMusicConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 基于 Cloth Config API 的 AMusic 设置界面。
 *
 * <p>三个分类：音频 / 快捷指令 / 快捷键。</p>
 *
 * @author ssbtt
 * @since 2026-07-25
 */
public class SettingsScreen {

    /**
     * 构建设置界面。
     *
     * @param parent 父界面
     * @return Cloth Config Screen
     */
    public static Screen build(Screen parent) {
        ZMusicConfig config = ZMusic.getConfig();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("zmusic.settings.title"));

        // 保存时持久化配置
        builder.setSavingRunnable(() -> {
            if (config != null) {
                config.save();
            }
        });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // ===== 音频分类 =====
        ConfigCategory audioCat = builder.getOrCreateCategory(Component.translatable("zmusic.settings.tab.audio"));
        if (config != null) {
            // 音量滑块 0~100
            int volumePct = (int) (config.getVolume() * 100);
            audioCat.addEntry(entryBuilder.startIntSlider(Component.literal("音量"), volumePct, 0, 100)
                    .setDefaultValue(100)
                    .setTooltip(Component.literal("播放器音量 (0-100%)"))
                    .setSaveConsumer(v -> {
                        config.setVolume(v / 100.0f);
                        config.setMuted(false);
                    })
                    .build());

            // 静音开关
            audioCat.addEntry(entryBuilder.startBooleanToggle(Component.literal("静音"), config.isMuted())
                    .setDefaultValue(false)
                    .setSaveConsumer(config::setMuted)
                    .build());

            // 公开点歌开关
            audioCat.addEntry(entryBuilder.startBooleanToggle(Component.literal("公开点歌 (/zm music)"), config.isPublicPlay())
                    .setDefaultValue(true)
                    .setTooltip(Component.literal("开启时使用 /zm music 平台 歌名，关闭时使用 /zm 平台 歌名"))
                    .setSaveConsumer(config::setPublicPlay)
                    .build());

            // 音乐平台选择
            List<String> platforms = Arrays.asList("163", "bilibili", "kuwo", "netease", "qq");
            audioCat.addEntry(entryBuilder.startStringDropdownMenu(Component.literal("音乐平台"), config.getPlatform())
                    .setDefaultValue("163")
                    .setSelections(platforms)
                    .setSaveConsumer(config::setPlatform)
                    .build());

            // 音量条位置（显示当前坐标，提示通过快捷键调整）
            String posText = String.format("当前: (%.0f%%, %.0f%%)",
                    config.getVolumeOverlayX() * 100, config.getVolumeOverlayY() * 100);
            audioCat.addEntry(entryBuilder.startTextDescription(Component.literal("音量条位置 - " + posText))
                    .build());
            audioCat.addEntry(entryBuilder.startTextDescription(Component.literal("按「调整音量条位置」快捷键在屏幕中拖动设置"))
                    .build());
        }

        // ===== 快捷指令分类 =====
        ConfigCategory cmdCat = builder.getOrCreateCategory(Component.translatable("zmusic.settings.tab.commands"));
        cmdCat.addEntry(entryBuilder.startTextDescription(Component.literal("以下按键用于发送命令："))
                .build());
        cmdCat.addEntry(entryBuilder.startTextDescription(Component.literal("上一首: /zm music 平台 歌名（历史记录中上一首）"))
                .build());
        cmdCat.addEntry(entryBuilder.startTextDescription(Component.literal("下一首: /zm music 平台 歌名（历史记录中下一首）"))
                .build());
        cmdCat.addEntry(entryBuilder.startTextDescription(Component.literal("暂停: /zm stop"))
                .build());
        cmdCat.addEntry(entryBuilder.startTextDescription(Component.literal("在「选项 → 控制 → 按键绑定 → AMusic」分类中调整对应按键"))
                .build());

        // ===== 快捷键分类 =====
        ConfigCategory hotkeyCat = builder.getOrCreateCategory(Component.translatable("zmusic.settings.tab.hotkeys"));
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("所有按键均可在原版「按键绑定」中调整，分类为 AMusic"))
                .build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("打开设置: " + keyName(ZMusicKeys.OPEN_SETTINGS)))
                .build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("音量增大: " + keyName(ZMusicKeys.VOLUME_UP)))
                .build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("音量减小: " + keyName(ZMusicKeys.VOLUME_DOWN)))
                .build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("静音切换: " + keyName(ZMusicKeys.MUTE_TOGGLE)))
                .build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("上一首: " + keyName(ZMusicKeys.PREVIOUS)))
                .build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("下一首: " + keyName(ZMusicKeys.NEXT)))
                .build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("暂停: " + keyName(ZMusicKeys.PAUSE)))
                .build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("查看历史记录: " + keyName(ZMusicKeys.HISTORY)))
                .build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("查看收藏: " + keyName(ZMusicKeys.FAVORITE)))
                .build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("查看歌单: " + keyName(ZMusicKeys.PLAYLIST)))
                .build());
        hotkeyCat.addEntry(entryBuilder.startTextDescription(Component.literal("调整音量条位置: " + keyName(ZMusicKeys.VOLUME_POSITION)))
                .build());

        return builder.build();
    }

    private static String keyName(net.minecraft.client.KeyMapping mapping) {
        try {
            return mapping.getTranslatedKeyMessage().getString();
        } catch (Throwable t) {
            return mapping.getName();
        }
    }
}
