package com.otokit.noharayh.ui.pages.pageMusicData

import android.content.Context
import android.view.Gravity
import android.widget.TextView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PageContent

/**
 * 歌曲详情占位。
 *
 * “歌曲详情”只是展示文案；路由标识始终是 `music_data`，不随文案修改。
 */
object MusicDataPlaceholder {

    /**
     * @param textColor 由 shell 提供的当前主题展示色；
     * 这里不查询主题，也不持有 Registry。
     */
    fun create(context: Context, textColor: Int): PageContent {
        val text = TextView(context).apply {
            setText(R.string.placeholder_music_data)
            gravity = Gravity.CENTER
            setTextAppearance(android.R.style.TextAppearance_Material_Large)
            AppTypography.applyAppTypeface(this)
            setTextColor(textColor)
        }
        return PageContent(text, text::setTextColor)
    }
}
