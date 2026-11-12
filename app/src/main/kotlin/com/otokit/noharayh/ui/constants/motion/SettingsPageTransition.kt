package com.otokit.noharayh.ui.constants.motion

/** 设置父子页共用节奏；反色窗口也是进度比例，不额外计时。 */
object SettingsPageTransition {
    val PHONE_KIND = TransitionKind.Slide
    val SPLIT_KIND = TransitionKind.ScaleFadeSlide
    val CURVE = MotionDefaults.PAGE_CURVE
    const val DURATION_MS = 300L
    const val SELECTION_WINDOW = 0.25f
    const val PAGE_SCALE = 1.05f

    // 设置页统一：一条时间轴中先让位、再展开、最后内容和标题落稳。
    const val UNFOLD_DURATION_MS = 420L
    val UNFOLD_CURVE = MotionDefaults.EMPHASIZED_CURVE
    const val UNFOLD_PAGE_DISTANCE_DP = 32f
    const val UNFOLD_ROOT_DISTANCE_DP = 16f
    const val UNFOLD_TITLE_DISTANCE_DP = 18f
    const val UNFOLD_REVEAL_END = 0.65f
    const val UNFOLD_CONTENT_START = 0.28f
    const val UNFOLD_CONTENT_END = 0.9f
    const val UNFOLD_ROOT_END = 0.4f
    const val UNFOLD_TITLE_EXIT_END = 0.25f
    const val UNFOLD_TITLE_START = 0.25f
    const val UNFOLD_TITLE_END = 0.85f
}
