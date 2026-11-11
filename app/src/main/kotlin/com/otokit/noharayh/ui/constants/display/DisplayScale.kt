package com.otokit.noharayh.ui.constants.display

import kotlin.math.roundToInt

/**
 * 应用内显示比例。
 *
 * 只描述固定比例与纯换算，不读取 Context/DisplayMetrics；
 * 实际系统密度由平台层读取，避免常量层接触 Android 运行状态。
 */
enum class DisplayScaleOption(val scale: Float, val savedValue: String) {
    Small(0.85f, "small"),
    Standard(1f, "standard"),
    Large(1.15f, "large"),
    ;

    companion object {
        val DEFAULT: DisplayScaleOption = Small

        fun fromSavedValue(value: String?): DisplayScaleOption? =
            entries.firstOrNull { it.savedValue == value }
    }
}

object DisplayScale {

    fun densityDpi(
        stableDensityDpi: Int,
        fallbackDensityDpi: Int,
        scale: Float = DisplayScaleOption.DEFAULT.scale,
    ): Int {
        val base = if (stableDensityDpi > 0) stableDensityDpi else fallbackDensityDpi
        return (base * scale).roundToInt().coerceAtLeast(1)
    }
}
