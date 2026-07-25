package me.zhenxin.zmusic.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * ZMusic 按键绑定定义。
 *
 * <p>所有按键都会出现在原版「选项 → 控制 → 按键绑定」中，分类为 ZMusic。</p>
 *
 * @author 真心
 * @since 2026-07-25
 */
public final class ZMusicKeys {

    /** 打开设置界面 */
    public static final KeyMapping OPEN_SETTINGS = new KeyMapping(
            "key.zmusic.open_settings",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            "key.categories.zmusic"
    );

    /** 音量增大 */
    public static final KeyMapping VOLUME_UP = new KeyMapping(
            "key.zmusic.volume_up",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            "key.categories.zmusic"
    );

    /** 音量减小 */
    public static final KeyMapping VOLUME_DOWN = new KeyMapping(
            "key.zmusic.volume_down",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            "key.categories.zmusic"
    );

    /** 静音/取消静音 */
    public static final KeyMapping MUTE_TOGGLE = new KeyMapping(
            "key.zmusic.mute_toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.zmusic"
    );

    /** 上一首 */
    public static final KeyMapping PREVIOUS = new KeyMapping(
            "key.zmusic.previous",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.zmusic"
    );

    /** 下一首 */
    public static final KeyMapping NEXT = new KeyMapping(
            "key.zmusic.next",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.zmusic"
    );

    /** 暂停（发送 /zm stop） */
    public static final KeyMapping PAUSE = new KeyMapping(
            "key.zmusic.pause",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.zmusic"
    );

    /** 查看历史记录 */
    public static final KeyMapping HISTORY = new KeyMapping(
            "key.zmusic.history",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.categories.zmusic"
    );

    /** 查看收藏 */
    public static final KeyMapping FAVORITE = new KeyMapping(
            "key.zmusic.favorite",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.zmusic"
    );

    /** 查看歌单 */
    public static final KeyMapping PLAYLIST = new KeyMapping(
            "key.zmusic.playlist",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.zmusic"
    );

    /** 调整音量条位置（打开拖动界面） */
    public static final KeyMapping VOLUME_POSITION = new KeyMapping(
            "key.zmusic.volume_position",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            "key.categories.zmusic"
    );

    private ZMusicKeys() {
    }

    /** 所有按键映射，用于注册。 */
    public static final KeyMapping[] ALL = {
            OPEN_SETTINGS, VOLUME_UP, VOLUME_DOWN, MUTE_TOGGLE,
            PREVIOUS, NEXT, PAUSE, HISTORY, FAVORITE, PLAYLIST, VOLUME_POSITION
    };
}
