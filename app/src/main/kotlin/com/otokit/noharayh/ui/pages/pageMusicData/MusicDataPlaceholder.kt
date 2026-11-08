package com.otokit.noharayh.ui.pages.pageMusicData

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.otokit.noharayh.R

/**
 * 歌曲详情占位。
 *
 * “歌曲详情”只是展示文案；路由标识始终是 `music_data`，不随文案修改。
 */
object MusicDataPlaceholder {

    fun create(context: Context): View = TextView(context).apply {
        setText(R.string.placeholder_music_data)
        gravity = Gravity.CENTER
        setTextAppearance(android.R.style.TextAppearance_Material_Large)
    }
}
