package me.zhenxin.zmusic.playlist;

/**
 * 歌单播放顺序。
 *
 * @author 真心
 * @since 2026-07-25
 */
public enum PlayOrder {
    /** 顺序播放（播完最后一首停止） */
    SEQUENCE,
    /** 随机播放 */
    RANDOM,
    /** 列表循环（播完最后一首回到第一首） */
    LOOP,
    /** 单曲循环 */
    SINGLE;

    /**
     * 获取中文显示名称。
     *
     * @return 中文名
     */
    public String getDisplayName() {
        switch (this) {
            case SEQUENCE: return "顺序播放";
            case RANDOM: return "随机播放";
            case LOOP: return "列表循环";
            case SINGLE: return "单曲循环";
            default: return name();
        }
    }
}
