package com.otokit.noharayh.ui.kit.pageScoreSync

import android.graphics.Color
import android.graphics.ColorMatrixColorFilter

/** 未选中谱面的颜色：去色后取半亮度，保留透明度。 */
internal object DifficultySelectionColors {
    private const val R = .1065f
    private const val G = .3575f
    private const val B = .036f

    val dimFilter = filter(0f)

    /** 0 为暗灰，1 为原色；同一矩阵也作用于渐变与四色面。 */
    fun filter(selected: Float): ColorMatrixColorFilter {
        val r = R * (1f - selected)
        val g = G * (1f - selected)
        val b = B * (1f - selected)
        return ColorMatrixColorFilter(floatArrayOf(
            r + selected, g, b, 0f, 0f,
            r, g + selected, b, 0f, 0f,
            r, g, b + selected, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }

    fun dim(color: Int): Int {
        val gray = (R * Color.red(color) + G * Color.green(color) + B * Color.blue(color)).toInt()
        return Color.argb(Color.alpha(color), gray, gray, gray)
    }
}
