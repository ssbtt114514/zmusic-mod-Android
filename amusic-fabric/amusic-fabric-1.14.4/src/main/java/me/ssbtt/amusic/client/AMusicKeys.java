package me.ssbtt.amusic.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * AMusic 按键绑定定义（Fabric 版本）。
 *
 * <p>所有按键都会出现在原版「选项 → 控制 → 按键绑定」中，分类为 AMusic。
 * 通过 {@link KeyBindingHelper#registerKeyBinding} 在客户端初始化时注册。</p>
 *
 * @author ssbtt
 * @since 2026-07-28
 */
public final class AMusicKeys {

    /** 打开设置界面 */
    public static final KeyBinding OPEN_SETTINGS = new KeyBinding(
            "key.amusic.open_settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            "key.categories.amusic"
    );

    /** 音量增大 */
    public static final KeyBinding VOLUME_UP = new KeyBinding(
            "key.amusic.volume_up",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            "key.categories.amusic"
    );

    /** 音量减小 */
    public static final KeyBinding VOLUME_DOWN = new KeyBinding(
            "key.amusic.volume_down",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            "key.categories.amusic"
    );

    /** 静音/取消静音 */
    public static final KeyBinding MUTE_TOGGLE = new KeyBinding(
            "key.amusic.mute_toggle",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.amusic"
    );

    /** 上一首 */
    public static final KeyBinding PREVIOUS = new KeyBinding(
            "key.amusic.previous",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.amusic"
    );

    /** 下一首 */
    public static final KeyBinding NEXT = new KeyBinding(
            "key.amusic.next",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.amusic"
    );

    /** 暂停（发送 /zm stop） */
    public static final KeyBinding PAUSE = new KeyBinding(
            "key.amusic.pause",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.amusic"
    );

    /** 查看历史记录 */
    public static final KeyBinding HISTORY = new KeyBinding(
            "key.amusic.history",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.categories.amusic"
    );

    /** 查看歌单 */
    public static final KeyBinding PLAYLIST = new KeyBinding(
            "key.amusic.playlist",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.amusic"
    );

    /** 调整音量条位置（打开拖动界面） */
    public static final KeyBinding VOLUME_POSITION = new KeyBinding(
            "key.amusic.volume_position",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            "key.categories.amusic"
    );

    private AMusicKeys() {
    }

    /** 所有按键映射，用于注册。 */
    public static final KeyBinding[] ALL = {
            OPEN_SETTINGS, VOLUME_UP, VOLUME_DOWN, MUTE_TOGGLE,
            PREVIOUS, NEXT, PAUSE, HISTORY, PLAYLIST, VOLUME_POSITION
    };

    /**
     * 注册所有按键绑定。在 {@code onInitializeClient} 中调用。
     */
    public static void register() {
        for (KeyBinding binding : ALL) {
            KeyBindingHelper.registerKeyBinding(binding);
        }
    }
}
