package com.otokit.noharayh.ui.shell.navigation

/**
 * 稳定功能标识。
 *
 * [savedValue] 属于持久化契约，不随展示文案或声明顺序变化；禁止用 ordinal 或显示文字代替。
 */
enum class Destination(val savedValue: String) {
    ScoreSync("score_sync"),
    MusicData("music_data"),
    ComingSoon("coming_soon"),
    ;

    companion object {
        fun fromSavedValue(value: String?): Destination? =
            entries.firstOrNull { it.savedValue == value }
    }
}
