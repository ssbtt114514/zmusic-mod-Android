package me.ssbtt.amusic.client;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
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
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.amusic.open_settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            KeyMapping.Category.MISC
    );

    /** 音量增大 */
    public static final KeyMapping VOLUME_UP = new KeyMapping(
            "key.amusic.volume_up",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            KeyMapping.Category.MISC
    );

    /** 音量减小 */
    public static final KeyMapping VOLUME_DOWN = new KeyMapping(
            "key.amusic.volume_down",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            KeyMapping.Category.MISC
    );

    /** 静音/取消静音 */
    public static final KeyMapping MUTE_TOGGLE = new KeyMapping(
            "key.amusic.mute_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KeyMapping.Category.MISC
    );

    /** 上一首 */
    public static final KeyMapping PREVIOUS = new KeyMapping(
            "key.amusic.previous",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KeyMapping.Category.MISC
    );

    /** 下一首 */
    public static final KeyMapping NEXT = new KeyMapping(
            "key.amusic.next",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KeyMapping.Category.MISC
    );

    /** 暂停（发送 /zm stop） */
    public static final KeyMapping PAUSE = new KeyMapping(
            "key.amusic.pause",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            KeyMapping.Category.MISC
    );

    /** 查看历史记录 */
    public static final KeyMapping HISTORY = new KeyMapping(
            "key.amusic.history",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            KeyMapping.Category.MISC
    );

    /** 查看歌单 */
    public static final KeyMapping PLAYLIST = new KeyMapping(
            "key.amusic.playlist",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            KeyMapping.Category.MISC
    );

    /** 调整音量条位置（打开拖动界面） */
    public static final KeyMapping VOLUME_POSITION = new KeyMapping(
            "key.amusic.volume_position",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            KeyMapping.Category.MISC
    );

    private AMusicKeys() {
    }

    /** 所有按键映射，用于注册。 */
    public static final KeyMapping[] ALL = {
            OPEN_SETTINGS, VOLUME_UP, VOLUME_DOWN, MUTE_TOGGLE,
            PREVIOUS, NEXT, PAUSE, HISTORY, PLAYLIST, VOLUME_POSITION
    };

    /**
     * 注册所有按键绑定。在 {@code onInitializeClient} 中调用。
     */
    public static void register() {
        for (KeyMapping mapping : ALL) {
            KeyMappingHelper.registerKeyMapping(mapping);
        }
    }
}
