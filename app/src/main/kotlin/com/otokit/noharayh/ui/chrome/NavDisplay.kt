package com.otokit.noharayh.ui.chrome

import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.shell.navigation.Destination

/**
 * 单个功能的只读展示数据。
 *
 * 文案、图标与强调色固定，不随 Mai/Chu 主题变化；destination 是唯一路由事实。
 */
data class NavDisplayItem(
    val destination: Destination,
    @StringRes val label: Int,
    @StringRes val subLabel: Int,
    @DrawableRes val icon: Int,
    @ColorInt val accentColor: Int,
)

/** 抽屉导航展示表；08 的 Rail 复用同一顺序与配色。 */
object NavDisplay {

    const val SCORE_SYNC_ACCENT: Int = 0xFF4CAF50.toInt()
    const val MUSIC_DATA_ACCENT: Int = 0xFF2196F3.toInt()
    const val COMING_SOON_ACCENT: Int = 0xFF9E9E9E.toInt()
    const val SETTINGS_ICON_COLOR: Int = 0xFF9E9E9E.toInt()

    val items: List<NavDisplayItem> = listOf(
        NavDisplayItem(
            destination = Destination.ScoreSync,
            label = R.string.nav_score_sync_label,
            subLabel = R.string.nav_score_sync_sub,
            icon = R.drawable.ic_sync_24,
            accentColor = SCORE_SYNC_ACCENT,
        ),
        NavDisplayItem(
            destination = Destination.MusicData,
            label = R.string.nav_music_data_label,
            subLabel = R.string.nav_music_data_sub,
            icon = R.drawable.ic_library_music_24,
            accentColor = MUSIC_DATA_ACCENT,
        ),
        NavDisplayItem(
            destination = Destination.ComingSoon,
            label = R.string.nav_coming_soon_label,
            subLabel = R.string.nav_coming_soon_sub,
            icon = R.drawable.ic_more_horiz_24,
            accentColor = COMING_SOON_ACCENT,
        ),
    )

    fun itemFor(destination: Destination): NavDisplayItem =
        items.first { it.destination == destination }
}
