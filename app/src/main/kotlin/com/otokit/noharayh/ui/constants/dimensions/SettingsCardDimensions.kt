package com.otokit.noharayh.ui.constants.dimensions

/** 内框与外卡片共用边距和圆心；内圆角随外圆角和内距派生。 */
internal object SettingsCardDimensions {
    const val CORNER_DP = 16
    const val CONTENT_INSET_DP = 12
    const val CONTENT_CORNER_DP = CORNER_DP - CONTENT_INSET_DP
    const val CONTENT_ROW_HEIGHT_DP = 40

    /** 选中项使用完全圆角，按行高取半圆。 */
    const val CONTENT_SELECTED_CORNER_DP = CONTENT_ROW_HEIGHT_DP / 2
}
