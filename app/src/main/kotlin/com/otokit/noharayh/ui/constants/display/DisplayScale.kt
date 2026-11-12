package com.otokit.noharayh.ui.constants.display

import kotlin.math.roundToInt

/**
 * 应用内显示比例。
 *
 * 只描述固定比例与纯换算，不读取 Context/DisplayMetrics；
 * 实际系统密度由平台层读取，避免常量层接触 Android 运行状态。
 */
enum class DisplayScaleOption(val scale: Float, val savedValue: String) {
    Small(0.70f, "70"),
    Standard(0.85f, "85"),
    Large(1f, "100"),
    ;

    companion object {
        val DEFAULT: DisplayScaleOption = Standard

        // 旧档名按原比例兼容：85% 保持原大小，原 115% 归到新的最大档 100%。
        fun fromSavedValue(value: String?): DisplayScaleOption? = when (value) {
            "small" -> Standard
            "standard", "large" -> Large
            else -> entries.firstOrNull { it.savedValue == value }
        }
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
