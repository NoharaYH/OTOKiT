package com.otokit.noharayh.ui.constants.dimensions

/** 主题触发框、分段按钮与容器菜单的标准几何。 */
internal object ThemeSelectionDimensions {
    const val FIELD_GAP_DP = 12
    const val FIELD_HEIGHT_DP = 44
    const val FIELD_RADIUS_DP = FIELD_HEIGHT_DP / 2
    // 收起外轮廓与复合容器相差一个内容内缩，展开仍使用原来的胶囊半径。
    const val FIELD_COLLAPSED_RADIUS_DP = SettingsGroupDimensions.OUTER_RADIUS_DP - SettingsCardDimensions.CONTENT_INSET_DP
    const val FIELD_STROKE_DP = 2.5f
    const val FIELD_TEXT_INSET_DP = 26
    const val FIELD_END_INSET_DP = 12
    const val FIELD_ARROW_DP = 20
    const val LABEL_GAP_DP = 4
    const val MENU_ROW_DP = 44
    const val MENU_GAP_DP = 4
    const val MENU_BAR_DP = 6
    const val MENU_PADDING_DP = FIELD_GAP_DP
    const val MENU_MAX_SLOTS = 6
    const val MENU_LARGE_RADIUS_DP = 22f
    const val MENU_BAR_RADIUS_DP = 3f
    const val MENU_INNER_RADIUS_DP = 4f
    const val MENU_MIN_VISIBLE_DP = 2
    const val MENU_SELECTED_STROKE_DP = 2f
    const val PREVIEW_TRAVEL_MAX_DP = 56f
    const val PREVIEW_SCRIM_TEXT_WIDTH_SCALE = 1.3f
    const val PREVIEW_SCRIM_LENGTH_SCALE = 1.1f
    const val PREVIEW_MATERKIT_STAR_HEIGHT_FACTOR = 2f
    const val PREVIEW_BITMAP_MAX_WIDTH_PX = 1024
    const val PREVIEW_BITMAP_MAX_HEIGHT_PX = 144
    const val PREVIEW_CACHE_BYTES = 16 * 1024 * 1024
    const val MENU_OVERSCROLL_DP = 8
    const val MENU_TRANSLATION_DP = 8
    const val SCROLLBAR_WIDTH_DP = 9f
    const val SCROLLBAR_HIT_WIDTH_DP = 48
    const val SCROLLBAR_END_GAP_DP = 6f
    const val SCROLLBAR_PULL_DP = 14f
    const val SCROLLBAR_STRETCH_DP = 24f
}
