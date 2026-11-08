package com.otokit.noharayh.ui.pages.pageScoreSync

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.otokit.noharayh.R

/**
 * 成绩同步占位。
 *
 * 只构造自己的根 View，不引用 shell、不读取或保存导航状态。
 */
object ScoreSyncPlaceholder {

    fun create(context: Context): View = TextView(context).apply {
        setText(R.string.placeholder_score_sync)
        gravity = Gravity.CENTER
        setTextAppearance(android.R.style.TextAppearance_Material_Large)
    }
}
