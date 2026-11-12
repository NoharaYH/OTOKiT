package com.otokit.noharayh.ui.animation.settings

import android.view.View
import com.otokit.noharayh.ui.constants.motion.ThemeSelectionMotion as Spec

/** DMI 箭头的唯一属性动画入口。 */
internal class ThemeDropdownArrowRunner(private val arrow: View) {
    fun preview(progress: Float) {
        arrow.animate().cancel()
        arrow.rotation = 90f + 180f * progress.coerceIn(0f, 1f)
    }
    fun render(expanded: Boolean) {
        arrow.animate().cancel()
        arrow.animate().rotation(if (expanded) 270f else 90f)
            .setDuration(Spec.ARROW_DURATION_MS).start()
    }
    fun reset() {
        arrow.animate().cancel()
        arrow.rotation = 90f
    }
}
