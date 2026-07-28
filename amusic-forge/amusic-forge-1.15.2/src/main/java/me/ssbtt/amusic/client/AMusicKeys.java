package me.ssbtt.amusic.client;

import com.mojang.blaze3d.platform.InputMappings;
import net.minecraft.client.settings.KeyBinding;

/**
 * AMusic 按键绑定定义。
 *
 * <p>所有按键都会出现在原版「选项 → 控制 → 按键绑定」中，分类为 AMusic。</p>
 *
 * @author 真心
 * @since 2026-07-25
 */
public final class AMusicKeys {

    /** 打开设置界面 */
    public static final KeyBinding OPEN_SETTINGS = new KeyBinding(
            "key.amusic.open_settings",
            InputMappings.Type.KEYSYM,
            290,  // GLFW_KEY_F6
            "key.categories.amusic"
    );

    /** 音量增大 */
    public static final KeyBinding VOLUME_UP = new KeyBinding(
            "key.amusic.volume_up",
            InputMappings.Type.KEYSYM,
            267,  // GLFW_KEY_PAGE_UP
            "key.categories.amusic"
    );

    /** 音量减小 */
    public static final KeyBinding VOLUME_DOWN = new KeyBinding(
            "key.amusic.volume_down",
            InputMappings.Type.KEYSYM,
            268,  // GLFW_KEY_PAGE_DOWN
            "key.categories.amusic"
    );

    /** 静音/取消静音 */
    public static final KeyBinding MUTE_TOGGLE = new KeyBinding(
            "key.amusic.mute_toggle",
            InputMappings.Type.KEYSYM,
            -1,  // GLFW_KEY_UNKNOWN
            "key.categories.amusic"
    );

    /** 上一首 */
    public static final KeyBinding PREVIOUS = new KeyBinding(
            "key.amusic.previous",
            InputMappings.Type.KEYSYM,
            -1,  // GLFW_KEY_UNKNOWN
            "key.categories.amusic"
    );

    /** 下一首 */
    public static final KeyBinding NEXT = new KeyBinding(
            "key.amusic.next",
            InputMappings.Type.KEYSYM,
            -1,  // GLFW_KEY_UNKNOWN
            "key.categories.amusic"
    );

    /** 暂停（发送 /zm stop） */
    public static final KeyBinding PAUSE = new KeyBinding(
            "key.amusic.pause",
            InputMappings.Type.KEYSYM,
            -1,  // GLFW_KEY_UNKNOWN
            "key.categories.amusic"
    );

    /** 查看历史记录 */
    public static final KeyBinding HISTORY = new KeyBinding(
            "key.amusic.history",
            InputMappings.Type.KEYSYM,
            291,  // GLFW_KEY_F7
            "key.categories.amusic"
    );

    /** 查看歌单 */
    public static final KeyBinding PLAYLIST = new KeyBinding(
            "key.amusic.playlist",
            InputMappings.Type.KEYSYM,
            293,  // GLFW_KEY_F9
            "key.categories.amusic"
    );

    /** 调整音量条位置（打开拖动界面） */
    public static final KeyBinding VOLUME_POSITION = new KeyBinding(
            "key.amusic.volume_position",
            InputMappings.Type.KEYSYM,
            294,  // GLFW_KEY_F10
            "key.categories.amusic"
    );

    private AMusicKeys() {
    }

    /** 所有按键映射，用于注册。 */
    public static final KeyBinding[] ALL = {
            OPEN_SETTINGS, VOLUME_UP, VOLUME_DOWN, MUTE_TOGGLE,
            PREVIOUS, NEXT, PAUSE, HISTORY, PLAYLIST, VOLUME_POSITION
    };
}
