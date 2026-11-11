package com.otokit.noharayh.ui.constants.motion

/** 设置父子页共用节奏；反色窗口也是进度比例，不额外计时。 */
object SettingsPageTransition {
    val PHONE_KIND = TransitionKind.Slide
    val SPLIT_KIND = TransitionKind.ScaleFadeSlide
    val TITLE_KIND = TransitionKind.ScaleFadeSlide
    val CURVE = MotionDefaults.PAGE_CURVE
    const val DURATION_MS = 300L
    const val SELECTION_WINDOW = 0.25f
    const val PAGE_SCALE = 1.05f
    const val TITLE_SCALE = 0.85f
    const val TITLE_DISTANCE_HEIGHT = 0.7f
}
