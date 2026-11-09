package com.otokit.noharayh.ui.pages.pageScoreSync

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.AppTypography

/**
 * 成绩同步占位。
 *
 * 只构造自己的根 View，不引用 shell、不读取或保存导航状态。
 */
object ScoreSyncPlaceholder {

    /**
     * @param textColor 由 shell 提供的展示色，本批固定为默认主题的 basicColor；
     * 这里不查询主题，也不持有 Registry。
     */
    fun create(context: Context, textColor: Int): View = TextView(context).apply {
        setText(R.string.placeholder_score_sync)
        gravity = Gravity.CENTER
        setTextAppearance(android.R.style.TextAppearance_Material_Large)
        AppTypography.applyAppTypeface(this)
        setTextColor(textColor)
    }
}
