package com.otokit.noharayh.ui.kit.pageScoreSync

import android.graphics.Color
import android.graphics.ColorMatrixColorFilter

/** 未选中谱面的颜色：去色后取半亮度，保留透明度。 */
internal object DifficultySelectionColors {
    private const val R = .1065f
    private const val G = .3575f
    private const val B = .036f

    val dimFilter = ColorMatrixColorFilter(floatArrayOf(
        R, G, B, 0f, 0f,
        R, G, B, 0f, 0f,
        R, G, B, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ))

    fun dim(color: Int): Int {
        val gray = (R * Color.red(color) + G * Color.green(color) + B * Color.blue(color)).toInt()
        return Color.argb(Color.alpha(color), gray, gray, gray)
    }
}
